package com.joyo.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.MetadataResponses.TableDetailResponse;
import com.joyo.backend.model.MetadataResponses.TableDetailResponse.TableColumn;
import com.joyo.backend.model.MetadataResponses.TableDetailResponse.TableIndex;
import com.joyo.backend.model.SyncModels;
import com.joyo.backend.model.SyncModels.ColumnDiff;
import com.joyo.backend.model.SyncModels.IndexDiff;
import com.joyo.backend.model.SyncModels.SyncCompareRequest;
import com.joyo.backend.model.SyncModels.SyncCompareResponse;
import com.joyo.backend.model.SyncModels.SyncExecuteResponse;
import com.joyo.backend.model.SyncModels.TableRef;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 表/数据同步（spec-17）：结构差异判定、脚本生成、校验和比对、单事务执行与回滚。
 *
 * 注意：所有 mock 桩必须在注册 stubbing 之前构造完毕，
 * 否则会在 `when(...)` 进行中再次 `when(...)` → UnfinishedStubbing。
 */
class SyncServiceTest {

    private final ConnectionService connectionService = mock(ConnectionService.class);
    private final MetadataService metadataService = mock(MetadataService.class);
    private final EditableResolver editableResolver = mock(EditableResolver.class);
    private final SyncService service = new SyncService(connectionService, metadataService, editableResolver);
    private final Connection sourceConn = mock(Connection.class);
    private final Connection targetConn = mock(Connection.class);

    private final TableRef source = new TableRef("c1", "db1", "users");
    private final TableRef target = new TableRef("c2", "db2", "users");

    @BeforeEach
    void setUp() throws Exception {
        HikariDataSource ds1 = mock(HikariDataSource.class);
        HikariDataSource ds2 = mock(HikariDataSource.class);
        when(connectionService.requireDataSource("c1")).thenReturn(ds1);
        when(connectionService.requireDataSource("c2")).thenReturn(ds2);
        when(ds1.getConnection()).thenReturn(sourceConn);
        when(ds2.getConnection()).thenReturn(targetConn);
        when(targetConn.getAutoCommit()).thenReturn(true);
    }

    private TableColumn column(String name, String type, boolean nullable) {
        return new TableColumn(name, type, null, nullable, "", null, "", "", 1);
    }

    private TableDetailResponse detail(List<TableColumn> columns, List<TableIndex> indexes) {
        return new TableDetailResponse(columns, indexes, null);
    }

    /** 校验和查询桩：rows 为 {主键值, 校验和} */
    private PreparedStatement checksumStatement(String[][] rows) throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        var next = when(rs.next());
        for (int i = 0; i < rows.length; i++) {
            next = next.thenReturn(true);
        }
        next.thenReturn(false);
        // 必须逐个完成链式桩：开启下一个 when() 前，前一个已经 thenReturn
        var first = when(rs.getString(1));
        for (String[] row : rows) {
            first = first.thenReturn(row[0]);
        }
        var second = when(rs.getString(2));
        for (String[] row : rows) {
            second = second.thenReturn(row[1]);
        }
        when(ps.executeQuery()).thenReturn(rs);
        return ps;
    }

    /** 取行查询桩：返回一行固定值 */
    private PreparedStatement rowStatement() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("id")).thenReturn("1");
        when(rs.getString("name")).thenReturn("Bob");
        when(ps.executeQuery()).thenReturn(rs);
        return ps;
    }

    private SyncCompareRequest request() {
        return new SyncCompareRequest(source, target, 10000);
    }

    private void stubSameStructure() throws Exception {
        List<TableColumn> columns = List.of(column("id", "bigint", false), column("name", "varchar", true));
        when(metadataService.structure(eq(sourceConn), eq("db1"), eq("users"))).thenReturn(detail(columns, List.of()));
        when(metadataService.structure(eq(targetConn), eq("db2"), eq("users"))).thenReturn(detail(columns, List.of()));
        when(editableResolver.primaryKey(eq(targetConn), eq("db2"), eq("users"))).thenReturn(List.of("id"));
    }

    @Test
    void 列差异分出missing_extra_changed() {
        List<TableColumn> sourceCols = List.of(
                column("id", "bigint", false),
                column("email", "varchar", true),
                column("age", "int", true));
        List<TableColumn> targetCols = List.of(
                column("id", "bigint", false),
                column("age", "bigint", true),
                column("extra_col", "varchar", true));

        List<ColumnDiff> diffs = service.diffColumns(sourceCols, targetCols);

        assertEquals("missing", diffs.stream().filter(d -> d.name().equals("email")).findFirst().orElseThrow().kind());
        assertEquals("changed", diffs.stream().filter(d -> d.name().equals("age")).findFirst().orElseThrow().kind());
        assertEquals("extra", diffs.stream().filter(d -> d.name().equals("extra_col")).findFirst().orElseThrow().kind());
        // 相同列（id）不产生差异
        assertTrue(diffs.stream().noneMatch(d -> d.name().equals("id")));
    }

    @Test
    void 索引差异识别缺失与唯一性变化() {
        List<TableIndex> sourceIdx = List.of(
                new TableIndex("idx_email", "BTREE", false, List.of("email")),
                new TableIndex("uk_name", "BTREE", true, List.of("name")));
        List<TableIndex> targetIdx = List.of(new TableIndex("uk_name", "BTREE", false, List.of("name")));

        List<IndexDiff> diffs = service.diffIndexes(sourceIdx, targetIdx);

        assertEquals("missing", diffs.stream().filter(d -> d.name().equals("idx_email")).findFirst().orElseThrow().kind());
        assertEquals("changed", diffs.stream().filter(d -> d.name().equals("uk_name")).findFirst().orElseThrow().kind());
    }

    @Test
    void 结构脚本只生成ADD_MODIFY_CREATE_INDEX不含DROP() {
        List<ColumnDiff> columnDiffs = List.of(
                new ColumnDiff("email", "missing", "varchar NULL", null),
                new ColumnDiff("age", "changed", "bigint NULL", "int NULL"),
                new ColumnDiff("gone", "extra", null, "varchar NULL"));
        List<IndexDiff> indexDiffs = List.of(
                new IndexDiff("idx_email", "missing", "(email)"),
                new IndexDiff("idx_old", "extra", "(old)"));

        List<String> statements = service.structureStatements(target, columnDiffs, indexDiffs);

        assertTrue(statements.contains("ALTER TABLE `db2`.`users` ADD COLUMN `email` varchar NULL"));
        assertTrue(statements.contains("ALTER TABLE `db2`.`users` MODIFY COLUMN `age` bigint NULL"));
        assertTrue(statements.contains("CREATE INDEX `idx_email` ON `db2`.`users` (email)"));
        assertTrue(statements.stream().noneMatch(s -> s.toUpperCase().contains("DROP")));
    }

    @Test
    void 目标表不存在时不可比对() throws Exception {
        when(metadataService.structure(eq(sourceConn), eq("db1"), eq("users")))
                .thenReturn(detail(List.of(column("id", "bigint", false)), List.of()));
        when(metadataService.structure(eq(targetConn), eq("db2"), eq("users")))
                .thenReturn(detail(List.of(), List.of()));

        SyncCompareResponse response = service.compare(request());

        assertFalse(response.comparable());
        assertEquals("目标表不存在", response.reason());
        assertEquals(0, response.dataDiff().insert());
    }

    @Test
    void 无主键时不可比对数据() throws Exception {
        stubSameStructure();
        when(editableResolver.primaryKey(eq(targetConn), eq("db2"), eq("users"))).thenReturn(List.of());

        SyncCompareResponse response = service.compare(request());

        assertFalse(response.comparable());
        assertEquals("表无主键，无法按主键比对数据", response.reason());
        assertEquals(0, response.dataDiff().update());
    }

    @Test
    void 校验和比对出新增修改与删除() throws Exception {
        stubSameStructure();
        PreparedStatement sourcePs = checksumStatement(new String[][] {{"1", "a"}, {"2", "b"}});
        PreparedStatement targetPs = checksumStatement(new String[][] {{"1", "a2"}, {"3", "c"}});
        when(sourceConn.prepareStatement(anyString())).thenReturn(sourcePs);
        when(targetConn.prepareStatement(anyString())).thenReturn(targetPs);

        SyncCompareResponse response = service.compare(request());

        assertTrue(response.comparable());
        assertEquals(1, response.dataDiff().insert());
        assertEquals(1, response.dataDiff().update());
        assertEquals(1, response.dataDiff().delete());
        assertEquals(2, response.dataDiff().scanned());
        assertFalse(response.dataDiff().truncated());
    }

    @Test
    void 执行同步在一个事务内提交() throws Exception {
        stubSameStructure();
        PreparedStatement sourceChecksum = checksumStatement(new String[][] {{"1", "a"}});
        PreparedStatement targetChecksum = checksumStatement(new String[][] {{"1", "b"}});
        PreparedStatement sourceRow = rowStatement();
        PreparedStatement writePs = mock(PreparedStatement.class);
        Statement ddl = mock(Statement.class);
        when(writePs.executeUpdate()).thenReturn(1);
        when(sourceConn.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.contains("MD5") ? sourceChecksum : sourceRow;
        });
        when(targetConn.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.contains("MD5") ? targetChecksum : writePs;
        });
        when(targetConn.createStatement()).thenReturn(ddl);

        SyncExecuteResponse response = service.execute(request());

        assertEquals(1, response.applied());
        verify(targetConn).setAutoCommit(false);
        verify(targetConn).commit();
    }

    @Test
    void 执行失败回滚并返回5102() throws Exception {
        stubSameStructure();
        PreparedStatement sourceChecksum = checksumStatement(new String[][] {{"1", "a"}});
        PreparedStatement targetChecksum = checksumStatement(new String[][] {{"1", "b"}});
        PreparedStatement sourceRow = rowStatement();
        PreparedStatement writePs = mock(PreparedStatement.class);
        Statement ddl = mock(Statement.class);
        when(writePs.executeUpdate()).thenThrow(new SQLException("Duplicate entry 'x'"));
        when(sourceConn.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.contains("MD5") ? sourceChecksum : sourceRow;
        });
        when(targetConn.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.contains("MD5") ? targetChecksum : writePs;
        });
        when(targetConn.createStatement()).thenReturn(ddl);

        BizException e = assertThrows(BizException.class, () -> service.execute(request()));

        assertEquals(ErrorCode.SYNC_FAILED.getCode(), e.getErrorCode().getCode());
        assertTrue(e.getMessage().contains("Duplicate entry"));
        verify(targetConn).rollback();
    }

    @Test
    void 表不存在时执行抛5101() throws Exception {
        when(metadataService.structure(eq(sourceConn), eq("db1"), eq("users")))
                .thenReturn(detail(List.of(column("id", "bigint", false)), List.of()));
        when(metadataService.structure(eq(targetConn), eq("db2"), eq("users")))
                .thenReturn(detail(List.of(), List.of()));

        BizException e = assertThrows(BizException.class, () -> service.execute(request()));

        assertEquals(ErrorCode.SYNC_NOT_COMPARABLE.getCode(), e.getErrorCode().getCode());
    }

    @Test
    void 空请求参数抛1001() {
        BizException e = assertThrows(BizException.class, () -> service.compare(null));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getErrorCode().getCode());
    }
}

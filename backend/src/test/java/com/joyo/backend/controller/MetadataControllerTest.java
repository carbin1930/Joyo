package com.joyo.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.MetadataResponses.SchemasResponse;
import com.joyo.backend.model.MetadataResponses.SchemasResponse.SchemaItem;
import com.joyo.backend.model.MetadataResponses.TableDetailResponse;
import com.joyo.backend.model.MetadataResponses.TableDetailResponse.TableColumn;
import com.joyo.backend.model.MetadataResponses.TableDetailResponse.TableIndex;
import com.joyo.backend.model.MetadataResponses.TablesResponse;
import com.joyo.backend.model.MetadataResponses.TablesResponse.TableItem;
import com.joyo.backend.service.MetadataService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "joyo.token=test-token")
class MetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetadataService metadataService;

    private static final String BASE = "/api/v1/connections/conn-1";

    @Test
    @DisplayName("库列表返回并包含系统库标记")
    void 库列表() throws Exception {
        when(metadataService.listSchemas("conn-1")).thenReturn(new SchemasResponse(List.of(
                new SchemaItem("joyo", false),
                new SchemaItem("mysql", true))));

        mockMvc.perform(get(BASE + "/schemas").header("X-Joyo-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.schemas.length()").value(2))
                .andExpect(jsonPath("$.data.schemas[0].name").value("joyo"))
                .andExpect(jsonPath("$.data.schemas[0].isSystem").value(false))
                .andExpect(jsonPath("$.data.schemas[1].isSystem").value(true));
    }

    @Test
    @DisplayName("表列表返回类型与行数估计")
    void 表列表() throws Exception {
        when(metadataService.listTables(eq("conn-1"), eq("joyo"))).thenReturn(new TablesResponse(List.of(
                new TableItem("user", "BASE_TABLE", "InnoDB", "用户表", 1024L),
                new TableItem("v_user", "VIEW", null, "", null))));

        mockMvc.perform(get(BASE + "/schemas/joyo/tables").header("X-Joyo-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tables.length()").value(2))
                .andExpect(jsonPath("$.data.tables[0].type").value("BASE_TABLE"))
                .andExpect(jsonPath("$.data.tables[0].engine").value("InnoDB"))
                .andExpect(jsonPath("$.data.tables[1].type").value("VIEW"));
    }

    @Test
    @DisplayName("表详情返回列/索引/DDL")
    void 表详情() throws Exception {
        when(metadataService.tableDetail(eq("conn-1"), eq("joyo"), eq("user"))).thenReturn(
                new TableDetailResponse(
                        List.of(new TableColumn("id", "bigint", null, false, "PRI", null,
                                "auto_increment", "主键", 1)),
                        List.of(new TableIndex("PRIMARY", "BTREE", true, List.of("id"))),
                        "CREATE TABLE `user` (...)"));

        mockMvc.perform(get(BASE + "/schemas/joyo/tables/user").header("X-Joyo-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].name").value("id"))
                .andExpect(jsonPath("$.data.columns[0].key").value("PRI"))
                .andExpect(jsonPath("$.data.indexes[0].unique").value(true))
                .andExpect(jsonPath("$.data.ddl").value("CREATE TABLE `user` (...)"));
    }

    @Test
    @DisplayName("会话失效透传 2004")
    void 会话失效() throws Exception {
        when(metadataService.listSchemas(any()))
                .thenThrow(new BizException(ErrorCode.SESSION_INVALID));

        mockMvc.perform(get(BASE + "/schemas").header("X-Joyo-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2004));
    }

    @Test
    @DisplayName("元数据读取失败透传 2101")
    void 元数据失败() throws Exception {
        when(metadataService.listTables(any(), any()))
                .thenThrow(new BizException(ErrorCode.METADATA_FAILED, "元数据读取失败: 权限不足"));

        mockMvc.perform(get(BASE + "/schemas/joyo/tables").header("X-Joyo-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2101))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("权限不足")));
    }
}

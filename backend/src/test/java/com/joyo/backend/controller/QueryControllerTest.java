package com.joyo.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.QueryModels.ColumnMeta;
import com.joyo.backend.model.QueryModels.QueryExecuteResponse;
import com.joyo.backend.model.QueryModels.StatementResult;
import com.joyo.backend.service.QueryService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "joyo.token=test-token")
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueryService queryService;

    @Test
    @DisplayName("执行成功返回语句结果数组")
    void 执行成功() throws Exception {
        when(queryService.execute(eq("conn-1"), any())).thenReturn(new QueryExecuteResponse(
                List.of(new StatementResult(1, "SELECT", "q-1",
                        List.of(new ColumnMeta("id", "BIGINT", true)),
                        true, 2L, false, List.<String[]>of(new String[] {"1", "alice"}), 1, 100,
                        null, 12L, null)),
                20L));

        mockMvc.perform(post("/api/v1/connections/conn-1/queries")
                        .header("X-Joyo-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"SELECT 1\",\"maxRows\":1000,\"queryTimeoutSec\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.statements[0].kind").value("SELECT"))
                .andExpect(jsonPath("$.data.statements[0].queryId").value("q-1"))
                .andExpect(jsonPath("$.data.statements[0].rows[0][0]").value("1"));
    }

    @Test
    @DisplayName("空 SQL 返回 1001")
    void 空sql() throws Exception {
        mockMvc.perform(post("/api/v1/connections/conn-1/queries")
                        .header("X-Joyo-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("SQL 错误透传 3001 与 MySQL 原始信息")
    void 执行错误() throws Exception {
        when(queryService.execute(any(), any()))
                .thenThrow(new BizException(ErrorCode.QUERY_ERROR,
                        "Unknown column 'nmae' in 'field list'"));

        mockMvc.perform(post("/api/v1/connections/conn-1/queries")
                        .header("X-Joyo-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"SELECT nmae\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3001))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("nmae")));
    }

    @Test
    @DisplayName("翻页：queryId 不存在返回 1002 与过期提示")
    void 翻页过期() throws Exception {
        mockMvc.perform(get("/api/v1/queries/not-exist/rows")
                        .queryParam("page", "2")
                        .queryParam("pageSize", "100")
                        .header("X-Joyo-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("结果已过期，请重新执行"));
    }
}

package com.joyo.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joyo.backend.exception.BizException;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.ConnectionTestResponse;
import com.joyo.backend.model.SessionResponse;
import com.joyo.backend.service.ConnectionService;
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
class ConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConnectionService connectionService;

    @Test
    @DisplayName("连接测试成功返回 code=0 与版本耗时")
    void 测试成功() throws Exception {
        when(connectionService.test(any())).thenReturn(new ConnectionTestResponse("8.0.36", 35));

        mockMvc.perform(post("/api/v1/connections/test")
                        .header("X-Joyo-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"host":"127.0.0.1","port":3306,"username":"root","password":"x"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.serverVersion").value("8.0.36"))
                .andExpect(jsonPath("$.data.elapsedMs").value(35));
    }

    @Test
    @DisplayName("连接测试失败透传业务错误码（2002）")
    void 测试失败() throws Exception {
        when(connectionService.test(any()))
                .thenThrow(new BizException(ErrorCode.AUTH_FAILED));

        mockMvc.perform(post("/api/v1/connections/test")
                        .header("X-Joyo-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"host":"127.0.0.1","port":3306,"username":"root","password":"bad"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("参数缺失返回 1001 并带字段名")
    void 参数校验() throws Exception {
        mockMvc.perform(post("/api/v1/connections/test")
                        .header("X-Joyo-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"port":3306,"username":"root"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("host")));
    }

    @Test
    @DisplayName("建立会话返回会话信息")
    void 建立会话() throws Exception {
        String id = "b3c1-uuid";
        when(connectionService.createSession(eq(id), any()))
                .thenReturn(new SessionResponse(id, "8.0.36", "joyo"));

        mockMvc.perform(post("/api/v1/connections/{id}/session", id)
                        .header("X-Joyo-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"host":"127.0.0.1","port":3306,"username":"root","password":"x","defaultDatabase":"joyo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.connectionId").value(id))
                .andExpect(jsonPath("$.data.defaultDatabase").value("joyo"));
    }

    @Test
    @DisplayName("关闭会话幂等返回成功")
    void 关闭会话() throws Exception {
        mockMvc.perform(delete("/api/v1/connections/{id}/session", "any-id")
                        .header("X-Joyo-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("关闭会话异常透传 2004")
    void 关闭会话异常() throws Exception {
        doThrow(new BizException(ErrorCode.SESSION_INVALID))
                .when(connectionService).closeSession("bad-id");

        mockMvc.perform(delete("/api/v1/connections/{id}/session", "bad-id")
                        .header("X-Joyo-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2004));
    }
}

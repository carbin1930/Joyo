package com.joyo.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "joyo.token=test-token")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("携带正确 Token 访问健康检查返回成功")
    void 携带正确Token访问健康检查返回成功() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("X-Joyo-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.app").value("joyo-backend"))
                .andExpect(jsonPath("$.data.version").isNotEmpty())
                .andExpect(jsonPath("$.data.uptimeSec").isNumber());
    }

    @Test
    @DisplayName("缺少 Token 返回鉴权失败 1003")
    void 缺少Token返回鉴权失败() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("Token 错误返回鉴权失败 1003")
    void Token错误返回鉴权失败() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("X-Joyo-Token", "wrong-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("跨域预检请求放行且不需要 Token（渲染进程源 → 后端）")
    void 跨域预检放行() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .options("/api/v1/health")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Joyo-Token"))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader("Access-Control-Allow-Origin"))
                        .isNotNull());
    }
}

package com.joyo.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("成功响应序列化为 code=0/message=ok")
    void 成功响应序列化() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.ok(new HealthResponse("joyo-backend", "0.1.0", 1)));

        assertThat(json).contains("\"code\":0");
        assertThat(json).contains("\"message\":\"ok\"");
        assertThat(json).contains("\"app\":\"joyo-backend\"");
    }

    @Test
    @DisplayName("错误响应的 data 为 null 且包含错误信息")
    void 错误响应序列化() throws Exception {
        ApiResponse<Void> resp = ApiResponse.error(1001, "参数校验失败");
        String json = objectMapper.writeValueAsString(resp);

        assertThat(json).contains("\"code\":1001");
        assertThat(json).contains("\"message\":\"参数校验失败\"");
        assertThat(resp.data()).isNull();
    }
}

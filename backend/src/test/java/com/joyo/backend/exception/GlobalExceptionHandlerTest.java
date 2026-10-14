package com.joyo.backend.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joyo.backend.model.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "joyo.token=test-token")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    /** 仅用于触发各类异常的测试控制器 */
    @TestConfiguration
    static class TestControllerConfig {
        @RestController
        @RequestMapping("/api/v1/test-exception")
        static class ThrowingController {
            @GetMapping("/biz")
            public ApiResponse<Void> biz() {
                throw new BizException(ErrorCode.NOT_FOUND, "资源不存在");
            }

            @GetMapping("/unexpected")
            public ApiResponse<Void> unexpected() {
                throw new IllegalStateException("boom");
            }
        }
    }

    @Test
    @DisplayName("业务异常转换为对应错误码")
    void 业务异常转换为对应错误码() throws Exception {
        mockMvc.perform(get("/api/v1/test-exception/biz").header("X-Joyo-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("资源不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("未捕获异常转换为 1500 且不泄露内部细节")
    void 未捕获异常转换为内部错误() throws Exception {
        mockMvc.perform(get("/api/v1/test-exception/unexpected").header("X-Joyo-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1500))
                .andExpect(jsonPath("$.message").value("发生内部错误"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("boom"))));
    }
}

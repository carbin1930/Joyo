package com.joyo.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joyo.backend.exception.ErrorCode;
import com.joyo.backend.model.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 本地鉴权过滤器：所有接口（含 /health）要求请求头 X-Joyo-Token。
 * Token 由 Electron Main 每次启动生成随机 UUID 并通过 --joyo.token 下发，
 * 防止本机其他进程未授权调用（docs/10-architecture/overview.md 第 4 节）。
 */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = "X-Joyo-Token";

    private static final Logger log = LoggerFactory.getLogger(TokenAuthFilter.class);

    private final String expectedToken;
    private final ObjectMapper objectMapper;

    public TokenAuthFilter(@Value("${joyo.token}") String expectedToken, ObjectMapper objectMapper) {
        this.expectedToken = expectedToken;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(TOKEN_HEADER);
        if (expectedToken.equals(provided)) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Token 校验失败: uri={}", request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(ErrorCode.TOKEN_INVALID.getCode(), ErrorCode.TOKEN_INVALID.getMessage())));
    }
}

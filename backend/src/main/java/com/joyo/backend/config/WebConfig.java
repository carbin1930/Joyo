package com.joyo.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 配置：渲染进程页面源（dev: http://localhost:5173；prod: file:// null 源）
 * 与后端 http://127.0.0.1:{port} 之间属于跨域请求，必须放行。
 * 安全性不依赖 CORS：所有接口要求随机 X-Joyo-Token（TokenAuthFilter），
 * 恶意网页即便跨域成功也拿不到 Token。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}

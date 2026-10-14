package com.joyo.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Joyo 后端服务入口。
 * 以 Sidecar 进程运行，由 Electron Main 拉起：
 * java -jar joyo-backend.jar --server.port={port} --joyo.token={token}
 */
@SpringBootApplication
public class JoyoBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(JoyoBackendApplication.class, args);
    }
}

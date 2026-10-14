# progress.md — 开发进度与跨会话记忆

> AI 会话必读。只记录三类内容：①已完成事实 ②关键决策及原因 ③踩过的坑。
> 不记录对话过程。每次任务完成后更新，提交信息：`docs: 更新 progress`。

## 项目状态速览

- 当前版本：v0.1（spec-01 ~ spec-04）
- 已完成 spec：无
- 进行中：v0.1 启动序列——backend 骨架（已完成）

## 已完成

- [x] 2026-10-15 文档体系全部生成：PRD、路线图、架构、ADR-001、数据模型、spec-template、spec-01~04、API 契约、四份约定、CLAUDE.md
- [x] 2026-10-15 backend 骨架：Spring Boot 3.3.5（Java 21、Log4j2）+ `/api/v1/health` + TokenAuthFilter + 统一响应/全局异常 + 7 个单测全部通过（`mvn test` BUILD SUCCESS）

## 关键决策记录

| 日期 | 决策 | 原因 |
|---|---|---|
| 2026-10-15 | 架构采用 Electron + Vue3 + Spring Boot Sidecar | 作者 Java 背景；Java 生态轮子成熟；见 ADR-001 |
| 2026-10-15 | JDK 采用 21（LTS），非 17 | 作者明确要求；Spring Boot 3.3+ 完整支持 21 |
| 2026-10-15 | 接口错误表达用"HTTP 200 + 业务码"单轨制 | 降低前后端与 AI 维护成本，见 api-conventions.md |
| 2026-10-15 | 连接配置 CRUD 走 Electron IPC（Main 持久化），后端无状态 | 密码加解密只能发生在 Main；后端不落盘任何敏感信息 |
| 2026-10-15 | 数据库值一律字符串传输 | 避免 DECIMAL/BIGINT 精度丢失 |
| 2026-10-15 | 新增 F12 表/数据同步功能（规划 v0.5） | 用户明确要求，需求评审时补充 |
| 2026-10-15 | 后端日志框架确定为 Log4j2 | 用户明确指定；需排除 spring-boot-starter-logging |
| 2026-10-15 | 新增错误码 1003（Token 校验失败） | backend 骨架引入 TokenAuthFilter，已登记 rest-api.md |

## 已知问题与坑

- IDE 内置终端可能读不到新安装工具的 PATH（mvn），用完整路径 `D:\dev\tool\apache-maven-3.9.8\bin\mvn.cmd` 或重开终端解决
- Maven 阿里云镜像偶发 `No route to host`，重试即可恢复
- 控制台中文日志乱码是终端代码页（GBK）显示问题，不影响文件内容与测试结果

## 下一任务

1. frontend 骨架：Vite + Vue 3 + TS（strict）+ Pinia + 路由 + `api/http.ts`（统一响应解析、Token 注入）+ 目录约定


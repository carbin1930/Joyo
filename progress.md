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
- [x] 2026-10-15 frontend 骨架：Vite 6 + Vue 3.5 + TS strict + Pinia + Router(hash) + `api/http.ts` 统一请求层（Token 注入/ApiError/BackendUnavailableError）+ 后端状态卡首页 + 6 个 vitest 单测；typecheck/test/build 全部通过
- [x] 2026-10-15 desktop 骨架：Electron 33 主进程（sidecar 拉起：空闲端口/随机 Token/健康检查轮询/taskkill 进程树清理 + 开发直连 JOYO_DEV_BACKEND_URL）+ preload（contextBridge 暴露 getLaunchConfig）；sidecar 冒烟测试通过（spawn jar → code=0 → 1003 拦截 → 清理）
- [x] 2026-10-15 【spec-01 任务 1】Main 侧 `secure-store.ts`（connections.json 原子写/损坏兜底/脱敏视图 + 密码 enc:v1 密文）+ `SafeStorageCipher` + 4 个 IPC 通道注册（list/save/delete/decrypt）+ 10 个 vitest 单测全过；IPC 载荷契约已登记 rest-api.md
- [x] 2026-10-15 【spec-01 任务 2】后端连接接口：`POST /connections/test` + `POST/DELETE /connections/{id}/session`（HikariCP max=5/timeout 5s/惰性建池/首个连接探活）+ SQLException→2001/2002/2003 分类 + URL 路径编码；18 个测试全过（含 MockMvc controller 测试；Testcontainers 集成测试无 Docker 自动跳过）
- [x] 2026-10-15 【spec-01 任务 3】前端连接管理 UI：preload 扩展 connections IPC 白名单 + `api/ipc.ts`/`api/connection.ts` + connection store（connect 流程：解密→建会话→绿点；无密码/解密失败/建会话失败的边界处理）+ ConnectionList（右键菜单/二次确认/复制连接）+ ConnectionDialog（测试连接/密码显隐/编辑留空不改密码）+ BaseDialog/ToastHost/use-toast；前端 12 个单测全过，typecheck/build 通过
- [x] 2026-10-17 spec-01 实机验收通过（真实 MySQL 5.7.40 连通、密码密文、数据迁移），合入 main 并推送
- [x] 2026-10-17 【spec-02 任务 1】后端元数据接口：`GET /connections/{id}/schemas`（系统库排序在后）+ `/schemas/{schema}/tables`（BASE_TABLE/VIEW/行数估计）+ `/schemas/{schema}/tables/{table}`（列/索引/DDL）；`SqlIdentifiers` 标识符转义（SHOW CREATE TABLE 防注入）；2101 错误码；28 个测试全过（新增 MetadataIT 真实 MySQL 集成测试，覆盖特殊字符表名/视图/无会话 2004/表不存在 2101）

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
- pnpm 12：package.json 的 `pnpm` 字段已失效，依赖构建脚本白名单用 `pnpm-workspace.yaml` 的 `allowBuilds:` 键（`pnpm approve-builds --all` 可非交互批准并自动写回）
- pnpm 下 rollup 的 Windows 原生二进制可能装不上（ERR_DLOPEN_FAILED），需在 devDependencies 显式加 `@rollup/rollup-win32-x64-msvc`（版本与 rollup 一致）
- vitest 2 内置 vite 5 类型，与 vite 6 冲突，须用 vitest 3；vitest node 环境无 `window`，浏览器 API 取值用 `globalThis`
- Electron postinstall 需在 pnpm-workspace.yaml 的 `allowBuilds` 中加入 `electron: true`，否则二进制下载脚本被 pnpm 12 拦截
- Windows 杀后端进程树必须用 `taskkill /T /F`，直接 child.kill 会残留 java 进程；taskkill 强杀后子进程 exitCode 为 1，属正常

## 下一任务

1. 【spec-02 任务 2】前端 schema 树 + 表详情：lazy 树组件（连接→库→表→列）+ 表详情三 Tab（列/索引/DDL）+ 过滤框 + 刷新，联调端到端


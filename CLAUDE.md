# CLAUDE.md — Joyo AI 开发总入口

> 本文件是 AI 编码助手每次会话的必读入口。请先通读本文，再按需查阅索引中的文档。

## 项目是什么

Joyo：开源的 MySQL 桌面客户端。架构为 **Electron + Vue 3 + Spring Boot Sidecar**（Java 后端以独立进程运行，前端经 localhost HTTP 调用）。

## 当前状态

- 当前版本：**v0.1（开发中）**，范围 = spec-01 ~ spec-04，详见 `docs/00-product/roadmap.md`
- 实时进度、关键决策、已踩过的坑：**`progress.md`（每次会话必读）**

## 文档索引

| 需要了解 | 阅读 |
|---|---|
| 产品范围与版本规划 | `docs/00-product/PRD.md`、`roadmap.md` |
| 整体架构、进程生命周期、安全模型 | `docs/10-architecture/overview.md` |
| 架构决策理由 | `docs/10-architecture/adr/`（001 技术栈、002 Element Plus） |
| 本地数据存储与加密 | `docs/10-architecture/data-model.md` |
| 正在实现的功能规格 | `docs/20-specs/`（先看 spec-template 了解格式） |
| 接口定义（单一事实源） | `docs/30-api/rest-api.md` |
| 后端/前端/接口/Git 规范 | `docs/90-conventions/` 四个文件 |

## 铁律（违反任何一条都视为错误实现）

1. **规格先行**：只实现 spec 文档中定义的行为。发现 spec 缺陷或缺失 → 停下，先改 spec 文档（提交 `docs(spec-NN): ...`），经确认后再写代码
2. **接口单一事实源**：接口以 `rest-api.md` 为准；任何接口变更必须先改该文档，再同步改代码与两端类型
3. **任务粒度**：一次会话只做一个可独立验证的任务（如"后端连接测试接口 + 单测"），不越界实现后续 spec 的功能
4. **安全红线**：不得在任何文件写入真实密码/密钥/真实主机/本机私有路径；密码只能经 `secure-store.ts`（Main 进程 safeStorage）处理；后端只监听 127.0.0.1 且校验 `X-Joyo-Token`
5. **规范遵守**：代码风格、分层、异常、命名严格遵守 `docs/90-conventions/`；注释与文案用中文，标识符用英文
6. **完成即记录**：任务完成后按 `git-workflow.md` 提交，并更新 `progress.md`
7. **值一律字符串**：数据库值传输一律转字符串，前端不转 Number（DECIMAL/BIGINT 精度红线）
8. **会话卫生（用户明确要求）**：完成一个任务/里程碑，或会话较长时，**主动提醒**：①更新 progress.md ②提交推送 ③建议用户新开窗口继续（上下文过长消耗积分）。新会话开始时先读本文件与 progress.md 恢复上下文

## 开发环境

- Windows；前端 pnpm、后端 Maven + JDK 21
- 开发模式启动：见各子目录 README（backend: `mvn spring-boot:run`，frontend: `pnpm dev`，desktop: `pnpm dev`，环境变量 `JOYO_DEV_BACKEND_URL` 直连手启后端）

## 首个开发任务（v0.1 启动序列）

按顺序：搭建 backend 骨架（健康检查 + Token 过滤器 + 全局异常）→ frontend 骨架 → desktop 骨架（sidecar 拉起）→ spec-01。

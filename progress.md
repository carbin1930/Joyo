# progress.md — 开发进度与跨会话记忆

> AI 会话必读。只记录三类内容：①已完成事实 ②关键决策及原因 ③踩过的坑。
> 不记录对话过程。每次任务完成后更新，提交信息：`docs: 更新 progress`。

## 项目状态速览

- 当前版本：v0.1（spec-01 ~ spec-04）
- 已完成并合入 main：spec-01 连接管理、spec-02 库表浏览
- **进行中：spec-03 SQL 编辑器**（前后端代码完成，实机验收大部分通过；遗留一个待查问题，见"已知问题"第 1 条）
- 当前分支：`feat/spec-03-query-editor`（有未提交改动：SQL 补全体系、库切换联动、菜单中文化、文件日志等）
- **下次会话第一件事**：重启后端启用文件日志 → 复现"发生内部错误" → 读 `backend/logs/backend.log` 定位（详见"已知问题"第 1 条）

## 已完成（按时间）

- [x] 2026-10-15 文档体系全部生成：PRD、路线图、架构、ADR-001、数据模型、spec-template、spec-01~04、API 契约、四份约定、CLAUDE.md
- [x] 2026-10-15 启动序列三件套：backend 骨架（健康检查/Token 过滤器/统一响应/7 单测）+ frontend 骨架（Vite6/Vue3.5/TS strict/http.ts/状态卡/6 单测）+ desktop 骨架（sidecar 拉起/preload）；sidecar 冒烟测试通过
- [x] 2026-10-17 【spec-01】任务 1 连接存储（secure-store/SafeStorageCipher/4 IPC 通道/10 单测）+ 任务 2 连接接口（test/session/错误分类 2001-2004/18 测试）+ 任务 3 连接管理 UI（ConnectionList/ConnectionDialog/toast）+ 实机验收通过，合入 main
- [x] 2026-10-17 【spec-02 任务 1】后端元数据接口（schemas/tables/tableDetail + SqlIdentifiers 转义 + 2101 + MetadataIT）
- [x] 2026-10-17 【spec-02 任务 2】前端 schema 树 + 表详情（schema store 懒加载/SchemaTree/TableDetail 三 Tab/连接联动/19 单测）；实机验收通过，合入 main
- [x] 2026-10-18 【spec-03 任务 1】后端查询执行：`SqlSplitter`（字符串/注释感知拆分，16 单测）+ `POST /connections/{id}/queries`（多语句依次执行/失败中止/setMaxRows 截断检测/重复列名去重/全值字符串化）+ `QueryResultCache`（LRU 50/分页/1002 过期）+ 3001/3002 错误分类；53 测试全过
- [x] 2026-10-18 【spec-03 任务 2】前端 SQL 编辑器：`MonacoEditor` 封装 + `query` store + `QueryEditorView` + `ResultPanel`；24 单测全过
- [x] 2026-10-18 spec-03 实机验收过程中的增强与修复（详见各文档）：
- [x] 2026-10-18 【F09 多 SQL 页签提前实现】主区顶部页签（多 SQL 页签 + 日志页签移入顶栏，侧栏日志按钮移除）；每页签独立绑定库/草稿/结果，后端 QueryExecuteRequest 增加 database 字段（执行前按页签 USE，根治连接池切库残留）；result store 重构为多页签结构；前端 25 测试 + 后端 53 测试全过。日志弹窗保留为未连接时的兜底入口
  - 数据库选择器（顶栏，USE 切库）+ 树-编辑器联动（单击表自动切库）
  - SQL 补全体系：关键字/库名/表名/列名补全 + `别名.`、`表名.`、`库名.` 三级点号补全 + **按 SQL 子句动态优先级**（analyzeContext）+ 列名去重 + 库展开后台预取表结构（50 张上限）
  - 双击表插入：SQLyog 风格别名（下划线分段取首字母），当前库纯表名、跨库带库名限定
  -  风格库操作：双击库节点切库、当前库树中高亮
  - 中文应用菜单 + 单实例锁（防双实例抢缓存）+ 后端文件日志（logging.file.name + 菜单"打开后端日志"）

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
| 2026-10-17 | SQL 补全走"本地元数据 + 上下文优先级"，不请求后端 | 对象树已加载元数据够用，零延迟；v0.2 可加后端全量元数据 |
| 2026-10-18 | 双击插入别名规则（SQLyog 风格：下划线分段取首字母） | 用户要求参考主流工具；order_item → oi |
| 2026-10-18 | 新增 F13 日志查看（v0.2）+ 先落地文件日志 | 实机调试需要；控制台中文乱码且隐藏窗口看不到日志 |
| 2026-10-18 | 新增 F14 资源监控（v0.2）：右下角展示**全量进程**资源（Electron 各子进程 + Java 后端 JVM） | 用户要求；Electron 用 app.getAppMetrics，JVM 用 /health 扩展（Runtime 堆内存 + OperatingSystemMXBean 进程 CPU），主进程汇总 IPC 推送 |
| 2026-10-19 | 引入 Element Plus（全量导入，ADR-002）；结果网格定 Tabulator（spec-04） | 用户确认；手写组件样式成本高；桌面端体积不敏感；主题映射 --el-* → --joyo-*；渐进迁移不一次性重写 |

## 已知问题与坑

### 待查问题（下次会话优先）

1. 【已关闭 2026-10-19】执行 SQL 偶发"发生内部错误"（1500）：加文件日志后无法复现，判定为后端旧进程的偶发状态。文件日志已常态化，复发时读 `backend/logs/backend.log` 即可定位。
2. 【待实机验收】多 SQL 页签（拖拽排序/每页签独立库）与 Element Plus 第一批替换（下拉/按钮/弹窗）尚未实机过一遍——下个会话开始时先验证，再提交合并。

### 工程坑（已解决，留档防复发）

- IDE 内置终端可能读不到新装工具的 PATH（mvn），用完整路径或重开终端
- Maven 阿里云镜像偶发 `No route to host`，重试即可
- 控制台中文日志乱码是终端代码页（GBK）显示问题；后端窗口先 `chcp 65001`
- pnpm 12：`package.json` 的 `pnpm` 字段已失效，构建脚本白名单用 `pnpm-workspace.yaml` 的 `allowBuilds:`（`pnpm approve-builds --all` 非交互批准）
- pnpm 下 rollup 的 Windows 原生二进制可能装不上（ERR_DLOPEN_FAILED），devDependencies 显式加 `@rollup/rollup-win32-x64-msvc`（版本同 rollup）
- vitest 2 内置 vite 5 类型与 vite 6 冲突，须用 vitest 3；vitest node 环境无 `window`，用 `globalThis`
- Electron postinstall 需在 `allowBuilds` 加 `electron: true`，否则二进制下载被 pnpm 12 拦截
- Windows 杀进程树必须 `taskkill /T /F`；强杀后子进程 exitCode=1 属正常
- 【重要】渲染进程 → 后端是跨域请求：后端须配 CORS 且 TokenAuthFilter 放行 OPTIONS 预检，缺一表现为"后端服务异常"
- PowerShell 5.1 执行含中文的 .ps1 必须保存为 UTF-8 with BOM
- PowerShell 下 pnpm 优先调 pnpm.ps1 被执行策略拦截：`Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` 或用 `pnpm.cmd`
- 【重要】新版 JDK（21.0.6+）禁止 Mockito/ByteBuddy 动态自附加：Surefire `argLine=-Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading`（已配 pom.xml）
- 分支事故教训：切新分支前必须基于最新 main；切完立即核对基线（spec-02 曾漏带 spec-01 收尾提交，cherry-pick 补救）
- URLEncoder 是表单编码（空格→+），URL 路径需 `%20`
- 【重要】monaco-editor 0.57 的 exports 映射 `./*` → `./esm/vs/*.js`：worker 导入路径**不带** `esm/vs` 前缀（写成 `esm/vs/...` 会被拼成双倍路径）
- 【重要】`import * as monaco` 的模块命名空间对象不可扩展，不能挂全局标志，用 `globalThis`
- 【重要】通过 shell 写文件时 `&&`/`||` 会被替换成 `;`，写入后必须全量检查运算符（本次 MonacoEditor 连续踩中）
- Electron 双实例共用 userData 会报缓存锁冲突（Unable to create cache/0x5）；已加 `app.requestSingleInstanceLock` 单实例锁根治
- CSS 百分比高度坑：父容器只有 min-height 时子元素 `height: 40%` 塌陷为 0，改用 flex 布局
- 后台异步预取会在 vitest 用例间竞态：用例断言前 `await new Promise(r => setTimeout(r, 0))` 排空

### 设计限制（backlog，非缺陷）

- USE 切库经连接池后上下文会残留（HikariCP 检测不到 USE 语句）；单用户场景无影响，多标签/多会话改造时换会话级方案
- 表/列补全范围 = 已浏览库的元数据（预取上限 50 表/库）；全量补全需后端元数据缓存（v0.2 评估）

## 下一任务

1. 【实机验收本轮改动】多 SQL 页签（拖拽排序/每页签独立库/后端按页签 USE）+ Element Plus 第一批替换——重启应用逐项确认，问题修完
2. 【提交合并】验收通过后提交全部改动，合并回 main
3. 【spec-04】结果网格升级：**Tabulator**（已决策，ADR-002）——复制 CSV/TSV、列宽拖拽、虚拟滚动，替换 ResultPanel 内的自研表格 → v0.1 收官打 tag `v0.1.0`
4. v0.2 启动时：为 F13/F14/F05/F06 编写正式 spec（F13 精简版已提前落地）

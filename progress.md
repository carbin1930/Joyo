# progress.md — 开发进度与跨会话记忆

> AI 会话必读。只记录三类内容：①已完成事实 ②关键决策及原因 ③踩过的坑。
> 不记录对话过程。每次任务完成后更新，提交信息：`docs: 更新 progress`。

## 项目状态速览

- 当前版本：v0.1（spec-01 ~ spec-04）
- 已完成并合入 main：spec-01 连接管理、spec-02 库表浏览、spec-03 SQL 编辑器（含多 SQL 页签/F13 精简日志/Element Plus 第一批替换，提交 `1f68538`）
- 当前分支：`main`（与 origin/main 同步，工作区干净）
- 实机验收：spec-03 增强改动已通过（多 SQL 页签正常；"内部错误 1500" 未再复现）
- **v0.2 进行中**：spec-05 查询历史已实现（待实机验收）；下一个 spec-06 SSH 隧道
- 待办：`QueryEditorView.vue` 已 529 行超出组件规范（300 行），下个 spec 间隙拆分（SQL 页签条 / 工具行 / 分割区各自成组件）
- F08 表数据编辑范围已确认（增删改 + 编辑锁 + 保存确认），按 roadmap 排在 v0.4，未提前实现

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
- [x] 2026-10-25 【spec-05 变更】去重改逐次记录：每次执行新增一条（相同 SQL 不合并，保留执行轨迹），spec 行为 2/验收标准与实现、单测同步回写
- [x] 2026-10-25 【spec-05 查询历史】实现完成：`history-store.ts`（JSON + 原子写/上限/损坏兜底，10 单测）+ IPC 四通道 + preload + 类型镜像；`execute` 成败均记录（失败 kind=ERROR）；`HistoryPanel.vue` 浮层（搜索防抖/双击回填/单项删除/清空二次确认）；设计变更：better-sqlite3 → JSON（避免 native 依赖），spec 已回写定稿
- [x] 2026-10-25 【v0.1 收官】实机验收通过，打 tag `v0.1.0`；v0.2 四份 spec（05 查询历史 / 06 SSH 隧道 / 13 日志查看 / 14 资源监控）定稿，rest-api.md 同步（history 改走 Main IPC、错误码 2005、/logs 与 /health 扩展契约）
- [x] 2026-10-22 【spec-04 任务 3】编辑器/结果区上下拖拽分割（`splitter`，编辑器 30%~75%，拖拽时全局锁定光标）+ 状态条抽为 `ResultStatusBar.vue`（截断至 N 行/隐藏列提示/“排序仅本页”/页码/每页 50·100·200·500）；query store 增加 `loadPage`/`changePageSize`（改页大小回第 1 页）+ 单测
- [x] 2026-10-22 【spec-04 任务 2】结果网格复制：`Ctrl+C` 选区/整行 → TSV（`toTsv`）；右键菜单（`ResultContextMenu`）复制为 CSV（带表头、RFC4180 转义）/TSV，无选区时复制当前页整页；双击单元格进入编辑态仅用于复制（提交后 `cellEdited` 还原原值，不写库）；复制结果 toast 提示
- [x] 2026-10-22 【spec-04 任务 1】结果网格接入 Tabulator 6.7（`tabulator-tables` + `@types/tabulator-tables`）：`ResultGrid.vue` 只读网格（行号列冻结/列宽拖拽/虚拟滚动/NULL 灰色斜体居中/BLOB 占位/超长 tooltip 2000 字符/50 列上限提示/0 行占位）；`use-result-grid.ts`（列截断、行号跨页连续、行数据转换）+ `use-clipboard.ts`（TSV/CSV 格式化、NULL→空串、RFC4180 转义）及 8 单测；主题映射写入 main.css；typecheck/build 通过，33+ 前端测试全过

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
- 【待修】`pnpm lint` 当前跑不起来：`eslint.config.js` 依赖 `globals` 包但未声明安装（ERR_MODULE_NOT_FOUND），与业务代码无关
- Tabulator 6.x 不自带类型声明，需另装 `@types/tabulator-tables`（版本 6.3.6 对应 lib 6.7 可用）
- 【已修】`el-select` 的 `@change` 回调参数是**选中值**（string），不是 DOM Event；沿用原生 `select` 写法 `e.target.value` 会抛 TypeError，表现为"下拉选了没反应"（迁移 Element Plus 时踩中）

### 设计限制（backlog，非缺陷）

- USE 切库经连接池后上下文会残留（HikariCP 检测不到 USE 语句）；单用户场景无影响，多标签/多会话改造时换会话级方案
- 表/列补全范围 = 已浏览库的元数据（预取上限 50 表/库）；全量补全需后端元数据缓存（v0.2 评估）

## 下一任务

1. 【spec-04】结果网格升级：**Tabulator**（已决策，ADR-002）——复制 CSV/TSV、列宽拖拽、虚拟滚动，替换 ResultPanel 内的自研表格
2. v0.1 收官：全量回归 + 打 tag `v0.1.0`
3. v0.2 启动时：为 F13/F14/F05/F06 编写正式 spec（F13 精简版已提前落地）

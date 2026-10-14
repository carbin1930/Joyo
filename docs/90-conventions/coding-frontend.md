# 前端编码规范（Vue 3 + Electron）

> 文档状态：已定稿 | 适用范围：`frontend/` 与 `desktop/` 全部代码

## 1. 基础约定

- Vue 3 `<script setup lang="ts">` + TypeScript（strict）+ Vite + Pinia + Vue Router（hash 模式）
- 样式：普通 CSS + CSS 变量做主题（`var(--joyo-primary)` 等），不引入 UI 大框架；弹窗、表单等基础组件自建（放 `components/base/`）
- 注释中文，标识符英文

## 2. 目录职责（frontend/src）

| 目录 | 职责 | 规则 |
|---|---|---|
| api/ | 后端 HTTP 封装 | 每个资源一个文件（`connection.ts`、`query.ts`）；统一走 `http.ts` 的 fetch 包装 |
| components/ | 通用组件 | PascalCase 文件名；props 用 `defineProps<T>()` 类型化 |
| components/base/ | 自建基础组件 | `BaseDialog.vue`、`BaseButton.vue`、`BaseForm.vue`… |
| views/ | 页面 | 按主布局分区：`SidebarView`（连接+树）、`EditorView`、`ResultPanel` |
| stores/ | Pinia | 每领域一个：`connection.ts`、`schema.ts`、`query.ts`、`result.ts` |
| composables/ | 组合式函数 | `use-clipboard.ts` 等，use- 前缀 |
| types/ | 类型定义 | `api.d.ts` 与 rest-api.md 的 DTO 一一对应；IPC 类型在 `ipc.d.ts` |

## 3. HTTP 层（api/http.ts）

- 唯一出口：`request<T>(method, path, body?)` → 自动拼 baseUrl、注入 `X-Joyo-Token`（来自 preload 注入的启动参数）
- 响应处理：`code === 0` 返回 data；非 0 抛 `ApiError(code, message)`，由调用方或全局 toast 处理
- 网络错误/后端崩溃：统一上抛特殊错误 `BackendUnavailableError`，App 级监听并显示"后端服务异常"遮罩（含重启按钮）

## 4. 状态管理约定

- store 持有领域状态与异步 action；组件内局部 UI 状态（loading、表单草稿）不放 store
- `connection.ts`：连接列表、当前连接、已连接集合
- `query.ts`：编辑器草稿（按连接隔离）、执行中状态、语句队列结果
- `result.ts`：当前结果 Tab 集合、每个 Tab 的 queryId 与分页状态
- 禁止在 store 外直接调用 `api/`（保证数据流可追踪）

## 5. Electron（desktop/）

- Main 与 Renderer 通信**只经 preload 的 contextBridge 白名单**，通道名 `域:动作`（`connections:save`）
- `sidecar.ts`：后端进程管理唯一模块（spawn、健康检查、退出清理），按 `overview.md` 第 2 节实现
- `secure-store.ts`：connections.json 读写 + safeStorage 加解密唯一模块，原子写
- Renderer 永远不直接 `require('electron')`；`window.joyo.*` 类型定义在 `types/ipc.d.ts`

## 6. 组件与 UI 细节

- 组件文件 ≤ 300 行，超出则拆子组件
- 所有用户可见文案集中 `src/locales/zh-CN.ts`（为将来国际化预留，v0.1 不做多语言框架）
- 错误提示统一 `use-toast.ts`：右下角 toast，error 级别默认 5 秒，可手动关闭
- Monaco 与 Tabulator 封装为 `MonacoEditor.vue`、`ResultGrid.vue`，业务代码不直接操作其原生实例

## 7. 测试

- Vitest：`api/http.ts` 错误处理、store 逻辑、CSV/TSV 格式化函数必须有单测
- 组件测试 v0.1 不强制，`ResultGrid` 的复制逻辑优先覆盖

## 8. 工具链

- pnpm 工作区；ESLint（flat config）+ Prettier；`pnpm typecheck` 必须零错误才能提交
- 命名速查：组件 PascalCase / 组合式函数 use-xxx / 常量 UPPER_SNAKE / 其余 camelCase

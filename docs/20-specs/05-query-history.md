# spec-05：查询历史

- 规格编号：spec-05
- 功能名称：查询历史（F05）
- 所属版本：v0.2
- 状态：已定稿（2026-10-25 确认；存储实现由 SQLite 调整为 JSON，见第 3 节实现说明）
- 依赖 spec：spec-03（SQL 编辑器）

## 1. 功能描述

执行过的 SQL 自动保存历史，支持关键词搜索与一键回填到编辑器（PRD US-06）。历史是纯本地数据，与连接配置同层：**持久化在 Electron Main 进程（SQLite），不经过 Java 后端**（后端保持无状态，无需新增依赖）。

## 2. 界面设计

- 入口：编辑器工具行"执行"按钮旁新增 `历史` 按钮；打开浮层面板（宽 420px、高约 60%，右上角关闭）。
- 面板结构：顶部搜索框（占位"搜索 SQL…"）；中间历史列表；底部"清空历史"按钮。
- 列表项两行：第一行 SQL 单行截断（等宽字体）；第二行灰色小字 `连接名 · 库 · MM-DD HH:mm · 12ms / 100行`。
- 状态：空态显示"暂无历史"；失败条目左侧红色竖条，错误信息放 tooltip；加载失败（IPC 异常）toast 提示。
- 交互：单击选中；双击**回填**（替换编辑器全部内容、关闭面板、焦点回编辑器）；条目 hover 显示 `×` 删除单项。
- 清空历史需二次确认弹窗。

## 3. 接口依赖

不走 REST。新增 IPC 通道（Main 实现，渲染层经 preload 暴露），需登记到 `rest-api.md` 第 2 节 IPC 白名单表：

| 通道 | 入参 | 返回 |
|---|---|---|
| `history:append` | `HistoryEntry`（不含 id） | `void` |
| `history:list` | `{ keyword?: string, limit?: number }`（limit 默认 50，最大 200） | `HistoryItem[]`（executedAt 倒序） |
| `history:delete` | `id: number` | `boolean` |
| `history:clear` | 无 | `void` |

```ts
// 类型镜像到 frontend/src/types/ipc.d.ts
interface HistoryEntry {
  sql: string; connectionName: string; database: string
  kind: string;                 // SELECT/UPDATE/.../MULTI
  elapsedMs: number; rowCount: number | null
  success: boolean; errorMessage: string | null
  executedAt: string            // ISO-8601 UTC
}
interface HistoryItem extends HistoryEntry { id: number }
```

存储（实现说明）：原设计 better-sqlite3，实现时调整为 **`%APPDATA%/joyo/history.json`（JSON + 临时文件原子重命名，与 connections.json 同模式）**——避免为 1000 条上限的追加型数据引入 native 模块与 electron-rebuild 链路，desktop 保持零 runtime 依赖。文件结构 `{ version: 1, nextId, items: HistoryItem[] }`；损坏文件重命名 `.broken.*` 保留现场后重建（与 connections.json 同规则）。唯一实现模块 `desktop/src/main/history-store.ts`。

## 4. 行为细节

1. **记录时机**：query store `execute()` 结束后（成功与失败都记）调用 `history:append`；多语句记整段原始 SQL 一次，`kind='MULTI'`、`rowCount=null`；单语句记实际 kind / elapsedMs（总耗时）/ rowCount（SELECT 取 totalRows，其余 null）；**失败条目 `kind='ERROR'`、`elapsedMs=0`**（错误信息入 errorMessage）。
2. **去重**：append 前按 `sql` 逐字节精确匹配查重，命中则**原位更新**该行 executed_at 与结果字段，不新增行（相同 SQL 永远只有一条）。
3. **上限**：append 后若总行数 > 1000，按 id 升序删除最旧直至 1000。
4. **搜索**：`sql LIKE '%keyword%'`，大小写不敏感（SQLite `LIKE` 默认对 ASCII 不敏感，足够 v0.2）。
5. **回填**：替换 Monaco 全部内容，不自动执行。

## 5. 边界情况

| 场景 | 期望表现 |
|---|---|
| 历史记录失败（db 打不开等） | 静默失败，记 console.warn，不影响查询本身 |
| 超长 SQL（> 10KB） | 照常存储；列表显示截断，回填完整 |
| 清空时列表为空 | 按钮置灰 |
| 搜索无结果 | 空态文案"无匹配历史" |
| 删除单项后列表 | 局部移除，不整表刷新 |

## 6. 验收标准

- [ ] 执行 SQL（成功/失败各一次）后打开历史，最近一条为刚执行的语句，失败项标红
- [ ] 同一 SQL 连续执行 3 次，列表仍只有 1 条且时间更新为最后一次
- [ ] 关键词搜索大小写不敏感命中子串
- [ ] 双击条目，编辑器内容被替换且焦点回到编辑器
- [ ] 手工插入 1001 条（测试通道或脚本）后再执行 1 条，总行数为 1000 且最旧的被删除
- [ ] 重启应用历史仍在；清空有二次确认，清空后列表为空态

## 7. 扩展点（只设计不实现）

- 按连接过滤、按日期分组、导出历史（v1.0 设置面板顺路）
- 条数上限/保留天数做成设置项（F10 预留字段）

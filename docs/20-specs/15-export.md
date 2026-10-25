- 规格编号：spec-15
- 功能名称：导出（F07）
- 所属版本：v0.3
- 状态：草稿（待确认）
- 依赖 spec：spec-01（连接模型）、spec-04（结果网格，复用其列头文案与格式化约定）、spec-13（`shell:show-item` IPC）

## 1. 功能描述

把查询结果或整表数据导出为 **CSV / SQL INSERT / Excel(.xlsx)** 文件（PRD F07、US-08）。导出由后端流式生成（大结果集不占内存），Electron Main 中转写盘（渲染层无文件系统权限）。

两个入口共享同一条导出链路，差异仅是 SQL 来源：

| 入口 | 位置 | SQL 来源 |
|---|---|---|
| 查询结果导出 | 结果网格工具栏"导出"按钮 | 当前页签执行过的 SQL（重跑导出） |
| 整表导出 | 表结构页顶部"导出数据"按钮 | `SELECT * FROM \`db\`.\`table\`` |

## 2. 界面设计

**导出弹窗**（两个入口共用 `ExportDialog.vue`）：

- 格式单选：`CSV` / `INSERT` / `Excel(.xlsx)`，默认 CSV
- 选择 INSERT 时显示"表名"输入框（必填，生成 `INSERT INTO \`表名\` (...) VALUES ...`；JOIN 结果需用户手填目标表名）
- 行数上限：输入框，空 = 全部；限制 1-5,000,000
- 按钮：`导出`（触发系统保存对话框）+ `取消`
- 导出中：按钮 loading + 文案"导出中…"；完成后 toast 成功，并附"打开目录"动作（复用 `shell:show-item`）

**结果网格工具栏**：在"复制为 CSV/TSV"旁新增"导出"按钮（仅查询类结果可导出；错误/空结果禁用）。

## 3. 接口依赖

1. **后端**：`POST /export`（rest-api.md 预留节，v0.3 落地为流式响应）：

```
POST /api/v1/export
X-Joyo-Token: <token>
Content-Type: application/json

{ "connectionId": "...", "database": "db1", "sql": "SELECT ...",
  "format": "csv | insert | xlsx", "tableName": "t1", "rowLimit": 100000 }
```

- 响应**不是** ApiResponse JSON，而是文件流：
  - `Content-Type`：`text/csv; charset=UTF-8` / `application/sql` / `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  - `Content-Disposition: attachment; filename="export.<ext>"`
- 开流前失败（参数校验 1001、连接不存在 2001、SQL 执行错误 3xxx）→ 正常 ApiResponse 错误 JSON
- 开流后失败 → 连接中断（客户端感知 read 不完整）；Main 落盘前先写临时文件 `.part`，成功后 rename 为目标文件，半成品不会残留为正式文件

2. **格式细则**：

- **CSV**：UTF-8 **带 BOM**（Excel 直开中文不乱码）；RFC 4180 转义（含逗号/引号/换行的字段加双引号包裹）；NULL → 空串；第一行列头用列名
- **INSERT**：逐行 `INSERT INTO \`t\` (\`c1\`,...) VALUES (...);`；字符串值转义 `'` → `''`，NULL → `NULL`，数值/布尔裸值；整文件包 `SET NAMES utf8mb4;` 开头
- **xlsx**：Apache POI `SXSSFWorkbook`（滑动窗口流式写，内存可控）；第一行列头；NULL 空单元格；日期按 `yyyy-MM-dd HH:mm:ss`

3. **Electron Main**：IPC `export:run`（invoke）：

```ts
// 渲染层传入导出参数 + 保存对话框默认文件名
window.joyo.exportRun(params): Promise<{ canceled: true } | { canceled: false; filePath: string; bytes: number }>
```

流程：`dialog.showSaveDialog`（用户取消 → `{ canceled: true }`）→ Main 携 token fetch `/export` 流 → 管道写入 `<目标>.part` → 成功 rename → 返回字节数。大文件全程不进渲染层内存。

## 4. 行为细节

1. 查询结果导出重跑当前 SQL：以页签当前 SQL 与所绑库为准（spec-09 每页签独立库）；导出结果可能与网格显示行数不同（网格有分页上限，导出默认全部）。
2. token 鉴权与常规接口一致（X-Joyo-Token 请求头）。
3. 导出过程不阻塞 UI；同一页签允许并发导出（不同文件名）。
4. 文件名默认：`<表名或"query-result">-<yyyyMMdd-HHmmss>.<ext>`。

## 5. 边界情况

| 场景 | 期望表现 |
|---|---|
| 0 行结果 | CSV/xlsx 只有表头；INSERT 为空文件（仅 SET NAMES 行） |
| INSERT 未填表名 | 弹窗校验拦截，不发请求 |
| SQL 执行失败 | toast 展示后端错误信息，无半成品文件 |
| 保存对话框取消 | 直接返回，无请求发出 |
| 流中途中断（后端崩溃） | Main 删除 `.part` 文件，toast"导出失败" |
| 10 万行大表 | 流式导出，UI 不卡，内存平稳 |
| rowLimit 越界 | 1001 参数错误 |

## 6. 验收标准

- [ ] 三种格式 1 万行表导出成功；CSV/INSERT 可被 MySQL 重新导入，xlsx 可被 Excel 打开
- [ ] CSV 含中文与逗号/引号字段时，Excel 打开不乱码、列不错位
- [ ] 导出 10 万行：后端堆内存平稳（状态栏 JVM 观测），UI 不卡顿
- [ ] 取消保存对话框不产生任何请求与文件
- [ ] SQL 报错时 toast 报错且目标目录无残留文件
- [ ] 整表导出与查询结果导出共用同一弹窗与链路

## 7. 扩展点（只设计不实现）

- 导出进度回调（行数百分比）、JSON / Markdown 格式、选择性列导出、定时导出；INSERT 改为批量多值语句（`VALUES (...),(...)` 每千行一批）提速导入

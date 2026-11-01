- 规格编号：spec-20
- 功能名称：查询中断（rest-api 预留端点落地）
- 所属版本：v0.4 补做（2026-10-27 补齐）
- 状态：已定稿并实现（2026-10-27。实现说明：执行请求带客户端自选 `clientQueryId`，后端用 `ConcurrentHashMap` 登记执行中的 JDBC `Statement`，`POST /queries/{queryId}/cancel` 调 `Statement.cancel()` 中断；前端执行中把"运行"旁追加"停止"按钮。中断后原执行请求失败并按常规错误 toast）
- 依赖 spec：spec-03（查询执行）

## 1. 功能描述

长查询跑起来后能立刻停掉，而不是干等到超时（`Statement.setQueryTimeout` 才生效）。用户在执行期间点"停止"，后端取消 MySQL 上正在执行的语句。

## 2. 界面设计

SQL 工具栏：`运行` 按钮在执行中变为禁用态"执行中…"，其右侧出现红色 **`停止`** 按钮；点击后按钮保持 loading 直到后端取消生效（原请求随即失败并 toast）。查询结束后 `停止` 消失。

## 3. 接口依赖

1. `POST /connections/{connectionId}/queries`：请求体新增可选字段 `clientQueryId`（客户端生成的本次执行标识）
2. `POST /api/v1/queries/{queryId}/cancel`：

```json
// 响应 data
{ "queryId": "q-1712345678-ab12cd", "canceled": true }
```

- `canceled=false`：该 id 当前不在执行中（已结束或不存在），无副作用
- `queryId` 为空 → 1001

## 4. 行为细节

1. `clientQueryId` 由前端在执行开始时生成（时间戳 + 随机串），随执行请求发出；后端在 `Statement.execute` 前登记、`finally` 里注销，避免句柄泄漏
2. 取消调用 JDBC `Statement.cancel()`；驱动侧失败（需另开连接发 KILL QUERY）只记日志，不掩盖原查询错误
3. 取消成功后，原执行请求以 SQL 执行错误/中断结束，前端按常规错误 toast，`executing` 复位
4. 多语句批量执行时，`clientQueryId` 对应"当前正在执行的那条语句"；取消只中断该条

## 5. 边界情况

| 场景 | 期望表现 |
|---|---|
| 点击停止时查询已结束 | 后端返回 `canceled=false`，前端无副作用 |
| 未传 clientQueryId | 后端不登记句柄，取消返回 false（不影响执行） |
| 取消后原请求报错 | 按常规查询错误 toast，可重新执行 |
| 并发多页签执行 | 各页签自带 clientQueryId，互不影响 |

## 6. 验收标准

- [ ] 执行 `SELECT SLEEP(20)` 期间点"停止"，2 秒内查询结束并提示错误
- [ ] 查询已结束时调用取消接口返回 `canceled=false`，不报错
- [ ] 停止后 UI 恢复可执行状态

## 7. 扩展点（只设计不实现）

- 取消范围选择（中断整批 / 仅当前语句）、取消原因回传、查询进度反馈（已扫描行数）

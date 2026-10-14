# API 通用约定

> 文档状态：已定稿 | 下游文档：`docs/30-api/rest-api.md`
> 所有接口设计与前后端实现必须遵守本文；修改本文需记入 `progress.md`。

## 1. URL 与版本

- 格式：`/api/v1/{资源}[/{资源id}[/{子资源}]]`
- 资源名用**复数名词 + kebab-case**：`/connections`、`/query-history`
- 大版本升级（破坏性变更）时 v1 → v2 并存，旧版保留一个迭代周期

## 2. 请求

- 方法语义：GET 查询、POST 创建/执行类动作、PUT 全量更新、DELETE 删除
- 动作型端点允许动词（REST 惯例的务实妥协）：`/connections/test`、`/connections/{id}/session`
- Content-Type：`application/json; charset=utf-8`
- 认证：所有请求携带 `X-Joyo-Token`（Electron Main 每次启动生成的随机 UUID）

## 3. 响应

**统一 HTTP 200 + 业务码**（本项目的明确决策：不做"HTTP 状态码表达业务错误"的双轨制，降低前后端与 AI 维护成本）：

```json
{ "code": 0, "message": "ok", "data": { } }
```

| 字段 | 说明 |
|---|---|
| code | 0 = 成功；非 0 见错误码表 |
| message | 成功固定 `ok`；失败为**可直接展示给用户**的中文文案，技术细节放 `detail`（可选字段） |
| data | 成功时的业务数据；失败固定 `null` |

- 布尔字段以 `is`/`has`/`can` 开头或用形容词（`nullable`、`truncated`、`unique`）
- 分页响应统一含：`page`、`pageSize`、`totalRows`、`totalKnown`（总量未知时 false，如流式结果）
- 所有值为字符串优先：数据库值一律转字符串传输，杜绝 JS 精度问题

## 4. 错误码分段

| 分段 | 领域 |
|---|---|
| 1xxx | 通用（1001 参数、1002 不存在、1500 内部错误） |
| 2xxx | 连接与会话（2001-2099 连接，2101-2199 元数据） |
| 3xxx | 查询执行 |
| 4xxx | 预留（导出等） |

新增错误码必须：落在正确分段 → 在 `rest-api.md` 错误码表登记 → 提供用户可读的 message。

## 5. 变更流程

1. 接口变更（新增/修改/废弃）先改 `rest-api.md`，再改代码
2. 修改同步两端类型定义：后端 DTO 与前端 `frontend/src/types/api.d.ts`
3. 废弃的端点在响应 message 中带 `[deprecated]` 前缀一个迭代周期后删除

# Joyo REST API 契约

> 文档状态：已定稿 | 上游文档：`docs/90-conventions/api-conventions.md`（必须先读）
> 本文件是接口的**单一事实源**：代码与本文冲突时，以本文为准并立即修正代码。
> 新接口必须先在此登记，再开始编码。

## 0. 通用约定（摘要，详见 api-conventions.md）

- Base URL：`http://127.0.0.1:{port}/api/v1`
- 所有请求（含 `/health`）必须携带请求头 `X-Joyo-Token: {main进程下发的token}`
- 统一 HTTP 200 + 业务码：`{ "code": 0, "message": "ok", "data": ... }`
- 时间戳：ISO-8601 UTC（`2026-10-15T10:00:00Z`）
- 分页参数：`page`（1 起）、`pageSize`（默认 100）

## 1. 系统

### GET /health

健康检查，供 Electron Main 启动轮询。

```json
{ "code": 0, "message": "ok", "data": { "app": "joyo-backend", "version": "0.1.0", "uptimeSec": 12 } }
```

## 2. 连接管理（spec-01）

### POST /connections/test

用表单当前值直接测试连接（无需先保存）。

请求：
```json
{
  "host": "127.0.0.1", "port": 3306,
  "username": "root", "password": "plain-or-decrypted",
  "defaultDatabase": "", "sslMode": "PREFERRED"
}
```

成功响应 data：
```json
{ "serverVersion": "8.0.36", "elapsedMs": 35 }
```

### 连接配置 CRUD（持久化在 Electron Main，不经过后端）

连接配置的增删改查由前端直接经 IPC 调用 Main 进程完成（见 `data-model.md`），**后端不提供这些端点**。IPC 通道白名单：

| 通道 | 方向 | 说明 |
|---|---|---|
| `connections:list` | R→M→R | 返回连接列表（passwordEnc 脱敏为 `hasPassword: boolean`） |
| `connections:save` | R→M→R | 新建/更新；`password` 明文可选字段，Main 负责加密 |
| `connections:delete` | R→M→R | 按 id 删除 |
| `secure:decrypt` | R→M→R | 解密密码，仅用于编辑表单回填 |

载荷契约（类型定义对应 `desktop/src/main/secure-store.ts`，前端镜像到 `frontend/src/types/ipc.d.ts`）：

```ts
// 列表项（所有通道的返回视图均不含 passwordEnc）
interface ConnectionListItem {
  id: string; name: string; host: string; port: number; username: string
  defaultDatabase: string; sshTunnel: null
  createdAt: string; updatedAt: string   // ISO-8601 UTC
  hasPassword: boolean
}

// connections:save 入参（id 缺省 = 新建；password 留空 = 编辑时不修改原密码）
interface SaveConnectionInput {
  id?: string; name: string; host: string; port: number
  username: string; password?: string; defaultDatabase?: string
}

// connections:delete 入参 id: string，返回 boolean（是否删除）
// secure:decrypt 入参 id: string，返回 string | null（null = 连接不存在或解密失败）
```

约定：更新不存在的 id → handler 抛错（渲染层收到 rejected promise，message 为中文文案）。

### POST /connections/{connectionId}/session

建立会话：后端按参数创建 HikariDataSource 并缓存（connectionId 为前端生成的 UUID）。

请求体同 `/connections/test`（明文密码仅经 localhost 回环传输，后端不落盘）。

成功响应 data：
```json
{ "connectionId": "b3c1...", "serverVersion": "8.0.36", "defaultDatabase": "" }
```

### DELETE /connections/{connectionId}/session

断开会话并销毁连接池。响应 data：`null`。

## 3. 元数据（spec-02）

### GET /connections/{connectionId}/schemas

返回 data：
```json
{ "schemas": [ { "name": "joyo", "isSystem": false }, { "name": "mysql", "isSystem": true } ] }
```

### GET /connections/{connectionId}/schemas/{schema}/tables

返回 data：
```json
{ "tables": [ { "name": "user", "type": "BASE_TABLE", "engine": "InnoDB", "comment": "用户表", "rowsEstimate": 1024 } ] }
```
`type` ∈ `BASE_TABLE | VIEW`。

### GET /connections/{connectionId}/schemas/{schema}/tables/{table}

表详情。返回 data：
```json
{
  "columns": [
    { "name": "id", "dataType": "bigint", "length": null, "nullable": false,
      "key": "PRI", "defaultValue": null, "extra": "auto_increment", "comment": "主键", "ordinal": 1 }
  ],
  "indexes": [
    { "name": "PRIMARY", "type": "BTREE", "unique": true, "columns": ["id"] }
  ],
  "ddl": "CREATE TABLE `user` (...)"
}
```

## 4. 查询执行（spec-03）

### POST /connections/{connectionId}/queries

请求：
```json
{ "sql": "SELECT id, name FROM user; SELECT 1;", "maxRows": 1000, "queryTimeoutSec": 30, "database": "joyo" }
```

`database`（可选）：目标数据库。非空时后端在执行前先 `USE`（反引号转义），
用于多 SQL 页签各自绑定库的场景，避免连接池库上下文残留。

响应 data（多语句 → `statements` 数组，顺序与拆分结果一致）：
```json
{
  "statements": [
    {
      "ordinal": 1,
      "kind": "SELECT",
      "queryId": "9f8e...",
      "columns": [
        { "name": "id", "type": "BIGINT", "nullable": false },
        { "name": "name", "type": "VARCHAR", "nullable": true }
      ],
      "totalKnown": true,
      "totalRows": 1024,
      "truncated": false,
      "rows": [ ["1", "alice"], ["2", null] ],
      "page": 1, "pageSize": 100,
      "elapsedMs": 12
    },
    { "ordinal": 2, "kind": "SELECT", "queryId": "aa11...", "...": "同上" }
  ],
  "elapsedMs": 20
}
```

约定：
- `rows` 为二维**字符串数组**：所有值转为字符串传输（含 null → `null` 字面量在 JSON 中即 `null`），避免 JS 精度丢失；前端不做类型转换
- DML：`kind: "UPDATE"`，含 `affectedRows`，无 `queryId`/`columns`/`rows`
- DDL：`kind: "DDL"`，仅 `affectedRows: 0`
- 失败语句抛业务错误（见错误码 3xxx），后端**中止后续语句**；已成功语句不返回（v0.1 简化：整体失败，不返回部分结果）

### GET /queries/{queryId}/rows?page=1&pageSize=100

结果翻页。返回 data：`{ "queryId", "rows", "page", "pageSize", "totalKnown", "totalRows" }`。

## 5. 错误码表

| code | HTTP | 含义 | 前端文案建议 |
|---|---|---|---|
| 0 | 200 | 成功 | — |
| 1001 | 200 | 参数校验失败 | 显示 message（含字段名） |
| 1002 | 200 | 资源不存在（连接会话/queryId） | "结果已过期，请重新执行" |
| 1003 | 200 | X-Joyo-Token 校验失败 | "应用鉴权失败，请重启应用" |
| 1500 | 200 | 后端内部错误 | "发生内部错误，请查看日志" |
| 2001 | 200 | 无法连接到数据库主机 | "无法连接到主机，请检查地址与网络" |
| 2002 | 200 | 认证失败 | "用户名或密码错误" |
| 2003 | 200 | 连接超时 | "连接超时" |
| 2004 | 200 | 连接会话已失效 | "连接已断开，请重新连接" |
| 2101 | 200 | 元数据读取失败（含权限不足） | 显示 message |
| 3001 | 200 | SQL 语法/执行错误 | 显示 MySQL 原始错误 |
| 3002 | 200 | 查询超时 | "查询超时（xx 秒）" |
| 3003 | 200 | 查询被取消/中止 | "查询已中止" |

错误响应示例：
```json
{ "code": 3001, "message": "Unknown column 'nmae' in 'field list'", "data": null }
```

## 6. v0.2+ 预留端点（只登记不实现）

- `GET /history?keyword=&page=`、`DELETE /history/{id}`（spec-05 查询历史）
- 连接模型增加 `sshTunnel` 对象（spec-06）
- `POST /export`（spec-07 导出，流式响应）
- `POST /queries/{queryId}/cancel`（查询中断，v0.4）
- `POST /sync/compare`、`POST /sync/execute`（spec-12 表/数据同步，v0.5）
- `GET /logs?tail=200`（F13 日志查看，v0.2；读取文件日志尾部）
- `/health` 响应扩展（F14 资源监控，v0.2）：data 增加 `jvm: { heapUsedMb, heapMaxMb, cpuLoad }`，JVM 堆内存取 Runtime，进程 CPU 取 OperatingSystemMXBean.getProcessCpuLoad（增量采样）

# Joyo 本地数据模型设计

> 文档状态：已定稿 | 上游文档：`overview.md`

## 1. 存储位置

所有本地数据存放在 Electron 标准用户数据目录（`app.getPath('userData')`）：

- Windows：`%APPDATA%/joyo/`
- 约定路径常量统一定义在 `desktop/src/main/paths.ts`

```
%APPDATA%/joyo/
├── connections.json     # 连接配置（含加密后的密码密文）
├── settings.json        # 全局设置（v1.0 引入）
└── history.db           # 查询历史 SQLite（v0.2 引入）
```

## 2. connections.json 结构

```json
{
  "version": 1,
  "connections": [
    {
      "id": "b3c1a2d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
      "name": "本地开发库",
      "host": "127.0.0.1",
      "port": 3306,
      "username": "root",
      "passwordEnc": "enc:v1:QmFzZTY057yT562X...",
      "defaultDatabase": "",
      "sshTunnel": null,
      "createdAt": "2026-10-15T10:00:00.000Z",
      "updatedAt": "2026-10-15T10:00:00.000Z"
    }
  ]
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string (UUID v4) | 主键，创建时由 Main 进程生成 |
| name | string | 显示名称，用户可读，允许重复但会提示 |
| host / port / username | string / number / string | 连接目标，明文存储 |
| passwordEnc | string | 加密密码密文，格式见下；**任何 API 响应不得返回此字段** |
| defaultDatabase | string | 连接后默认选中的库，可为空 |
| sshTunnel | object \| null | v0.2 引入：`{ host, port, username, authType: "password" \| "privateKey", secretEnc, privateKeyPath }` |

### 密码加密格式

```
enc:v1:<base64(safeStorage.encryptString(plain))>
```

- 前缀 `enc:v1` 用于将来切换加密算法时做版本迁移
- 旧版无前缀的值视为明文（仅存在于极早期开发数据，迁移时重新加密）
- 加解密入口收敛在 `desktop/src/main/secure-store.ts`，其他模块不得直接调用 safeStorage

## 3. 配置文件的读写规则

1. 读写只发生在 Electron Main 进程（原子写：先写 `.tmp` 再 rename）
2. 文件损坏时：重命名为 `connections.json.broken.<时间戳>` 并新建空文件，UI 提示"配置已重置"
3. `version` 字段用于将来做迁移；当前固定为 `1`

## 4. 后端内存态（不落盘）

| 对象 | 生命周期 | 说明 |
|---|---|---|
| HikariDataSource | 按 connectionId 惰性创建，前端显式断开或后端关闭时销毁 | 每个连接一个独立池，池大小 max=5 |
| 查询结果缓存 | 内存 Map<queryId, rows>，上限 50MB / 10000 行，LRU 淘汰 | 服务分页读取，见 `rest-api.md` 查询接口 |

## 5. 敏感信息红线（写入约定文档的强制规则）

- 仓库内任何文件不得出现：真实主机名、真实密码、密钥、`enc:v1:` 开头的真实密文
- 文档与注释中的示例一律使用 `example.com`、`127.0.0.1`、`<your-password>` 占位
- 提交前按 `docs/90-conventions/git-workflow.md` 的隐私检查清单执行

# spec-06：SSH 隧道

- 规格编号：spec-06
- 功能名称：SSH 隧道（F06）
- 所属版本：v0.2
- 状态：草稿（待确认）
- 依赖 spec：spec-01（连接管理）

## 1. 功能描述

数据库在跳板机（堡垒机）后面时，用户可在连接配置中启用 SSH 隧道：先经 SSH 登录跳板机，再从跳板机转发到 MySQL（PRD US-07）。支持密码与私钥两种认证。

## 2. 界面设计

连接编辑对话框（spec-01）在"默认数据库"下方新增**"SSH 隧道"折叠区**（默认收起，标题右侧显示开关）：

| 字段 | 控件 | 必填（启用时） | 默认值 | 校验 |
|---|---|---|---|---|
| 启用 | 开关 | — | 关 | — |
| SSH 主机 | 文本框 | 是 | 空 | 非空 |
| SSH 端口 | 数字框 | 是 | 22 | 1-65535 |
| SSH 用户名 | 文本框 | 是 | 空 | 非空 |
| 认证方式 | 单选：密码 / 私钥 | 是 | 密码 | — |
| SSH 密码 | 密码框（可显隐） | 密码认证时是 | 空 | 无；编辑时留空=不修改 |
| 私钥文件 | 文件选择器 | 私钥认证时是 | 空 | 文件须存在 |
| 私钥口令 | 密码框 | 否 | 空 | 无；编辑时留空=不修改 |

- 开关关闭时折叠区其余字段禁用；`测试连接` 走完整链路（隧道 + 数据库）。
- 私钥选择器调 Electron `dialog.showOpenDialog`。

## 3. 接口依赖

1. **连接配置（Electron Main IPC）**：`connections:save` / `connections:list` 载荷增加 `sshTunnel` 对象（`data-model.md` 已预留字段落地）；`secure:decrypt` 入参扩展为 `{ id, field: 'password' | 'sshPassword' | 'sshPassphrase' }`（编辑回填解密）。

```ts
interface SshTunnelConfig {
  enabled: boolean
  host: string; port: number; username: string
  authType: 'password' | 'privateKey'
  password?: string          // 明文传入，Main 加密存储；list 视图脱敏为 hasSshPassword
  privateKeyPath?: string
  passphrase?: string        // 同上，脱敏为 hasSshPassphrase
}
```

2. **后端 REST**：`POST /connections/test` 与 `POST /connections/{id}/session` 请求体增加可选 `sshTunnel` 字段（password/passphrase 为解密后明文，仅回环传输）；隧道由**后端建立**（Java 侧 JSch fork `com.github.mwiede:jsch:0.2.x`）：

- 建会话流程：JSch 连跳板机（连接超时 5s）→ `Session.setPortForwardingL(随机本地端口, dbHost, dbPort)` → HikariDataSource 指向 `127.0.0.1:{localPort}`；断开会话时先关隧道再关池。
- 新增错误码 `2005`（SSH 隧道建立失败，message 区分：网络不可达 / 认证失败 / 私钥读取失败），已登记错误码表。

## 4. 行为细节

1. 隧道生命周期与连接会话严格绑定：建立会话失败时必须关闭已建立的 SSH Session；同一会话复用同一条隧道。
2. 本地转发端口由系统随机分配，绑定失败重试 3 次，仍失败报 2005。
3. 私钥支持 OpenSSH / PEM 格式（jsch 0.2.x 原生支持）；口令错误归入 2005"认证失败"。
4. 密码/口令经 safeStorage 加密，存储格式 `enc:v1:` 前缀（与连接密码同规则）；任何界面与日志不回显明文。
5. 未启用隧道的连接行为与 v0.1 完全一致。

## 5. 边界情况

| 场景 | 期望表现 |
|---|---|
| 跳板机不可达 | 2005，"SSH 隧道连接失败：无法连接跳板机" |
| SSH 密码/私钥错误 | 2005，"SSH 隧道连接失败：认证失败" |
| 私钥文件被移动/删除 | 2005，"SSH 隧道连接失败：私钥读取失败"，提示重新选择文件 |
| 私钥需要口令但未填 | 2005，"SSH 隧道连接失败：需要私钥口令" |
| 隧道通了但 MySQL 不可达 | 2001/2002（数据库侧错误，与隧道无关） |
| 编辑连接改用私钥认证 | 旧密码字段保留但不再使用 |
| 换机器后 safeStorage 解密失败 | 同 spec-01"密码失效"处理：标记失效，连接时提示重新输入 |

## 6. 验收标准

- [ ] 启用密码认证隧道，经跳板机连接 MySQL 成功，结果网格可正常查询
- [ ] 私钥（含口令）认证同样可连接；错误口令得到 2005 明确文案
- [ ] 关闭隧道开关后连接行为与直连一致
- [ ] connections.json 中 sshPassword / sshPassphrase 均为 `enc:v1:` 密文
- [ ] 编辑连接时 SSH 密码留空保存，原密码仍有效
- [ ] 断开会话后，跳板机上对应的 SSH 连接被关闭（netstat/服务端 who 可验证）
- [ ] 跳板机宕机时测试连接 2 秒级返回 2005，UI 有明确提示

## 7. 扩展点（只设计不实现）

- Known-hosts 校验与指纹确认对话框（安全增强，v1.0）
- 多跳代理（ProxyJump）、隧道保活参数配置

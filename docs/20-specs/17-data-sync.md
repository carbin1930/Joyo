- 规格编号：spec-17
- 功能名称：表/数据同步（F12）
- 所属版本：v0.5
- 状态：已定稿并实现（2026-10-27。实现说明：比对全在后端（`MetadataService.structure` 读两侧列与索引；数据比对用主键 + `MD5(CONCAT_WS(CHAR(31), COALESCE(...)))` 校验和，只回传主键与校验和，比对列取两侧交集）；execute 用与 compare 相同的入参在服务端重新比对后执行（不接受客户端 SQL）；结构 DDL 逐条执行（MySQL DDL 隐式提交），数据变更单事务参数化执行、失败仅回滚数据部分；踩坑：`TableColumn`/`TableIndex` 嵌套在 `TableDetailResponse` 内；Mockito 桩必须"先建桩再注册"，且链式桩要逐个完成后再开下一个）
- 依赖 spec：spec-01（连接与会话）、spec-02（表结构元数据）、spec-16（主键读取与单事务变更范式）

## 1. 功能描述

跨连接（源/目标可以是不同连接，也可以是同一连接的不同库）比对两张表的**结构**与**数据**，生成同步脚本，用户确认后在目标库**一个事务**内执行（PRD F12）。

解决的问题：开发/测试/生产库之间对齐一张表，过去要手写 ALTER 与 INSERT ... ON DUPLICATE KEY UPDATE，或依赖外部工具；Joyo 内建"比对 → 看差异 → 确认执行"，且所有写操作按主键精确定位、失败整体回滚。

## 2. 界面设计

**同步面板**（`SyncDialog.vue`，BaseDialog 720px）：

- 顶部源/目标两行：`连接`下拉（已建立会话的连接）+ `库`下拉 + `表`下拉；源默认带出当前连接与当前选中表，目标默认同连接同库同表（用户改）
- 中部左右分栏：
  - 左「结构差异」：列出列名、源类型 / 目标类型，`新增`（目标缺，绿）/ `缺失`（源缺，红）/ `变更`（黄）；下方索引差异（源有目标无、定义不同）
  - 右「数据差异」：三个计数卡 `新增 n / 修改 n / 删除 n`，超出比对上限时提示"仅比对前 N 行"
- 底部可折叠「同步脚本」（等宽字体逐条列出将执行的语句预览）
- 底部按钮：`重新比对` `取消` `执行同步`（危险色，仅在有差异时可用）

**入口**：表详情头部「导出数据」旁新增「同步」按钮（仅选中单表时可用）。

## 3. 接口依赖

1. **`POST /api/v1/sync/compare`**（rest-api.md 第 4 节）：

```
POST /api/v1/sync/compare
X-Joyo-Token: <token>
{ "source": { "connectionId": "c1", "database": "db1", "table": "users" },
  "target": { "connectionId": "c2", "database": "db2", "table": "users" },
  "rowLimit": 10000 }
```

响应 data：

```json
{
  "comparable": true,
  "reason": null,
  "columnDiffs": [ { "name": "email", "kind": "missing", "sourceType": "varchar(64)", "targetType": null } ],
  "indexDiffs":  [ { "name": "idx_email", "kind": "missing", "detail": "(email)" } ],
  "dataDiff": { "insert": 3, "update": 2, "delete": 1, "scanned": 10000, "truncated": false },
  "statements": [ "ALTER TABLE `db2`.`users` ADD COLUMN `email` varchar(64) NULL", "INSERT INTO ..." ]
}
```

- `comparable=false` 时 `reason` 说明原因（表不存在 / 无主键 / 连接失效），前端不显示执行按钮
- `statements` 仅供预览，实际执行的语句由后端在执行时重新生成并参数化

2. **`POST /api/v1/sync/execute`**：请求体与 compare **完全相同**（服务端重新比对后再执行，不接受客户端传 SQL，避免被篡改），响应 data：`{ "applied": 7 }`。

3. 错误码：5101 表不存在或不可比对、5102 同步执行失败（message 为 MySQL 原始错误）、5103 无主键无法比对数据。

## 4. 行为细节

1. **结构比对**（后端 `SyncService`，复用 `MetadataService.tableDetail` 两侧读取）：
   - 列按名比对：目标缺 → `missing`（生成 `ALTER TABLE ... ADD COLUMN`）；源缺 → `extra`（**只提示，不生成 DROP**）；类型/可空不同 → `changed`（生成 `ALTER TABLE ... MODIFY COLUMN`）
   - 索引按名比对：源有目标无 → 生成 `CREATE INDEX`；其余（定义不同、目标多出）只提示
   - 排序按源表列序（ORDINAL_POSITION）
2. **数据比对基于主键**：主键列用 `EditableResolver.primaryKey`（information_schema）。**无主键 → 不可比对数据**（`comparable=false`，reason 提示），结构差异仍展示。
3. **校验和比对**（避免全量传输）：
   - 两侧各执行一次 `SELECT <pk>, MD5(CONCAT_WS(0x1f, <交集列>)) FROM db.t LIMIT rowLimit`
   - NULL 用 `COALESCE(CAST(col AS CHAR), '#NULL#')` 归一，保证 NULL 与空串可区分
   - 只回传主键与校验和，在内存比对出 insert（源有目标无）/ update（校验和不同）/ delete（目标有源无）
   - 比对列取**两侧列的交集**，目标缺列时不影响数据比对
4. **执行**：重新比对 → 从源读取差异行的真实值（按主键批量 `IN`）→ 在目标连接上以**一个事务**执行：结构 ALTER（DDL 隐式提交，故放在数据变更前单独执行）、INSERT/UPDATE/DELETE 参数化执行 → 全部成功 commit，任一条失败 rollback（5102）
5. **行数上限**：`rowLimit` 默认 10000，范围 1-1000000；扫描行数达上限时 `truncated=true` 并在面板提示"仅比对前 N 行"
6. **DDL 与事务**：MySQL DDL 会隐式提交，因此结构脚本先执行且逐条独立；数据变更随后在单事务内执行，失败只回滚数据部分并在提示中说明"结构变更已提交"

## 5. 边界情况

| 场景 | 期望表现 |
|---|---|
| 目标表不存在 | `comparable=false`，reason"目标表不存在"；不生成数据脚本（建表不自动生成） |
| 源表不存在 | 同上，reason"源表不存在" |
| 表无主键 | 结构差异照常展示，数据不可比对（reason"目标表无主键，无法按主键比对数据"） |
| 连接会话失效 | 2004（沿用现有会话错误） |
| 两侧表完全不同（列名无交集） | 结构差异全部列出，数据比对交集为空 → 仅提示 |
| 目标多出的列 | 只展示提示，**不生成 DROP COLUMN** |
| 超大表（超过 rowLimit） | 提示"仅比对前 N 行"，结果标注 truncated |
| 执行时目标行已被他人改动 | UPDATE/DELETE 影响 0 行视为失败，整体回滚 + 5102 |
| 执行时违反约束（唯一键冲突） | 整体回滚，toast 展示 MySQL 原始错误 |
| 同一连接同库同表 | 差异为空，`执行同步`禁用 |

## 6. 验收标准

- [ ] 同连接两个库同构表：数据差异（新增/修改/删除）计数与实际一致
- [ ] 目标缺列：结构差异显示 `missing`，脚本含 `ALTER TABLE ... ADD COLUMN`，执行后目标表结构与源一致
- [ ] 目标多列：只提示，不生成 DROP
- [ ] 无主键表：提示不可比对数据，结构差异仍可见
- [ ] 跨连接（两个已连接实例）比对成功
- [ ] 执行一次含增改删的同步：目标库数据与源一致；构造一条会失败的操作 → 数据部分整体回滚
- [ ] 超过 rowLimit 的大表比对：提示"仅比对前 N 行"，UI 不卡

## 7. 扩展点（只设计不实现）

- 按列选择性同步、忽略指定列/表、定时同步、双向同步与冲突策略、结构同步生成完整 `CREATE TABLE`（当前不自动生成建表）、同步结果导出为脚本文件、大表分批同步与断点续传

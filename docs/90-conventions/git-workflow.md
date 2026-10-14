# Git 工作流与提交规范

> 文档状态：已定稿

## 1. 分支模型

```
main            ← 永远可构建、可运行；只在 spec 验收通过后合入
feat/spec-NN-*  ← 每个 spec 一个功能分支，如 feat/spec-01-connection-manager
fix/*           ← 缺陷修复
```

- 单人项目采用 trunk-light 模式：feature 分支生命周期 ≤ 一周，完成后尽快合入 main 并删除
- tag 规则：版本验收通过打 `v0.1.0` 格式的 tag（见 `roadmap.md`）

## 2. Commit Message

格式：`<type>(<scope>): <中文描述> [spec-NN]`

| type | 用途 |
|---|---|
| feat | 新功能 |
| fix | 缺陷修复 |
| docs | 仅文档 |
| refactor | 重构（不改行为） |
| test | 测试 |
| chore | 构建/工具链 |

示例：
```
feat(backend): 实现连接测试接口 [spec-01]
fix(frontend): 修复 NULL 单元格复制为字符串 "null" 的问题 [spec-04]
docs: 新增 spec-02 库表浏览规格
```

- scope 用模块名：backend / frontend / desktop / docs / scripts
- 每个功能分支合并到 main 时用 merge commit（`--no-ff`），message 注明 spec 编号

## 3. 提交前检查清单

1. `pnpm typecheck` 与后端 `mvn compile` 零错误
2. 相关单元测试通过
3. **隐私检查（开源项目强制）**：
   - [ ] 无真实主机名、IP（`127.0.0.1`、`example.com` 除外）、密码、密钥、`enc:v1:` 真实密文
   - [ ] 无本机绝对路径中的用户名（如 `d:/dev/myproject` 之类私有目录信息出现在文档/注释/配置中）
   - [ ] 无家人、朋友等个人信息
4. `docs/private/` 与 `.env*` 未被 `git add`（已在 .gitignore，勿强制添加）

## 4. 与开发流程的衔接

- 每完成一个 spec：合入 main → 更新 `progress.md`（已完成项 + 关键决策 + 新踩的坑）→ 单独提交 `docs: 更新 progress（spec-NN 完成）`
- 开发中发现规格问题：先改 spec 文档并提交（`docs(spec-NN): 调整 xx`），再改代码——**文档先行**
- 大模型辅助开发的 commit 规范与人类提交完全一致，不标注"AI 生成"（内容经人工审查后视为作者提交）

## 5. 远程与备份

- origin 为 GitHub 开源仓库；`docs/private/` 不入远程库
- 换机器开发：clone 后按根 `package.json` 与各子目录 README 安装依赖即可恢复完整开发环境（AI 开发上下文全部在仓库内）

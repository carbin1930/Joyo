- 规格编号：spec-22
- 功能名称：打包与分发
- 所属版本：v1.0（正式发布门槛）
- 状态：已定稿并实现（2026-10-27。实现说明：`scripts/package.ps1` / `package.sh` 串起四步（vite → tsc → `mvn package` → jlink → electron-builder nsis），产物 `release/Joyo-Setup-<version>.exe`；渲染产物由脚本复制到 `desktop/dist-electron/renderer`，主窗打包后从 `__dirname/../renderer/index.html` 加载；后端 jar 与裁剪 JRE 以 `extraResources` 落到 `resources/bin/joyo-backend.jar` 与 `resources/backend-runtime`，与 overview.md 第 2 节 spawn 路径一致；`publish` 指向 GitHub Releases，供 spec-19 自动更新拉取）
- 依赖 spec：spec-19（自动更新需要安装包与发布地址）

## 1. 功能描述

把三端产物组装成可安装的 Windows 安装包（`docs/10-architecture/overview.md` 第 5 节），使应用可以脱离开发环境分发，并为自动更新提供发布源。

## 2. 打包流程

| 步骤 | 命令 | 产物 |
|---|---|---|
| 1 | `pnpm build:frontend` | `frontend/dist` |
| 2 | `pnpm build:desktop` | `desktop/dist-electron` |
| 3 | `mvn -DskipTests package` | `backend/target/joyo-backend.jar` |
| 4 | `jdeps` + `jlink` | `backend-runtime/`（实测约 35MB） |
| 5 | `electron-builder --win --x64` | `release/Joyo-Setup-<version>.exe` |

一键执行：`pnpm package`（Windows 走 `scripts/package.ps1`，Linux/Mac 走 `package.sh`）。

## 3. 关键决策

1. **渲染产物复制到 `dist-electron/renderer`**：electron-builder 只打包 desktop 目录，而主窗开发期读的是仓库根 `frontend/dist`；打包后改为读 `__dirname/../renderer/index.html`
2. **JRE 裁剪可选**：`jdeps` 对 Spring Boot fat jar 可能分析失败，失败则跳过裁剪，sidecar 回退到系统 `java`（不影响功能，只影响安装包体积）
3. **NSIS 非一键安装**：允许用户选择安装目录
4. **不签名**：未配置代码签名证书，安装包会被 SmartScreen 提示，属预期

## 4. 边界情况

| 场景 | 期望表现 |
|---|---|
| 后端 jar 不存在 | 脚本在步骤 3 后即报错中断 |
| jlink 不可用 | 跳过裁剪，安装包不带内置 JRE |
| makensis 报"无法创建 __uninstaller.exe" | 多为杀软/索引器占用；清空 `release/` 重跑即可（已实测复现与修复） |
| 未配置签名证书 | 跳过签名，安装时被 SmartScreen 提示 |

## 5. 验收标准

- [ ] `pnpm package` 跑通，产出 `release/Joyo-Setup-<version>.exe`
- [ ] 安装包安装后可从开始菜单启动，无需本机安装 Java/JDK
- [ ] 安装目录含 `resources/bin/joyo-backend.jar` 与 `resources/backend-runtime`
- [ ] 应用启动出现启动画面 → 主窗 → 可连接数据库执行查询
- [ ] 卸载后 userData（连接配置/历史/设置）保留策略符合预期（`deleteAppDataOnUninstall=false`）

## 6. 扩展点（只设计不实现）

- macOS dmg / Linux AppImage 目标、代码签名、CI 自动发版、增量更新（差分）、安装包体积进一步优化（模块化 jar + 更小 JRE）

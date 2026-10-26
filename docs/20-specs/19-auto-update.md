- 规格编号：spec-19
- 功能名称：自动更新（F11）
- 所属版本：v1.0
- 状态：已定稿并实现（2026-10-27。实现说明：接入 electron-updater，Main 侧封装 `updater.ts`（检查/下载进度/安装），IPC `updater:check` / `updater:install` 与事件推送 `updater:event`；渲染层 `UpdateDialog.vue` 展示版本说明与下载进度，入口在设置面板内"检查更新"。**发布地址（publish）为占位配置，真实发布流程需打包与发布服务器，本机无法验证**——spec 明确该边界）
- 依赖 spec：spec-18（设置面板作为入口）

## 1. 功能描述

应用内检查并安装新版本（PRD F11）：用户点"检查更新"后查询是否有新版本，有则展示版本号与更新说明，下载过程显示进度，完成后可"立即安装并重启"。

## 2. 界面设计

入口：设置面板底部「检查更新」按钮 + 启动时静默检查（有更新时状态栏齿轮旁显示小红点）。

更新弹窗（`UpdateDialog.vue`，BaseDialog 420px）：

- 检查中：loading + "正在检查更新…"
- 无更新："当前已是最新版本（vX.Y.Z）"
- 有更新：版本号 + 更新说明（releaseNotes，纯文本展示）+ `立即更新`
- 下载中：进度条 + 百分比（禁用按钮）
- 下载完成：`立即安装并重启`（危险色）
- 失败：错误文案 + `重试`

## 3. 接口依赖

不经过后端，走 Electron IPC：

```ts
window.joyo.checkUpdate(): Promise<UpdateState>
window.joyo.installUpdate(): Promise<void>
window.joyo.onUpdateEvent(cb: (state: UpdateState) => void): () => void
```

`UpdateState`：`{ phase: 'idle' | 'checking' | 'available' | 'not-available' | 'downloading' | 'downloaded' | 'error'; version?: string; notes?: string; percent?: number; message?: string }`

## 4. 行为细节

1. 检查与下载由 Main 进程执行（electron-updater），渲染层只接收状态事件
2. 下载进度按 `download-progress` 事件节流推送（每 5% 一帧，避免高频 IPC）
3. 安装调用 `autoUpdater.quitAndInstall()`，由 Main 决定重启时机
4. **开发环境（`app.isPackaged === false`）不触发真实检查**，直接返回"开发环境不支持更新"，避免开发期噪音与报错
5. publish 配置占位（`build.publish` 指向 GitHub Releases 占位地址），真实发版前需替换为实际仓库

## 5. 边界情况

| 场景 | 期望表现 |
|---|---|
| 开发环境点击检查更新 | 提示"开发环境不支持更新"，不报错 |
| 无网络 / 发布地址不可达 | 状态 error，展示错误文案，可重试 |
| 已是最新 | 显示当前版本号 |
| 下载中途断网 | 进度停止并转 error，可重试 |
| 用户取消安装 | 保留已下载版本，下次可直接安装 |
| 打包配置未填 publish | 构建期提示（占位配置），不影响开发 |

## 6. 验收标准

- [ ] 设置面板"检查更新"可点击，开发环境下给出明确提示（不报错）
- [ ] IPC 通道与类型镜像齐全（`ipc.d.ts` 与 preload 一致），typecheck 通过
- [ ] 事件推送可订阅与退订（组件卸载不泄漏监听）
- [ ] 打包配置含 electron-updater 依赖与 publish 占位（构建不失败）
- [ ] 三端构建/测试全绿

## 7. 扩展点（只设计不实现）

- 启动时自动静默下载、更新渠道（beta/stable）切换、跳过此版本、强制更新策略、更新说明 Markdown 渲染、增量更新（差分下载）

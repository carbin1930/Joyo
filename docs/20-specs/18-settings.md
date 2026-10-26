- 规格编号：spec-18
- 功能名称：设置面板（F10）
- 所属版本：v1.0
- 状态：已定稿并实现（2026-10-27。实现说明：设置持久化在 Electron Main（userData/settings.json，复用 atomic-write），IPC `settings:get` / `settings:set`；主题用 `<html data-theme>` 切换 CSS 变量（只覆盖 `--joyo-*`，不新增组件样式分支），字号写入 `--joyo-font-size`（作用于 body 与结果网格）；默认行数上限同步给查询 store，超时在保存后随查询请求生效；越界值在 Main 侧 clamp，渲染层拿到的永远合法）
- 依赖 spec：spec-01（连接持久化范式）、spec-03（maxRows / 超时参数）

## 1. 功能描述

提供全局设置面板（PRD F10），让用户按自己的习惯调整外观与查询行为：主题（浅色/深色）、界面字号、查询默认行数上限、查询超时。设置持久化到本地，重启后保留，改动即时生效。

## 2. 界面设计

入口：全局状态栏右侧齿轮图标（所有页面可见）。

设置面板（`SettingsDialog.vue`，BaseDialog 480px）分两组：

| 分组 | 项目 | 控件 |
|---|---|---|
| 外观 | 主题（浅色/深色） | 单选 |
| 外观 | 字号（12-18px） | 滑块（带输入框） |
| 查询 | 默认行数上限（100 / 1000 / 10000） | 下拉 |
| 查询 | 查询超时（5-600 秒） | 数字输入 |

底部 `取消` / `保存`（无改动时禁用）。主题与字号在面板内改动即预览，不必先保存。

## 3. 接口依赖

不经过后端，全部走 Electron IPC（与连接配置同层，渲染层无 fs 权限）：

```ts
window.joyo.getSettings(): Promise<AppSettings>
window.joyo.setSettings(patch: Partial<AppSettings>): Promise<AppSettings>
```

`AppSettings`：`{ theme: 'light' | 'dark'; fontSize: number; maxRows: number; queryTimeoutSec: number }`

## 4. 行为细节

1. 启动即加载设置并应用（App.vue `onMounted`）；行数上限写回查询 store，后续查询立即采用
2. 主题通过 `<html data-theme="dark">` 覆盖 `--joyo-*` 变量实现，Element Plus 变量同步映射（已在 main.css 建立映射）
3. 字号写入 `--joyo-font-size`，作用于 `body` 与 `.tabulator`（网格）
4. 保存为**合并更新**（只传改动项），Main 侧逐字段归一：字号 12-18、行数 100-10000、超时 5-600，非法值回退默认
5. 文件缺失或损坏 → 返回默认设置（记 error 日志），不阻塞启动
6. 无 Electron 环境（纯浏览器开发、单测）→ 使用内置默认设置，不报错

## 5. 边界情况

| 场景 | 期望表现 |
|---|---|
| 首次启动无 settings.json | 使用默认设置（浅色 / 13px / 1000 / 30s） |
| settings.json 被写坏 | 回退默认并记日志，界面照常启动 |
| 手工改成越界值（如字号 99） | Main 侧夹到 18，返回合法值 |
| 保存时无 Electron | 前端降级：本地合并后应用，不抛错 |
| 深色主题下的网格与弹窗 | 颜色随变量变化，不出现不可读文本（变量覆盖保证） |

## 6. 验收标准

- [ ] 状态栏齿轮可打开设置面板；浅/深色切换即时生效
- [ ] 字号调整后网格与界面文字同步变化（12-18px 范围内）
- [ ] 改默认行数上限为 100 后执行查询，结果最多 100 行
- [ ] 改查询超时后生效（可用长查询或超时场景验证）
- [ ] 重启应用后设置保留
- [ ] 手动写坏 settings.json 后启动，应用正常且不崩

## 7. 扩展点（只设计不实现）

- 更多设置项（字体族、网格行高、NULL 显示文案、代理设置、SSH 默认私钥路径）、设置导入/导出、按连接覆盖全局设置、快捷键自定义

import { app, BrowserWindow } from 'electron'
import path from 'node:path'

/**
 * 启动画面（ADR-001：JVM 冷启动慢 → 启动画面 + 健康检查轮询）。
 * 后端 JVM 启动期间（通常 2-5 秒）用户没有任何反馈，用一个小窗口显示"正在启动后端"，
 * 主窗就绪后关闭。用内联 HTML，避免额外资源文件与打包路径问题。
 */

let splash: BrowserWindow | null = null

const SPLASH_HTML = `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <style>
    html, body { margin: 0; height: 100%; }
    body {
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      gap: 14px; background: #f7f8fa; color: #24292f;
      font-family: 'Segoe UI', 'Microsoft YaHei', system-ui, sans-serif; user-select: none;
    }
    .logo { font-size: 26px; font-weight: 600; letter-spacing: 2px; color: #2f6fed; }
    .hint { font-size: 13px; color: #7a828c; }
    .bar { width: 180px; height: 3px; border-radius: 2px; background: #dfe3e8; overflow: hidden; }
    .bar > i { display: block; width: 40%; height: 100%; background: #2f6fed; animation: slide 1.1s ease-in-out infinite; }
    @keyframes slide { 0% { margin-left: -40%; } 100% { margin-left: 100%; } }
  </style>
</head>
<body>
  <div class="logo">Joyo</div>
  <div class="hint">正在启动后端服务…</div>
  <div class="bar"><i></i></div>
</body>
</html>`

export function showSplash(): void {
  if (splash) return
  splash = new BrowserWindow({
    width: 360,
    height: 200,
    frame: false,
    resizable: false,
    movable: true,
    center: true,
    show: false,
    backgroundColor: '#f7f8fa',
    // 与主窗一致：避免使用 Electron 默认图标触发 libpng sRGB 警告
    icon: app.isPackaged ? undefined : path.join(__dirname, '../../build/icon.ico'),
    webPreferences: { offscreen: false },
  })
  splash.once('ready-to-show', () => splash?.show())
  void splash.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(SPLASH_HTML)}`)
}

export function closeSplash(): void {
  if (!splash) return
  if (!splash.isDestroyed()) splash.close()
  splash = null
}

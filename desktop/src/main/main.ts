import { app, BrowserWindow, dialog, ipcMain } from 'electron'
import path from 'node:path'
import { resolveBackend, type ResolvedBackend } from './sidecar'

/**
 * Electron 主进程入口（docs/10-architecture/overview.md 第 2 节）：
 * 1. 启动时解析后端（开发直连 或 spawn Sidecar）
 * 2. 就绪后创建窗口，preload 向渲染进程注入 { baseUrl, token }
 * 3. 退出时清理后端进程树
 */

let win: BrowserWindow | null = null
let backend: ResolvedBackend | null = null

function createWindow(): void {
  win = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 960,
    minHeight: 600,
    title: 'Joyo',
    show: false,
    webPreferences: {
      preload: path.join(__dirname, '../preload/preload.js'),
      // 安全模型（overview.md 第 4 节）
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  win.once('ready-to-show', () => win?.show())
  // 防止页面 title 覆盖
  win.on('page-title-updated', (e) => e.preventDefault())

  const devServerUrl = process.env.VITE_DEV_SERVER_URL
  if (devServerUrl) {
    void win.loadURL(devServerUrl)
  } else {
    void win.loadFile(path.join(__dirname, '../../../frontend/dist/index.html'))
  }

  win.on('closed', () => {
    win = null
  })
}

app.whenReady().then(async () => {
  try {
    backend = await resolveBackend(app.isPackaged)
    console.log(`[main] 后端就绪: ${backend.baseUrl}`)

    // 渲染进程经 preload 获取启动配置
    ipcMain.handle('joyo:get-launch-config', () => {
      if (!backend) throw new Error('后端尚未就绪')
      return { baseUrl: backend.baseUrl, token: backend.token }
    })

    createWindow()
  } catch (e) {
    dialog.showErrorBox('Joyo 启动失败', String(e))
    app.quit()
  }
})

app.on('before-quit', () => {
  // 兜底清理：确保不留孤儿 Java 进程（overview.md 第 2 节）
  void backend?.stop()
})

app.on('window-all-closed', () => {
  app.quit()
})

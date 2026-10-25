import { app, BrowserWindow, clipboard, dialog, ipcMain, Menu, shell } from 'electron'
import path from 'node:path'
import { registerConnectionsIpc } from './connections-ipc'
import { HistoryStore, historyFilePath } from './history-store'
import { registerHistoryIpc } from './history-ipc'
import { SafeStorageCipher } from './safe-storage-cipher'
import { ConnectionsStore, connectionsFilePath } from './secure-store'
import { resolveBackend, type ResolvedBackend } from './sidecar'

/**
 * Electron 主进程入口（docs/10-architecture/overview.md 第 2 节）：
 * 1. 启动时解析后端（开发直连 或 spawn Sidecar）
 * 2. 就绪后创建窗口，preload 向渲染进程注入 { baseUrl, token }
 * 3. 退出时清理后端进程树
 */

// 单实例锁：重复启动时聚焦已有窗口，避免双实例抢缓存/后端会话
const gotTheLock = app.requestSingleInstanceLock()
if (!gotTheLock) {
  app.quit()
}

let win: BrowserWindow | null = null
let backend: ResolvedBackend | null = null

/** 后端文件日志路径：打包模式在数据目录，开发模式在 backend/logs */
function backendLogPath(): string {
  return app.isPackaged
    ? path.join(app.getPath('userData'), 'logs', 'backend.log')
    : path.join(__dirname, '../../../backend/logs/backend.log')
}

/** 中文应用菜单（编辑角色保证快捷键在输入框/编辑器内生效） */
function buildMenu(): void {
  const template: Electron.MenuItemConstructorOptions[] = [
    {
      label: '文件',
      submenu: [{ label: '退出', role: 'quit' }],
    },
    {
      label: '编辑',
      submenu: [
        { label: '撤销', role: 'undo' },
        { label: '重做', role: 'redo' },
        { type: 'separator' },
        { label: '剪切', role: 'cut' },
        { label: '复制', role: 'copy' },
        { label: '粘贴', role: 'paste' },
        { label: '全选', role: 'selectAll' },
      ],
    },
    {
      label: '查看',
      submenu: [
        { label: '重新加载', role: 'reload' },
        { label: '强制重新加载', role: 'forceReload' },
        { type: 'separator' },
        { label: '放大', role: 'zoomIn' },
        { label: '缩小', role: 'zoomOut' },
        { label: '重置缩放', role: 'resetZoom' },
        { type: 'separator' },
        { label: '开发者工具', role: 'toggleDevTools' },
      ],
    },
    {
      // spec-13 第 2 节：工具 → 应用日志 / 打开日志目录
      label: '工具',
      submenu: [
        { label: '应用日志', click: () => win?.webContents.send('joyo:open-logs') },
        { label: '打开日志目录', click: () => shell.showItemInFolder(backendLogPath()) },
      ],
    },
    {
      label: '帮助',
      submenu: [{ label: '关于 Joyo', role: 'about' }],
    },
  ]
  Menu.setApplicationMenu(Menu.buildFromTemplate(template))
}

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

app.on('second-instance', () => {
  if (win) {
    if (win.isMinimized()) win.restore()
    win.focus()
  }
})

app.whenReady().then(async () => {
  try {
    buildMenu()
    backend = await resolveBackend(app.isPackaged, backendLogPath())
    console.log(`[main] 后端就绪: ${backend.baseUrl}`)

    // 渲染进程经 preload 获取启动配置
    ipcMain.handle('joyo:get-launch-config', () => {
      if (!backend) throw new Error('后端尚未就绪')
      return { baseUrl: backend.baseUrl, token: backend.token }
    })

    // 连接配置持久化（spec-01）：userData/connections.json + safeStorage 加解密
    const connectionsStore = new ConnectionsStore(
      connectionsFilePath(app.getPath('userData')),
      new SafeStorageCipher(),
    )
    registerConnectionsIpc(connectionsStore)

    // 查询历史持久化（spec-05）：userData/history.json，不经后端
    registerHistoryIpc(new HistoryStore(historyFilePath(app.getPath('userData'))))

    // spec-13：渲染层"打开目录"与"复制全部"（渲染进程无 shell/clipboard 权限）
    ipcMain.handle('shell:show-item', (_event, target: string) => {
      shell.showItemInFolder(target)
    })
    ipcMain.handle('clipboard:write-text', (_event, text: string) => {
      clipboard.writeText(text)
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

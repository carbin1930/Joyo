"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const electron_1 = require("electron");
const node_path_1 = __importDefault(require("node:path"));
const connections_ipc_1 = require("./connections-ipc");
const safe_storage_cipher_1 = require("./safe-storage-cipher");
const secure_store_1 = require("./secure-store");
const sidecar_1 = require("./sidecar");
/**
 * Electron 主进程入口（docs/10-architecture/overview.md 第 2 节）：
 * 1. 启动时解析后端（开发直连 或 spawn Sidecar）
 * 2. 就绪后创建窗口，preload 向渲染进程注入 { baseUrl, token }
 * 3. 退出时清理后端进程树
 */
let win = null;
let backend = null;
function createWindow() {
    win = new electron_1.BrowserWindow({
        width: 1280,
        height: 800,
        minWidth: 960,
        minHeight: 600,
        title: 'Joyo',
        show: false,
        webPreferences: {
            preload: node_path_1.default.join(__dirname, '../preload/preload.js'),
            // 安全模型（overview.md 第 4 节）
            contextIsolation: true,
            nodeIntegration: false,
            sandbox: true,
        },
    });
    win.once('ready-to-show', () => win?.show());
    // 防止页面 title 覆盖
    win.on('page-title-updated', (e) => e.preventDefault());
    const devServerUrl = process.env.VITE_DEV_SERVER_URL;
    if (devServerUrl) {
        void win.loadURL(devServerUrl);
    }
    else {
        void win.loadFile(node_path_1.default.join(__dirname, '../../../frontend/dist/index.html'));
    }
    win.on('closed', () => {
        win = null;
    });
}
electron_1.app.whenReady().then(async () => {
    try {
        backend = await (0, sidecar_1.resolveBackend)(electron_1.app.isPackaged);
        console.log(`[main] 后端就绪: ${backend.baseUrl}`);
        // 渲染进程经 preload 获取启动配置
        electron_1.ipcMain.handle('joyo:get-launch-config', () => {
            if (!backend)
                throw new Error('后端尚未就绪');
            return { baseUrl: backend.baseUrl, token: backend.token };
        });
        // 连接配置持久化（spec-01）：userData/connections.json + safeStorage 加解密
        const connectionsStore = new secure_store_1.ConnectionsStore((0, secure_store_1.connectionsFilePath)(electron_1.app.getPath('userData')), new safe_storage_cipher_1.SafeStorageCipher());
        (0, connections_ipc_1.registerConnectionsIpc)(connectionsStore);
        createWindow();
    }
    catch (e) {
        electron_1.dialog.showErrorBox('Joyo 启动失败', String(e));
        electron_1.app.quit();
    }
});
electron_1.app.on('before-quit', () => {
    // 兜底清理：确保不留孤儿 Java 进程（overview.md 第 2 节）
    void backend?.stop();
});
electron_1.app.on('window-all-closed', () => {
    electron_1.app.quit();
});
//# sourceMappingURL=main.js.map
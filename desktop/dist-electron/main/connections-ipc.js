"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerConnectionsIpc = registerConnectionsIpc;
const electron_1 = require("electron");
/**
 * 连接管理 IPC 通道注册（通道白名单见 docs/30-api/rest-api.md 连接管理节）。
 * 载荷契约：通道名 `域:动作`；输入/输出类型与 frontend/src/types/ipc.d.ts 保持一致。
 */
function registerConnectionsIpc(store) {
    electron_1.ipcMain.handle('connections:list', () => {
        return store.list();
    });
    electron_1.ipcMain.handle('connections:save', (_event, input) => {
        return store.save(input);
    });
    electron_1.ipcMain.handle('connections:delete', (_event, id) => {
        return store.remove(id);
    });
    electron_1.ipcMain.handle('secure:decrypt', (_event, id) => {
        return store.decryptPassword(id);
    });
}
//# sourceMappingURL=connections-ipc.js.map
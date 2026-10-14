"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const electron_1 = require("electron");
/**
 * Preload：仅通过 contextBridge 暴露白名单 API（overview.md 第 4 节安全模型）。
 * 渲染进程类型定义见 frontend/src/types/ipc.d.ts，两侧签名必须一致。
 */
let cached = null;
function getLaunchConfig() {
    cached ??= electron_1.ipcRenderer.invoke('joyo:get-launch-config');
    return cached;
}
electron_1.contextBridge.exposeInMainWorld('joyo', {
    getLaunchConfig,
});
//# sourceMappingURL=preload.js.map
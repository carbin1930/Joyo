"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.ConnectionsStore = void 0;
exports.newConnectionsFile = newConnectionsFile;
exports.connectionsFilePath = connectionsFilePath;
const node_fs_1 = __importDefault(require("node:fs"));
const node_path_1 = __importDefault(require("node:path"));
const node_crypto_1 = require("node:crypto");
function newConnectionsFile() {
    return { version: 1, connections: [] };
}
function toListItem(c) {
    const { passwordEnc, ...rest } = c;
    return { ...rest, hasPassword: passwordEnc !== '' };
}
class ConnectionsStore {
    filePath;
    cipher;
    constructor(filePath, cipher) {
        this.filePath = filePath;
        this.cipher = cipher;
    }
    load() {
        if (!node_fs_1.default.existsSync(this.filePath))
            return newConnectionsFile();
        try {
            const raw = JSON.parse(node_fs_1.default.readFileSync(this.filePath, 'utf-8'));
            if (raw.version !== 1 || !Array.isArray(raw.connections)) {
                throw new Error('version 或 connections 字段不符');
            }
            return raw;
        }
        catch (e) {
            // data-model.md 第 3 节：损坏文件重命名保留现场，并新建空文件
            const brokenPath = `${this.filePath}.broken.${Date.now()}`;
            node_fs_1.default.renameSync(this.filePath, brokenPath);
            console.warn(`[secure-store] 配置文件损坏，已重命名为 ${brokenPath}: ${String(e)}`);
            const fresh = newConnectionsFile();
            this.persist(fresh);
            return fresh;
        }
    }
    /** IPC: connections:list */
    list() {
        return this.load().connections.map(toListItem);
    }
    /** IPC: connections:save（新建或更新）；返回脱敏视图 */
    save(input) {
        const file = this.load();
        const now = new Date().toISOString();
        if (input.id) {
            const existing = file.connections.find((c) => c.id === input.id);
            if (!existing) {
                throw new Error(`连接不存在: ${input.id}`);
            }
            existing.name = input.name;
            existing.host = input.host;
            existing.port = input.port;
            existing.username = input.username;
            existing.defaultDatabase = input.defaultDatabase ?? existing.defaultDatabase;
            if (input.password !== undefined) {
                existing.passwordEnc = this.cipher.encrypt(input.password);
            }
            existing.updatedAt = now;
            this.persist(file);
            return toListItem(existing);
        }
        const created = {
            id: (0, node_crypto_1.randomUUID)(),
            name: input.name,
            host: input.host,
            port: input.port,
            username: input.username,
            passwordEnc: this.cipher.encrypt(input.password ?? ''),
            defaultDatabase: input.defaultDatabase ?? '',
            sshTunnel: null,
            createdAt: now,
            updatedAt: now,
        };
        file.connections.push(created);
        this.persist(file);
        return toListItem(created);
    }
    /** IPC: connections:delete；返回是否删除了条目 */
    remove(id) {
        const file = this.load();
        const before = file.connections.length;
        file.connections = file.connections.filter((c) => c.id !== id);
        if (file.connections.length === before)
            return false;
        this.persist(file);
        return true;
    }
    /** IPC: secure:decrypt —— 仅用于编辑表单回填；解密失败返回 null（spec-01 边界情况） */
    decryptPassword(id) {
        const conn = this.load().connections.find((c) => c.id === id);
        if (!conn)
            return null;
        if (conn.passwordEnc === '')
            return '';
        return this.cipher.decrypt(conn.passwordEnc);
    }
    persist(file) {
        const tmpPath = `${this.filePath}.tmp`;
        node_fs_1.default.writeFileSync(tmpPath, JSON.stringify(file, null, 2), 'utf-8');
        node_fs_1.default.renameSync(tmpPath, this.filePath);
    }
}
exports.ConnectionsStore = ConnectionsStore;
/** 便捷构造：userData 目录下的标准配置文件路径 */
function connectionsFilePath(userDataDir) {
    return node_path_1.default.join(userDataDir, 'connections.json');
}
//# sourceMappingURL=secure-store.js.map
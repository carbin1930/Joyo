"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SafeStorageCipher = void 0;
const electron_1 = require("electron");
const ENC_PREFIX = 'enc:v1:';
/**
 * 基于 Electron safeStorage（系统钥匙串机制）的密码加解密实现。
 * 密文格式 enc:v1:<base64>（docs/10-architecture/data-model.md 第 2 节）。
 * 本模块是全项目唯一允许调用 safeStorage 的位置（coding-frontend.md 第 5 节）。
 */
class SafeStorageCipher {
    encrypt(plain) {
        if (!electron_1.safeStorage.isEncryptionAvailable()) {
            throw new Error('系统加密机制不可用，无法安全保存密码');
        }
        const encrypted = electron_1.safeStorage.encryptString(plain);
        return `${ENC_PREFIX}${encrypted.toString('base64')}`;
    }
    decrypt(cipherText) {
        if (!cipherText.startsWith(ENC_PREFIX))
            return null;
        try {
            const raw = Buffer.from(cipherText.slice(ENC_PREFIX.length), 'base64');
            return electron_1.safeStorage.decryptString(raw);
        }
        catch (e) {
            // 换机器 / 钥匙串不匹配（spec-01 边界情况）
            console.warn('[cipher] 密码解密失败:', String(e));
            return null;
        }
    }
}
exports.SafeStorageCipher = SafeStorageCipher;
//# sourceMappingURL=safe-storage-cipher.js.map
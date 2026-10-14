"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.getFreePort = getFreePort;
exports.waitForHealthy = waitForHealthy;
exports.startSidecar = startSidecar;
exports.resolveBackend = resolveBackend;
const node_child_process_1 = require("node:child_process");
const node_net_1 = __importDefault(require("node:net"));
const node_crypto_1 = require("node:crypto");
const node_fs_1 = __importDefault(require("node:fs"));
const node_path_1 = __importDefault(require("node:path"));
const HEALTH_TIMEOUT_MS = 30_000;
const HEALTH_INTERVAL_MS = 500;
/** 扫描一个空闲 TCP 端口（存在竞态，但配合健康检查重试足够可靠） */
function getFreePort() {
    return new Promise((resolve, reject) => {
        const server = node_net_1.default.createServer();
        server.listen(0, '127.0.0.1', () => {
            const address = server.address();
            if (address && typeof address === 'object') {
                const port = address.port;
                server.close(() => resolve(port));
            }
            else {
                server.close();
                reject(new Error('获取空闲端口失败'));
            }
        });
        server.on('error', reject);
    });
}
function findJava(explicitRuntimeDir) {
    // 优先使用应用内置 JRE（打包时由 jlink 生成，见 overview.md 第 5 节）
    const candidates = [];
    if (explicitRuntimeDir) {
        const exe = process.platform === 'win32' ? 'java.exe' : 'java';
        candidates.push(node_path_1.default.join(explicitRuntimeDir, 'bin', exe));
    }
    candidates.push('java');
    for (const candidate of candidates) {
        try {
            node_fs_1.default.accessSync(candidate, node_fs_1.default.constants.X_OK);
            return candidate;
        }
        catch {
            // 继续尝试下一个候选
        }
    }
    return 'java';
}
/** 单次健康检查，2 秒超时 */
async function probeHealth(baseUrl, token) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 2_000);
    try {
        const resp = await fetch(`${baseUrl}/api/v1/health`, {
            headers: { 'X-Joyo-Token': token },
            signal: controller.signal,
        });
        if (!resp.ok)
            return false;
        const payload = (await resp.json());
        return payload.code === 0;
    }
    catch {
        return false;
    }
    finally {
        clearTimeout(timer);
    }
}
/** 轮询健康检查直到就绪或超时 */
async function waitForHealthy(baseUrl, token) {
    const deadline = Date.now() + HEALTH_TIMEOUT_MS;
    while (Date.now() < deadline) {
        if (await probeHealth(baseUrl, token))
            return;
        await new Promise((r) => setTimeout(r, HEALTH_INTERVAL_MS));
    }
    throw new Error(`后端健康检查超时（${HEALTH_TIMEOUT_MS / 1000}s）: ${baseUrl}`);
}
async function killProcessTree(child) {
    if (child.pid === undefined || child.exitCode !== null)
        return;
    const pid = child.pid;
    await new Promise((resolve) => {
        if (process.platform === 'win32') {
            // Windows 必须杀进程树，否则残留 java 子进程
            const killer = (0, node_child_process_1.spawn)('taskkill', ['/pid', String(pid), '/T', '/F'], { windowsHide: true });
            killer.on('close', () => resolve());
            killer.on('error', () => resolve());
        }
        else {
            child.kill('SIGTERM');
            resolve();
        }
    });
}
/** 拉起 Java 后端 Sidecar 并等待就绪 */
async function startSidecar(options) {
    const port = await getFreePort();
    const token = (0, node_crypto_1.randomUUID)();
    const javaPath = findJava(options.runtimeDir);
    const baseUrl = `http://127.0.0.1:${port}`;
    const log = options.onLog ?? ((line) => console.log(`[backend] ${line}`));
    const child = (0, node_child_process_1.spawn)(javaPath, ['-jar', options.jarPath, `--server.port=${port}`, `--joyo.token=${token}`], { stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true });
    child.stdout?.on('data', (chunk) => log(chunk.toString().trimEnd()));
    child.stderr?.on('data', (chunk) => log(chunk.toString().trimEnd()));
    child.on('exit', (code) => log(`后端进程退出: code=${code}`));
    try {
        await waitForHealthy(baseUrl, token);
    }
    catch (e) {
        await killProcessTree(child);
        throw e;
    }
    return {
        baseUrl,
        token,
        stop: () => killProcessTree(child),
    };
}
/**
 * 解析后端连接方式：
 * - 开发模式（JOYO_DEV_BACKEND_URL 已设置）：直连手动启动的后端，不 spawn
 * - 打包模式（isPackaged=true）：spawn 内置 jar（resources/bin/joyo-backend.jar）；
 *   开发模式下也可用 JOYO_BACKEND_JAR 覆盖 jar 路径来测试 sidecar 流程
 */
async function resolveBackend(isPackaged) {
    const devUrl = process.env.JOYO_DEV_BACKEND_URL;
    if (devUrl) {
        const baseUrl = devUrl.replace(/\/$/, '');
        return {
            baseUrl,
            token: process.env.JOYO_DEV_TOKEN ?? 'dev-token',
            stop: async () => { },
        };
    }
    const jarPath = process.env.JOYO_BACKEND_JAR ??
        (isPackaged
            ? node_path_1.default.join(process.resourcesPath, 'bin', 'joyo-backend.jar')
            : node_path_1.default.join(__dirname, '../../../backend/target/joyo-backend.jar'));
    if (!node_fs_1.default.existsSync(jarPath)) {
        throw new Error(`找不到后端 jar: ${jarPath}\n开发模式请设置 JOYO_DEV_BACKEND_URL，` +
            `或先执行 mvn package 并用 JOYO_BACKEND_JAR 指定路径`);
    }
    const runtimeDir = process.env.JOYO_BACKEND_RUNTIME;
    return startSidecar({ jarPath, runtimeDir });
}
//# sourceMappingURL=sidecar.js.map
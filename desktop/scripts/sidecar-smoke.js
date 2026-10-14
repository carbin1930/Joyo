// sidecar 冒烟测试（无 GUI）：spawn jar → 健康检查 → HTTP 调用 → 清理进程树
// 用后即弃脚本，不纳入构建。用法：node sidecar-smoke.js <jar路径>
const assert = require('node:assert')
const { resolveBackend } = require('../dist-electron/main/sidecar.js')

async function main() {
  const jar = process.argv[2]
  assert.ok(jar, '用法: node sidecar-smoke.js <jar路径>')
  console.log('[smoke] 拉起后端:', jar)

  process.env.JOYO_BACKEND_JAR = jar
  const backend = await resolveBackend(false)
  console.log('[smoke] 后端就绪:', backend.baseUrl)

  const resp = await fetch(`${backend.baseUrl}/api/v1/health`, {
    headers: { 'X-Joyo-Token': backend.token },
  })
  const payload = await resp.json()
  assert.strictEqual(payload.code, 0, '健康检查应返回 code=0')
  console.log('[smoke] 带随机 Token 调用 /health 成功:', JSON.stringify(payload))

  const bad = await fetch(`${backend.baseUrl}/api/v1/health`, {
    headers: { 'X-Joyo-Token': 'wrong-token' },
  })
  const badPayload = await bad.json()
  assert.strictEqual(badPayload.code, 1003, '错误 Token 应返回 1003')
  console.log('[smoke] 错误 Token 被 1003 拦截 ✓')

  await backend.stop()
  console.log('[smoke] 进程树已清理 ✓')
}

main().catch((e) => {
  console.error('[smoke] 失败:', e)
  process.exit(1)
})

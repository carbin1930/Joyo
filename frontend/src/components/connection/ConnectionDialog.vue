<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import BaseDialog from '@/components/base/BaseDialog.vue'
import { connectionsIpc } from '@/api/ipc'
import { testConnection } from '@/api/connection'
import { useConnectionStore } from '@/stores/connection'
import { toast } from '@/composables/use-toast'
import { zhCN } from '@/locales/zh-CN'
import type { ConnectionListItem } from '@/types/ipc'

/**
 * 连接编辑对话框（spec-01 第 2 节）：新建/编辑共用；
 * 测试连接用表单当前值（无需保存）；编辑时密码留空 = 不修改。
 */

const props = defineProps<{
  open: boolean
  /** 传入 = 编辑；null = 新建 */
  editing: ConnectionListItem | null
  /** 复制连接：以该连接为模板新建 */
  cloneFrom: ConnectionListItem | null
}>()

const emit = defineEmits<{ close: [] }>()

const store = useConnectionStore()

const form = reactive({
  name: '',
  host: '127.0.0.1',
  port: 3306,
  username: 'root',
  password: '',
  defaultDatabase: '',
  ssh: {
    enabled: false,
    host: '',
    port: 22,
    username: '',
    authType: 'password' as 'password' | 'privateKey',
    password: '',
    privateKeyPath: '',
    passphrase: '',
  },
})
const showPassword = ref(false)
const sshOpen = ref(false)
const showSshPassword = ref(false)
const testing = ref(false)
const testResult = ref('')
const testOk = ref(false)
const saving = ref(false)

const isEdit = computed(() => props.editing !== null)

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    testResult.value = ''
    showPassword.value = false
    const source = props.editing ?? props.cloneFrom
    if (source) {
      form.name = props.cloneFrom ? `${source.name} - ${zhCN.connection.copySuffix}` : source.name
      form.host = source.host
      form.port = source.port
      form.username = source.username
      form.defaultDatabase = source.defaultDatabase
      // 编辑模式：解密回填原密码；复制模式：不复制密码（安全红线）
      form.password = props.editing ? ((await connectionsIpc.decryptPassword(source.id)) ?? '') : ''
      // spec-06：SSH 隧道配置回填；secret 解密回填仅编辑模式（复制不复制密码，同安全红线）
      const ssh = source.sshTunnel
      form.ssh.enabled = ssh?.enabled ?? false
      form.ssh.host = ssh?.host ?? ''
      form.ssh.port = ssh?.port ?? 22
      form.ssh.username = ssh?.username ?? ''
      form.ssh.authType = ssh?.authType ?? 'password'
      form.ssh.privateKeyPath = ssh?.privateKeyPath ?? ''
      if (props.editing && ssh?.enabled) {
        form.ssh.password =
          ssh.authType === 'password' ? ((await connectionsIpc.decryptPassword(source.id, 'sshPassword')) ?? '') : ''
        form.ssh.passphrase =
          ssh.authType === 'privateKey' ? ((await connectionsIpc.decryptPassword(source.id, 'sshPassphrase')) ?? '') : ''
      } else {
        form.ssh.password = ''
        form.ssh.passphrase = ''
      }
      sshOpen.value = ssh?.enabled ?? false
    } else {
      form.name = ''
      form.host = '127.0.0.1'
      form.port = 3306
      form.username = 'root'
      form.password = ''
      form.defaultDatabase = ''
      form.ssh.enabled = false
      form.ssh.host = ''
      form.ssh.port = 22
      form.ssh.username = ''
      form.ssh.authType = 'password'
      form.ssh.password = ''
      form.ssh.privateKeyPath = ''
      form.ssh.passphrase = ''
      sshOpen.value = false
    }
  },
)

function validate(): string | null {
  if (!form.name.trim()) return zhCN.connection.validate.name
  if (form.name.trim().length > 50) return zhCN.connection.validate.nameLength
  if (!form.host.trim()) return zhCN.connection.validate.host
  if (!Number.isInteger(form.port) || form.port < 1 || form.port > 65535)
    return zhCN.connection.validate.port
  if (!form.username.trim()) return zhCN.connection.validate.username
  // spec-06 第 2 节：启用 SSH 时的必填校验
  if (form.ssh.enabled) {
    if (!form.ssh.host.trim()) return zhCN.connection.ssh.validateHost
    if (!Number.isInteger(form.ssh.port) || form.ssh.port < 1 || form.ssh.port > 65535)
      return zhCN.connection.validate.port
    if (!form.ssh.username.trim()) return zhCN.connection.ssh.validateUsername
    if (form.ssh.authType === 'password' && !form.ssh.password)
      return zhCN.connection.ssh.validatePassword
    if (form.ssh.authType === 'privateKey' && !form.ssh.privateKeyPath.trim())
      return zhCN.connection.ssh.validateKeyPath
  }
  return null
}

/** SSH 保存入参：只带当前认证方式的 secret，另一个传 undefined（保留原值，spec-06 边界"改认证方式旧密码保留"） */
function buildSshInput() {
  const s = form.ssh
  const base = {
    enabled: s.enabled,
    host: s.host.trim(),
    port: s.port,
    username: s.username.trim(),
    authType: s.authType,
    privateKeyPath: s.privateKeyPath.trim(),
  }
  if (!s.enabled) return base
  return s.authType === 'password' ? { ...base, password: s.password } : { ...base, passphrase: s.passphrase }
}

function toPayload() {
  return {
    host: form.host.trim(),
    port: form.port,
    username: form.username.trim(),
    password: form.password,
    defaultDatabase: form.defaultDatabase.trim(),
    sshTunnel: buildSshInput(),
  }
}

/** spec-06：私钥文件选择器（Electron 原生对话框） */
async function onPickKey(): Promise<void> {
  try {
    const picked = await connectionsIpc.pickPrivateKey()
    if (picked) form.ssh.privateKeyPath = picked
  } catch (e) {
    toast('error', e instanceof Error ? e.message : String(e))
  }
}

async function onTest(): Promise<void> {
  const error = validate()
  if (error) {
    toast('error', error)
    return
  }
  testing.value = true
  testResult.value = ''
  try {
    const result = await testConnection(toPayload())
    testOk.value = true
    testResult.value = zhCN.connection.testOk(result.serverVersion, result.elapsedMs)
  } catch (e) {
    testOk.value = false
    testResult.value = e instanceof Error ? e.message : String(e)
  } finally {
    testing.value = false
  }
}

async function onSave(): Promise<void> {
  const error = validate()
  if (error) {
    toast('error', error)
    return
  }
  saving.value = true
  try {
    await store.save({
      id: props.editing?.id,
      name: form.name.trim(),
      ...toPayload(),
    })
    toast('success', zhCN.connection.saved)
    emit('close')
  } catch (e) {
    toast('error', e instanceof Error ? e.message : String(e))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <BaseDialog
    :open="open"
    :title="isEdit ? zhCN.connection.dialog.editTitle : zhCN.connection.dialog.newTitle"
    :width="480"
    @close="emit('close')"
  >
    <form class="conn-form" @submit.prevent>
      <label class="form-row">
        <span class="form-label">{{ zhCN.connection.field.name }}</span>
        <input v-model="form.name" type="text" maxlength="50" :placeholder="`${form.host}-${form.port}`" />
      </label>
      <label class="form-row">
        <span class="form-label">{{ zhCN.connection.field.host }}</span>
        <input v-model="form.host" type="text" required />
      </label>
      <label class="form-row">
        <span class="form-label">{{ zhCN.connection.field.port }}</span>
        <input v-model.number="form.port" type="number" min="1" max="65535" required />
      </label>
      <label class="form-row">
        <span class="form-label">{{ zhCN.connection.field.username }}</span>
        <input v-model="form.username" type="text" required />
      </label>
      <label class="form-row">
        <span class="form-label">{{ zhCN.connection.field.password }}</span>
        <span class="password-wrap">
          <input
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            :placeholder="isEdit ? zhCN.connection.passwordKeepHint : ''"
          />
          <button type="button" class="toggle-eye" @click="showPassword = !showPassword">
            {{ showPassword ? zhCN.connection.hide : zhCN.connection.show }}
          </button>
        </span>
      </label>
      <label class="form-row">
        <span class="form-label">{{ zhCN.connection.field.database }}</span>
        <input v-model="form.defaultDatabase" type="text" />
      </label>

      <div class="ssh-section" data-testid="ssh-section">
        <div class="ssh-header" @click="sshOpen = !sshOpen">
          <span class="ssh-caret">{{ sshOpen ? '▾' : '▸' }}</span>
          <span>{{ zhCN.connection.ssh.title }}</span>
          <label class="ssh-toggle" @click.stop>
            <input v-model="form.ssh.enabled" type="checkbox" />
            <span>{{ zhCN.connection.ssh.enabled }}</span>
          </label>
        </div>
        <template v-if="sshOpen">
          <label class="form-row">
            <span class="form-label">{{ zhCN.connection.ssh.host }}</span>
            <input
              v-model="form.ssh.host"
              type="text"
              :disabled="!form.ssh.enabled"
              :placeholder="zhCN.connection.ssh.hostPlaceholder"
            />
          </label>
          <label class="form-row">
            <span class="form-label">{{ zhCN.connection.ssh.port }}</span>
            <input v-model.number="form.ssh.port" type="number" min="1" max="65535" :disabled="!form.ssh.enabled" />
          </label>
          <label class="form-row">
            <span class="form-label">{{ zhCN.connection.ssh.username }}</span>
            <input v-model="form.ssh.username" type="text" :disabled="!form.ssh.enabled" />
          </label>
          <div class="form-row">
            <span class="form-label">{{ zhCN.connection.ssh.authType }}</span>
            <span class="auth-radios">
              <label>
                <input v-model="form.ssh.authType" type="radio" value="password" :disabled="!form.ssh.enabled" />
                {{ zhCN.connection.ssh.authPassword }}
              </label>
              <label>
                <input v-model="form.ssh.authType" type="radio" value="privateKey" :disabled="!form.ssh.enabled" />
                {{ zhCN.connection.ssh.authPrivateKey }}
              </label>
            </span>
          </div>
          <label v-if="form.ssh.authType === 'password'" class="form-row">
            <span class="form-label">{{ zhCN.connection.ssh.password }}</span>
            <span class="password-wrap">
              <input
                v-model="form.ssh.password"
                :type="showSshPassword ? 'text' : 'password'"
                :disabled="!form.ssh.enabled"
              />
              <button type="button" class="toggle-eye" @click="showSshPassword = !showSshPassword">
                {{ showSshPassword ? zhCN.connection.hide : zhCN.connection.show }}
              </button>
            </span>
          </label>
          <template v-else>
            <div class="form-row">
              <span class="form-label">{{ zhCN.connection.ssh.privateKeyPath }}</span>
              <span class="password-wrap">
                <input v-model="form.ssh.privateKeyPath" type="text" :disabled="!form.ssh.enabled" />
                <button type="button" class="toggle-eye" :disabled="!form.ssh.enabled" @click="onPickKey">
                  {{ zhCN.connection.ssh.pickKey }}
                </button>
              </span>
            </div>
            <label class="form-row">
              <span class="form-label">{{ zhCN.connection.ssh.passphrase }}</span>
              <input v-model="form.ssh.passphrase" type="password" :disabled="!form.ssh.enabled" />
            </label>
          </template>
        </template>
      </div>

      <div class="test-line" data-testid="test-result">
        <span v-if="testResult" :class="testOk ? 'test-ok' : 'test-fail'">{{ testResult }}</span>
      </div>

      <div class="form-actions">
        <button type="button" class="btn" :disabled="testing" @click="onTest">
          {{ testing ? zhCN.connection.testing : zhCN.connection.test }}
        </button>
        <span class="spacer" />
        <button type="button" class="btn" @click="emit('close')">{{ zhCN.common.cancel }}</button>
        <button type="button" class="btn btn-primary" :disabled="saving" @click="onSave">
          {{ zhCN.common.save }}
        </button>
      </div>
    </form>
  </BaseDialog>
</template>

<style scoped>
.conn-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.form-label {
  width: 90px;
  flex-shrink: 0;
  color: var(--joyo-text-muted);
}

.form-row input {
  flex: 1;
  padding: 7px 10px;
  border: 1px solid var(--joyo-border);
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.form-row input:focus {
  border-color: var(--joyo-primary);
}

.password-wrap {
  flex: 1;
  display: flex;
  gap: 6px;
}

.password-wrap input {
  flex: 1;
  padding: 7px 10px;
  border: 1px solid var(--joyo-border);
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.toggle-eye {
  border: 1px solid var(--joyo-border);
  background: none;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  cursor: pointer;
  color: var(--joyo-text-muted);
}

.test-line {
  min-height: 20px;
  font-size: 12px;
}

.test-ok {
  color: var(--joyo-success);
}

.test-fail {
  color: var(--joyo-danger);
}

.form-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.spacer {
  flex: 1;
}

.btn {
  padding: 7px 14px;
  border: 1px solid var(--joyo-border);
  border-radius: 6px;
  background: var(--joyo-bg-card);
  cursor: pointer;
  font-size: 13px;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: var(--joyo-primary);
  border-color: var(--joyo-primary);
  color: #fff;
}

/* ===== SSH 隧道折叠区（spec-06） ===== */
.ssh-section {
  border: 1px solid var(--joyo-border);
  border-radius: 6px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ssh-header {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  user-select: none;
}

.ssh-caret {
  color: var(--joyo-text-muted);
  font-size: 11px;
}

.ssh-toggle {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: normal;
  color: var(--joyo-text-muted);
  cursor: pointer;
}

.ssh-section input:disabled {
  opacity: 0.5;
}

.auth-radios {
  flex: 1;
  display: flex;
  gap: 16px;
  font-size: 13px;
}

.auth-radios label {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}
</style>

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
})
const showPassword = ref(false)
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
    } else {
      form.name = ''
      form.host = '127.0.0.1'
      form.port = 3306
      form.username = 'root'
      form.password = ''
      form.defaultDatabase = ''
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
  return null
}

function toPayload() {
  return {
    host: form.host.trim(),
    port: form.port,
    username: form.username.trim(),
    password: form.password,
    defaultDatabase: form.defaultDatabase.trim(),
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
</style>

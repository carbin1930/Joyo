<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useConnectionStore } from '@/stores/connection'
import { toast } from '@/composables/use-toast'
import { zhCN } from '@/locales/zh-CN'
import ConnectionDialog from '@/components/connection/ConnectionDialog.vue'
import type { ConnectionListItem } from '@/types/ipc'

/**
 * 连接列表侧栏（spec-01 第 2 节）：
 * + 新建 / 单击选中 / 双击连接 / 右键菜单（编辑/删除/复制连接）/ 删除二次确认。
 */

const store = useConnectionStore()

const dialogOpen = ref(false)
const editing = ref<ConnectionListItem | null>(null)
const cloneFrom = ref<ConnectionListItem | null>(null)
const confirmDelete = ref<ConnectionListItem | null>(null)
const connectingId = ref<string | null>(null)

const contextMenu = reactive({ show: false, x: 0, y: 0, target: null as ConnectionListItem | null })

function openNew(): void {
  editing.value = null
  cloneFrom.value = null
  dialogOpen.value = true
}

function openEdit(item: ConnectionListItem): void {
  editing.value = item
  cloneFrom.value = null
  dialogOpen.value = true
}

function openClone(item: ConnectionListItem): void {
  editing.value = null
  cloneFrom.value = item
  dialogOpen.value = true
}

function onContextMenu(e: MouseEvent, item: ConnectionListItem): void {
  e.preventDefault()
  contextMenu.show = true
  contextMenu.x = e.clientX
  contextMenu.y = e.clientY
  contextMenu.target = item
}

function closeContextMenu(): void {
  contextMenu.show = false
}

async function onDoubleClick(item: ConnectionListItem): Promise<void> {
  if (store.isConnected(item.id) || connectingId.value) return
  connectingId.value = item.id
  try {
    await store.connect(item.id)
  } finally {
    connectingId.value = null
  }
}

function askDelete(item: ConnectionListItem): void {
  confirmDelete.value = item
}

async function doDelete(): Promise<void> {
  if (!confirmDelete.value) return
  try {
    await store.remove(confirmDelete.value.id)
    toast('success', zhCN.connection.deleted)
  } catch (e) {
    toast('error', e instanceof Error ? e.message : String(e))
  } finally {
    confirmDelete.value = null
  }
}

onMounted(() => {
  void store.load()
  window.addEventListener('click', closeContextMenu)
  window.addEventListener('blur', closeContextMenu)
})

onUnmounted(() => {
  window.removeEventListener('click', closeContextMenu)
  window.removeEventListener('blur', closeContextMenu)
})
</script>

<template>
  <div class="conn-list">
    <button class="add-btn" data-testid="new-connection" @click="openNew">
      + {{ zhCN.connection.new }}
    </button>

    <div class="list">
      <div
        v-for="item in store.items"
        :key="item.id"
        class="conn-item"
        :class="{ selected: store.selectedId === item.id }"
        data-testid="connection-item"
        @click="store.select(item.id)"
        @dblclick="onDoubleClick(item)"
        @contextmenu="onContextMenu($event, item)"
      >
        <span
          class="dot"
          :class="{
            connected: store.isConnected(item.id),
            connecting: connectingId === item.id,
          }"
        />
        <span class="info">
          <span class="name">{{ item.name }}</span>
          <span class="summary">{{ item.host }}:{{ item.port }}</span>
        </span>
      </div>
      <div v-if="store.items.length === 0 && !store.loading" class="empty">
        {{ zhCN.connection.empty }}
      </div>
    </div>

    <!-- 右键菜单 -->
    <Teleport to="body">
      <div
        v-if="contextMenu.show && contextMenu.target"
        class="ctx-menu"
        :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
      >
        <button class="ctx-item" @click="openEdit(contextMenu.target)">
          {{ zhCN.connection.menu.edit }}
        </button>
        <button class="ctx-item" @click="openClone(contextMenu.target)">
          {{ zhCN.connection.menu.clone }}
        </button>
        <button class="ctx-item danger" @click="askDelete(contextMenu.target)">
          {{ zhCN.connection.menu.delete }}
        </button>
      </div>
    </Teleport>

    <!-- 删除二次确认 -->
    <Teleport to="body">
      <div v-if="confirmDelete" class="confirm-overlay" @mousedown.self="confirmDelete = null">
        <div class="confirm-panel">
          <p class="confirm-text">
            {{ zhCN.connection.confirmDelete(confirmDelete.name) }}
          </p>
          <div class="confirm-actions">
            <button class="btn" @click="confirmDelete = null">{{ zhCN.common.cancel }}</button>
            <button class="btn btn-danger" data-testid="confirm-delete" @click="doDelete">
              {{ zhCN.connection.menu.delete }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <ConnectionDialog
      :open="dialogOpen"
      :editing="editing"
      :clone-from="cloneFrom"
      @close="dialogOpen = false"
    />
  </div>
</template>

<style scoped>
.conn-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  flex-shrink: 0;
  max-height: 42%;
}

.add-btn {
  height: var(--joyo-h-control);
  padding: 0 10px;
  border: 1px solid var(--joyo-border);
  border-radius: var(--joyo-radius);
  background: var(--joyo-bg-card);
  cursor: pointer;
  color: var(--joyo-primary);
  font-size: var(--joyo-fs-md);
  font-weight: 500;
  transition:
    background-color var(--joyo-transition),
    border-color var(--joyo-transition);
}

.add-btn:hover {
  border-color: var(--joyo-primary);
  background: var(--joyo-primary-weak);
}

.list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow: auto;
}

.conn-item {
  display: flex;
  align-items: center;
  gap: var(--joyo-space-2);
  padding: 6px 8px;
  border-radius: var(--joyo-radius);
  cursor: pointer;
  user-select: none;
  transition: background-color var(--joyo-transition);
}

.conn-item:hover {
  background: var(--joyo-bg-hover);
}

/* 选中：主色弱底 + 左侧 2px 主色条（与对象树选中态统一） */
.conn-item.selected {
  background: var(--joyo-primary-weak);
  box-shadow: inset 2px 0 0 var(--joyo-primary);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--joyo-border);
  flex-shrink: 0;
}

.dot.connected {
  background: var(--joyo-success);
}

.dot.connecting {
  background: var(--joyo-warning);
}

.info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.name {
  font-size: var(--joyo-fs-md);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.summary {
  font-size: var(--joyo-fs-xs);
  color: var(--joyo-text-muted);
  font-variant-numeric: tabular-nums;
}

.empty {
  color: var(--joyo-text-muted);
  font-size: 12px;
  text-align: center;
  padding: 16px 0;
}

.ctx-menu {
  position: fixed;
  z-index: 600;
  background: var(--joyo-bg-elevated);
  border: 1px solid var(--joyo-border);
  border-radius: var(--joyo-radius);
  box-shadow: var(--joyo-shadow-2);
  padding: var(--joyo-space-1);
  display: flex;
  flex-direction: column;
  min-width: 132px;
}

.ctx-item {
  border: none;
  background: none;
  text-align: left;
  padding: 6px 10px;
  font-size: var(--joyo-fs-md);
  border-radius: var(--joyo-radius-sm);
  cursor: pointer;
  color: var(--joyo-text);
  transition: background-color var(--joyo-transition);
}

.ctx-item:hover {
  background: var(--joyo-bg-hover);
}

.ctx-item.danger {
  color: var(--joyo-danger);
}

.confirm-overlay {
  position: fixed;
  inset: 0;
  background: rgb(0 0 0 / 40%);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 700;
}

.confirm-panel {
  background: var(--joyo-bg-elevated);
  border: 1px solid var(--joyo-border);
  border-radius: var(--joyo-radius-lg);
  box-shadow: var(--joyo-shadow-2);
  padding: var(--joyo-space-4);
  width: 360px;
}

.confirm-text {
  margin: 0 0 var(--joyo-space-3);
  font-size: var(--joyo-fs-md);
  color: var(--joyo-text);
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.btn {
  height: var(--joyo-h-control);
  padding: 0 12px;
  border: 1px solid var(--joyo-border);
  border-radius: var(--joyo-radius);
  background: var(--joyo-bg-card);
  cursor: pointer;
  font-size: var(--joyo-fs-md);
  color: var(--joyo-text);
  transition:
    background-color var(--joyo-transition),
    border-color var(--joyo-transition);
}

.btn:hover {
  border-color: var(--joyo-border-strong);
  background: var(--joyo-bg-hover);
}

.btn-danger {
  background: var(--joyo-danger);
  border-color: var(--joyo-danger);
  color: #fff;
}

.btn-danger:hover {
  background: #c23b3b;
  border-color: #c23b3b;
}
</style>

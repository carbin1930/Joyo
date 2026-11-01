<script setup lang="ts">
import { computed } from 'vue'
import BaseDialog from '@/components/base/BaseDialog.vue'
import { useBackendStore } from '@/stores/backend'
import { zhCN } from '@/locales/zh-CN'

/**
 * 关于面板（菜单 → 帮助 → 关于 Joyo）：
 * 展示应用版本、后端 Java 版本、Electron 运行时版本、作者与项目地址。
 * 版本信息来自后端 /health（含 javaVersion）与 preload 暴露的 process.versions。
 */

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const backend = useBackendStore()

const repoUrl = 'https://github.com/carbin1930/Joyo'
const author = 'carbin1930'

const appVersion = computed(() => backend.info?.version ?? '—')
const javaText = computed(() => {
  const version = backend.info?.javaVersion
  if (!version) return zhCN.about.unknown
  const vendor = backend.info?.javaVendor
  return vendor ? `${version}（${vendor}）` : version
})
const electronText = computed(() => {
  const v = window.joyo?.versions
  return v?.electron || '—'
})
const chromiumText = computed(() => window.joyo?.versions?.chromium || '—')
const nodeText = computed(() => window.joyo?.versions?.node || '—')

async function openRepo(): Promise<void> {
  try {
    await window.joyo?.openExternal(repoUrl)
  } catch {
    // 打不开浏览器时忽略：链接文本本身可见可复制
  }
}
</script>

<template>
  <BaseDialog :open="props.open" :title="zhCN.about.title" :width="440" @close="emit('close')">
    <div class="about" data-testid="about-dialog">
      <div class="brand">
        <div class="logo">Joyo</div>
        <div class="slogan">{{ zhCN.app.slogan }}</div>
      </div>

      <dl class="info">
        <dt>{{ zhCN.about.version }}</dt>
        <dd data-testid="about-version">v{{ appVersion }}</dd>

        <dt>{{ zhCN.about.java }}</dt>
        <dd data-testid="about-java">{{ javaText }}</dd>

        <dt>{{ zhCN.about.electron }}</dt>
        <dd>{{ electronText }} · Chromium {{ chromiumText }} · Node {{ nodeText }}</dd>

        <dt>{{ zhCN.about.author }}</dt>
        <dd data-testid="about-author">{{ author }}</dd>

        <dt>{{ zhCN.about.repo }}</dt>
        <dd>
          <button class="repo-link" data-testid="about-repo" @click="openRepo">
            {{ repoUrl }}
          </button>
        </dd>
      </dl>

      <div class="footer">{{ zhCN.about.copyright }}</div>
    </div>
  </BaseDialog>
</template>

<style scoped>
.about {
  display: flex;
  flex-direction: column;
  gap: var(--joyo-space-4);
}

.brand {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.logo {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.6px;
  color: var(--joyo-primary);
}

.slogan {
  font-size: var(--joyo-fs-sm);
  color: var(--joyo-text-muted);
}

.info {
  display: grid;
  grid-template-columns: 88px 1fr;
  row-gap: var(--joyo-space-2);
  column-gap: var(--joyo-space-2);
  margin: 0;
  font-size: var(--joyo-fs-md);
}

.info dt {
  color: var(--joyo-text-muted);
}

.info dd {
  margin: 0;
  color: var(--joyo-text);
  word-break: break-all;
}

.repo-link {
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  font-size: var(--joyo-fs-md);
  color: var(--joyo-primary);
  text-align: left;
  word-break: break-all;
}

.repo-link:hover {
  text-decoration: underline;
}

.footer {
  padding-top: var(--joyo-space-2);
  border-top: 1px solid var(--joyo-border);
  font-size: var(--joyo-fs-xs);
  color: var(--joyo-text-subtle);
}
</style>

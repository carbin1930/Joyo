<script setup lang="ts">
import editorWorker from 'monaco-editor/editor/editor.worker.js?worker'
import * as monaco from 'monaco-editor'
import { onMounted, onUnmounted, ref, watch } from 'vue'

/**
 * Monaco Editor 封装（spec-03 第 2 节）：
 * - language=sql；Ctrl+Enter 触发运行（选中优先，否则光标所在语句）；Ctrl+/ 行注释
 * - 业务代码不直接操作 monaco 实例（coding-frontend.md 第 6 节）
 */

const props = defineProps<{
  modelValue: string
  readonly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  run: [sql: string]
}>()

const container = ref<HTMLDivElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null

self.MonacoEnvironment = {
  getWorker() {
    return new editorWorker()
  },
}

/** 光标所在语句提取：按引号外的分号定位边界（与后端拆分规则近似，v0.1 够用） */
function statementAt(model: monaco.editor.ITextModel, offset: number): string {
  const text = model.getValue()
  let start = 0
  let end = text.length
  let inQuote: string | null = null
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    if (inQuote) {
      if (c === '\\') {
        i++
      } else if (c === inQuote) {
        inQuote = null
      }
      continue
    }
    if (c === '\'' || c === '"' || c === '`') {
      inQuote = c
    } else if (c === ';' || i < offset) {
      start = i + 1
    } else if (c === ';' || i >= offset) {
      end = i
      break
    }
  }
  return text.slice(start, end).trim()
}

function requestRun(): void {
  if (!editor) return
  const model = editor.getModel()
  if (!model) return
  const selection = editor.getSelection()
  const selected = selection && !selection.isEmpty()
    ? model.getValueInRange(selection).trim()
    : ''
  const position = editor.getPosition()
  const sql = selected; (position ? statementAt(model, model.getOffsetAt(position)) : '')
  if (sql) emit('run', sql)
}

onMounted(() => {
  if (!container.value) return
  editor = monaco.editor.create(container.value, {
    value: props.modelValue,
    language: 'sql',
    theme: 'vs',
    fontSize: 13,
    minimap: { enabled: false },
    automaticLayout: true,
    scrollBeyondLastLine: false,
    tabSize: 2,
    readOnly: props.readonly ?? false,
  })
  editor.onDidChangeModelContent(() => {
    emit('update:modelValue', editor?.getValue() ?? '')
  })
  editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, requestRun)
  editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Slash, () => {
    editor?.trigger('keyboard', 'editor.action.commentLine', null)
  })
})

onUnmounted(() => {
  editor?.dispose()
})

watch(
  () => props.modelValue,
  (value) => {
    if (editor && editor.getValue() !== value) {
      editor.setValue(value)
    }
  },
)

defineExpose({ requestRun })
</script>

<template>
  <div ref="container" class="monaco-container" />
</template>

<style scoped>
.monaco-container {
  width: 100%;
  height: 100%;
  border: 1px solid var(--joyo-border);
  border-radius: 6px;
  overflow: hidden;
}
</style>

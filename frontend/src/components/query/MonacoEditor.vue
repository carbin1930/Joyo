<script setup lang="ts">
import editorWorker from 'monaco-editor/editor/editor.worker.js?worker'
import * as monaco from 'monaco-editor'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useSchemaStore } from '@/stores/schema'
import { useConnectionStore } from '@/stores/connection'
import type { TableDetailResponse } from '@/types/api'

/**
 * Monaco Editor 封装（spec-03 第 2 节）：
 * - language=sql；Ctrl+Enter 触发运行（选中优先，否则光标所在语句）；Ctrl+/ 行注释
 * - 业务代码不直接操作 monaco 实例（coding-frontend.md 第 6 节）
 */

const props = defineProps<{
  modelValue: string
  readonly?: boolean
  /** 外部插入请求（对象树双击表名）：seq 变化时在光标处插入 text */
  insertRequest?: { text: string; seq: number } | null
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
  const sql = selected || (position ? statementAt(model, model.getOffsetAt(position)) : '')
  if (sql) emit('run', sql)
}

/** 在光标处插入文本并聚焦 */
function insertAtCursor(text: string): void {
  if (!editor) return
  const position = editor.getPosition()
  if (!position) return
  editor.executeEdits('joyo', [{
    range: new monaco.Range(position.lineNumber, position.column, position.lineNumber, position.column),
    text,
    forceMoveMarkers: true,
  }])
  editor.focus()
}

watch(
  () => props.insertRequest?.seq,
  (seq) => {
    if (seq != null && props.insertRequest?.text) {
      insertAtCursor(props.insertRequest.text)
    }
  },
)

/** SQL 补全只注册一次：标志挂在 globalThis 上（monaco 模块命名空间不可扩展），跨 HMR 持久 */
const globalFlags = globalThis as unknown as { __joyoSqlCompletion?: boolean }

/** 基于已加载元数据的 SQL 补全：关键字 + 库/表/列（无后端请求） */
function registerSqlCompletion(): void {
  if (globalFlags.__joyoSqlCompletion) return
  globalFlags.__joyoSqlCompletion = true
  const schemaStore = useSchemaStore()
  const keywords = [
    'SELECT', 'FROM', 'WHERE', 'INSERT INTO', 'UPDATE', 'DELETE FROM', 'JOIN',
    'LEFT JOIN', 'RIGHT JOIN', 'INNER JOIN', 'ON', 'GROUP BY', 'ORDER BY',
    'HAVING', 'LIMIT', 'OFFSET', 'AS', 'AND', 'OR', 'NOT', 'IN', 'LIKE',
    'BETWEEN', 'DISTINCT', 'COUNT', 'SUM', 'AVG', 'MAX', 'MIN', 'DESC', 'ASC',
    'USE', 'SHOW TABLES', 'SHOW DATABASES', 'CREATE TABLE', 'ALTER TABLE',
    'DROP TABLE', 'TRUNCATE TABLE', 'EXPLAIN', 'IS NULL', 'IS NOT NULL',
  ]
  monaco.languages.registerCompletionItemProvider('sql', {
    triggerCharacters: ['.', '`'],
    provideCompletionItems(model, position) {
      const word = model.getWordUntilPosition(position)
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      }
      const suggestions: monaco.languages.CompletionItem[] = []

      // 点号补全：三级解析——库名. → 表；表名./别名. → 该表的列
      const lineBefore = model.getValueInRange({
        startLineNumber: position.lineNumber,
        startColumn: 1,
        endLineNumber: position.lineNumber,
        endColumn: position.column,
      })
      const dotMatch = lineBefore.match(/([\w\u4e00-\u9fa5`]+)\.\w*$/)
      if (dotMatch) {
        const prefix = dotMatch[1].replace(/`/g, '')

        // 1) 库名 → 该库的表（未加载则后台触发加载，本次先返回空）
        let schemaTables: { name: string; type: 'BASE_TABLE' | 'VIEW' }[] = []
        let isSchema = false
        const activeConnId = useConnectionStore().connectedIds[0] ?? null
        for (const tree of Object.values(schemaStore.trees)) {
          for (const s of tree.schemas) {
            if (s.name === prefix) {
              isSchema = true
              schemaTables = schemaTables.concat(tree.tablesBySchema[s.name] ?? [])
            }
          }
        }
        if (isSchema) {
          if (schemaTables.length === 0 && activeConnId) {
            // 首次点号触发后台加载：给出占位提示，再输一个字符即可看到表名
            void schemaStore.loadTablesIfMissing(activeConnId, prefix)
            suggestions.push({
              label: '(正在加载表列表…请再输入一个字符)',
              kind: monaco.languages.CompletionItemKind.Text,
              insertText: '',
              range,
            })
            return { suggestions }
          }
          for (const t of schemaTables) {
            const isView = t.type === 'VIEW'
            suggestions.push({
              label: t.name,
              detail: (isView ? '视图' : '表') + ` · ${prefix}`,
              kind: isView
                ? monaco.languages.CompletionItemKind.Interface
                : monaco.languages.CompletionItemKind.Class,
              insertText: t.name,
              range,
            })
          }
          return { suggestions }
        }

        // 2) 别名/表名 → 该表的列
        const aliasMap = buildAliasMap(model)
        const tableName = aliasMap[prefix.toLowerCase()] ?? (isKnownTable(prefix) ? prefix : null)
        if (tableName) {
          const seen = new Set<string>()
          for (const [key, d] of Object.entries(allDetails())) {
            if (key !== tableName && !key.endsWith('.' + tableName)) continue
            for (const col of d.columns) {
              if (seen.has(col.name)) continue
              seen.add(col.name)
              suggestions.push({
                label: col.name,
                detail: `${tableName} · ${col.dataType}`,
                kind: monaco.languages.CompletionItemKind.Field,
                insertText: col.name,
                range,
              })
            }
          }
          return { suggestions }
        }
      }

      for (const k of keywords) {
        suggestions.push({
          label: k,
          kind: monaco.languages.CompletionItemKind.Keyword,
          insertText: k,
          range,
        })
      }
      const seenColumns = new Set<string>()
      for (const tree of Object.values(schemaStore.trees)) {
        for (const s of tree.schemas) {
          suggestions.push({
            label: s.name,
            detail: '数据库',
            kind: monaco.languages.CompletionItemKind.Module,
            insertText: s.name,
            range,
          })
        }
        for (const [schema, tables] of Object.entries(tree.tablesBySchema)) {
          for (const t of tables) {
            const isView = t.type === 'VIEW'
            suggestions.push({
              label: t.name,
              detail: (isView ? '视图' : '表') + ` · ${schema}`,
              kind: isView
                ? monaco.languages.CompletionItemKind.Interface
                : monaco.languages.CompletionItemKind.Class,
              insertText: t.name,
              range,
            })
          }
        }
        for (const [key, d] of Object.entries(tree.detailByKey)) {
          for (const col of d.columns) {
            // 同名列（如各表都有 id）只保留一条建议
            if (seenColumns.has(col.name)) continue
            seenColumns.add(col.name)
            suggestions.push({
              label: col.name,
              detail: `${key} · ${col.dataType}`,
              kind: monaco.languages.CompletionItemKind.Field,
              insertText: col.name,
              range,
            })
          }
        }
      }

      // 按光标所在 SQL 子句动态排序
      const rank = analyzeContext(model, position)
      for (const s of suggestions) {
        const prefix =
          s.kind === monaco.languages.CompletionItemKind.Keyword
            ? rank.keyword
            : s.kind === monaco.languages.CompletionItemKind.Module
              ? rank.schema
              : s.kind === monaco.languages.CompletionItemKind.Field
                ? rank.column
                : rank.table
        s.sortText = prefix + (s.label ?? '')
      }
      return { suggestions }
    },
  })
}

/** 汇总所有连接已加载的表详情（key = `schema.table`） */
function allDetails(): Record<string, TableDetailResponse> {
  const out: Record<string, TableDetailResponse> = {}
  for (const tree of Object.values(useSchemaStore().trees)) {
    Object.assign(out, tree.detailByKey)
  }
  return out
}

function isKnownTable(name: string): boolean {
  for (const tree of Object.values(useSchemaStore().trees)) {
    for (const tables of Object.values(tree.tablesBySchema)) {
      if (tables.some((t) => t.name === name)) return true
    }
  }
  return false
}

const ALIAS_STOP_WORDS = new Set([
  'WHERE', 'GROUP', 'ORDER', 'SET', 'LEFT', 'RIGHT', 'INNER', 'OUTER',
  'ON', 'LIMIT', 'VALUES', 'SELECT', 'UNION', 'AS', 'CROSS',
])

/**
 * 上下文分析：根据光标前最近的子句关键字返回建议排序权重。
 * '0' 最高。SELECT→列优先；FROM/JOIN/UPDATE/INTO→表优先；
 * WHERE/ON/AND/OR/HAVING/ORDER BY/GROUP BY/SET→列优先；USE/SHOW→库名优先。
 */
function analyzeContext(
  model: monaco.editor.ITextModel,
  position: monaco.Position,
): Record<'table' | 'column' | 'keyword' | 'schema', string> {
  const offset = model.getOffsetAt(position)
  const text = model.getValue().slice(0, offset)
  const stmt = text.slice(text.lastIndexOf(';') + 1)
  const tokens = stmt.toUpperCase().match(/[A-Z_]{2,}/g) ?? []

  for (let i = tokens.length - 1; i >= 0; i--) {
    const t = tokens[i]
    const prev = i > 0 ? tokens[i - 1] : ''
    const pair = prev ? prev + ' ' + t : ''
    if (t === 'BY' && (pair === 'ORDER BY' || pair === 'GROUP BY')) {
      return { table: '2', column: '0', keyword: '1', schema: '3' }
    }
    if (t === 'SELECT') return { table: '2', column: '0', keyword: '1', schema: '3' }
    if (t === 'FROM' || t === 'JOIN' || t === 'UPDATE' || t === 'INTO') {
      return { table: '0', column: '2', keyword: '2', schema: '1' }
    }
    if (
      t === 'WHERE' || t === 'ON' || t === 'AND' || t === 'OR' || t === 'HAVING' ||
      t === 'LIKE' || t === 'IN' || t === 'BETWEEN'
    ) {
      return { table: '2', column: '0', keyword: '1', schema: '3' }
    }
    if (t === 'USE' || t === 'SHOW') return { table: '2', column: '3', keyword: '2', schema: '0' }
    if (t === 'SET') return { table: '1', column: '0', keyword: '2', schema: '3' }
    if (t === 'AS') return { table: '2', column: '2', keyword: '2', schema: '2' }
  }
  // 语句开头：语句级关键字优先
  return { table: '1', column: '1', keyword: '0', schema: '1' }
}

/** 从 SQL 文本解析 别名 → 表名 映射（FROM/JOIN/UPDATE/INSERT INTO 后的 `表 [AS] 别名`） */
function buildAliasMap(model: monaco.editor.ITextModel): Record<string, string> {
  const text = model.getValue()
  const map: Record<string, string> = {}
  const re =
    /(?:FROM|JOIN|UPDATE|INTO)\s+`?([\w\u4e00-\u9fa5$]+)`?(?:\s+(?:AS\s+)?`?([\w\u4e00-\u9fa5$]+)`?)?/gi
  let m: RegExpExecArray | null
  while ((m = re.exec(text)) !== null) {
    if (m[2] && !ALIAS_STOP_WORDS.has(m[2].toUpperCase())) {
      map[m[2].toLowerCase()] = m[1]
    }
  }
  return map
}

onMounted(() => {
  if (!container.value) return
  registerSqlCompletion()
  editor = monaco.editor.create(container.value, {
    value: props.modelValue,
    language: 'sql',
    theme: 'vs',
    fontSize: 13,
    minimap: { enabled: false },
    automaticLayout: true,
    scrollBeyondLastLine: false,
    tabSize: 2,
    quickSuggestions: { other: true, comments: false, strings: false },
    suggestOnTriggerCharacters: true,
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

defineExpose({
  requestRun,
  /** 聚焦编辑器（spec-05 历史回填后调用） */
  focus: () => editor?.focus(),
})
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

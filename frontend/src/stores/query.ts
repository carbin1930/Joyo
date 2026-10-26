import { defineStore } from 'pinia'
import { executeQuery, getQueryRows } from '@/api/query'
import { applyMutations } from '@/api/mutations'
import { toast } from '@/composables/use-toast'
import {
  addInsertRow,
  buildPayload,
  createState,
  discard,
  markDeleted,
  setCell,
  type EditState,
} from '@/composables/use-result-edit'
import type { StatementResult } from '@/types/api'
import type { HistoryEntryInput } from '@/types/ipc'
import { zhCN } from '@/locales/zh-CN'
import { useConnectionStore } from '@/stores/connection'

/** 结果面板的一个 Tab（每条语句一个；错误整体一个 Tab） */
export interface ResultTab {
  id: string
  ordinal: number
  kind: 'SELECT' | 'UPDATE' | 'DDL' | 'ERROR'
  title: string
  statement?: StatementResult
  errorMessage?: string
}

/** 一个 SQL 页签：独立的草稿、目标库与结果 */
export interface SqlTab {
  id: string
  title: string
  connectionId: string
  db: string
}

let sqlTabSeq = 0
let resultSeq = 0
let insertSeq = 0

function nextSqlTabId(): string {
  sqlTabSeq += 1
  return `sql-${sqlTabSeq}`
}

function nextResultId(): string {
  resultSeq += 1
  return `result-${resultSeq}`
}

function toTab(s: StatementResult, total: number): ResultTab {
  const title =
    s.kind === 'SELECT'
      ? zhCN.query.resultTab(s.ordinal, total)
      : `${zhCN.query.messageTab(s.ordinal, total)} - ${s.kind}`
  return { id: nextResultId(), ordinal: s.ordinal, kind: s.kind, title, statement: s }
}

/** spec-05：执行结束后记录查询历史（成功与失败都记；失败静默不影响查询） */
async function appendHistory(entry: Omit<HistoryEntryInput, 'executedAt'>): Promise<void> {
  try {
    // node 单测环境无 window，用 globalThis 属性访问避免未声明引用错误
    await globalThis.window?.joyo?.history?.append({
      ...entry,
      executedAt: new Date().toISOString(),
    })
  } catch (e) {
    console.warn('[query] 记录查询历史失败', e)
  }
}

/** 单条 SELECT 记 totalRows，其余（多语句/DML/失败）不记行数 */
function historyRowCount(stmts: StatementResult[]): number | null {
  if (stmts.length !== 1) return null
  const s = stmts[0]
  return s.kind === 'SELECT' ? (s.totalRows ?? s.rows?.length ?? null) : null
}

/**
 * 查询编辑器状态（spec-03 + F09 多页签提前实现）：
 * - sqlTabs：多个 SQL 页签，每个页签独立绑定库（执行时后端按页签 USE）
 * - drafts/resultsByTab：按页签隔离的草稿与结果
 * - mainTab：主区顶层页签（sql / 日志）
 */
export const useQueryStore = defineStore('query', {
  state: () => ({
    sqlTabs: [] as SqlTab[],
    /** 主区页签顺序（SQL 页签 id + 'log'），支持拖拽重排 */
    mainTabsOrder: [] as string[],
    /** 当前激活的主区页签（SQL 页签 id 或 'log'） */
    activeMainTabId: null as string | null,
    drafts: {} as Record<string, string>,
    resultsByTab: {} as Record<string, ResultTab[]>,
    activeResultByTab: {} as Record<string, string | null>,
    executing: false,
    savingEdit: false,
    maxRows: 1000,
    insertRequest: null as { text: string; seq: number } | null,
    resultsRev: 0,
    /** spec-16：结果编辑态，按结果页签 id 隔离（翻页/切换页签不清空） */
    edits: {} as Record<string, EditState>,
  }),

  getters: {
    activeSqlTab(state): SqlTab | null {
      if (!state.activeMainTabId || state.activeMainTabId === 'log') return null
      return state.sqlTabs.find((t) => t.id === state.activeMainTabId) ?? null
    },
    activeResultTabs(state): ResultTab[] {
      const id = state.activeMainTabId
      if (!id || id === 'log') return []
      return state.resultsByTab[id] ?? []
    },
    activeResultId(state): string | null {
      const id = state.activeMainTabId
      if (!id || id === 'log') return null
      return state.activeResultByTab[id] ?? null
    },
  },

  actions: {
    /** 确保某连接至少有一个 SQL 页签（连接成功后调用）；已有则复用并激活 */
    ensureTabFor(connectionId: string, db: string): SqlTab {
      const existing = this.sqlTabs.find((t) => t.connectionId === connectionId)
      if (existing) {
        this.setActiveMainTab(existing.id)
        return existing
      }
      return this.createTab(connectionId, db)
    },

    /** 新建一个 SQL 页签（"＋"按钮，同连接也可多开） */
    createTab(connectionId: string, db: string): SqlTab {
      const tab: SqlTab = {
        id: nextSqlTabId(),
        title: `SQL ${this.sqlTabs.length + 1}`,
        connectionId,
        db,
      }
      this.sqlTabs.push(tab)
      // 插入到日志页签之前（保持 SQL 页签在前的默认习惯，用户可拖拽调整）
      const logIdx = this.mainTabsOrder.indexOf('log')
      if (logIdx < 0) {
        this.mainTabsOrder.push(tab.id)
      } else {
        this.mainTabsOrder.splice(logIdx, 0, tab.id)
      }
      this.activeMainTabId = tab.id
      return tab
    },

    closeSqlTab(id: string): void {
      const idx = this.sqlTabs.findIndex((t) => t.id === id)
      if (idx < 0) return
      this.sqlTabs.splice(idx, 1)
      const orderIdx = this.mainTabsOrder.indexOf(id)
      if (orderIdx >= 0) this.mainTabsOrder.splice(orderIdx, 1)
      delete this.drafts[id]
      delete this.resultsByTab[id]
      delete this.activeResultByTab[id]
      if (this.activeMainTabId === id) {
        const nextId =
          this.mainTabsOrder[orderIdx] ??
          this.mainTabsOrder[orderIdx - 1] ??
          null
        this.activeMainTabId = nextId
      }
    },

    /** 激活某个主区页签（SQL 页签 id 或 'log'） */
    setActiveMainTab(id: string): void {
      this.activeMainTabId = id
    },

    /** 兼容入口：'sql' 激活当前 SQL 页签，'log' 激活日志（未在序列中则追加到末尾） */
    setMainTab(tab: 'sql' | 'log'): void {
      if (tab === 'log') {
        if (this.mainTabsOrder.indexOf('log') < 0) this.mainTabsOrder.push('log')
        this.activeMainTabId = 'log'
        return
      }
      const current = this.activeMainTabId
      const sqlId = current && current !== 'log' ? current : this.mainTabsOrder.find((i) => i !== 'log') ?? ''
      this.setActiveMainTab(sqlId)
    },

    /** 拖拽换位：把 fromId 移动到 toId 之前 */
    moveTab(fromId: string, toId: string): void {
      const order = this.mainTabsOrder
      const from = order.indexOf(fromId)
      const to = order.indexOf(toId)
      if (from < 0 || to < 0 || from === to) return
      order.splice(from, 1)
      order.splice(order.indexOf(toId), 0, fromId)
    },

    setTabDb(id: string, db: string): void {
      const tab = this.sqlTabs.find((t) => t.id === id)
      if (tab) tab.db = db
    },

    draft(tabId: string): string {
      return this.drafts[tabId] ?? ''
    },

    setDraft(tabId: string, text: string): void {
      this.drafts[tabId] = text
    },

    async execute(tabId: string, sql: string): Promise<void> {
      const tab = this.sqlTabs.find((t) => t.id === tabId)
      if (!tab) return
      if (this.executing) return
      if (!sql.trim()) {
        toast('error', zhCN.query.emptySql)
        return
      }
      this.executing = true
      const connectionName =
        useConnectionStore().items.find((c) => c.id === tab.connectionId)?.name ?? ''
      try {
        const resp = await executeQuery(tab.connectionId, {
          sql,
          maxRows: this.maxRows,
          database: tab.db ? tab.db : undefined,
        })
        // 重跑视为放弃旧改动（旧结果页签的编辑态一并清理）
        for (const old of this.resultsByTab[tabId] ?? []) delete this.edits[old.id]
        this.resultsByTab[tabId] = resp.statements.map((s) => toTab(s, resp.statements.length))
        const first = this.resultsByTab[tabId][0]
        this.activeResultByTab[tabId] = first ? first.id : null
        this.resultsRev += 1
        void appendHistory({
          sql,
          connectionName,
          database: tab.db,
          kind: resp.statements.length > 1 ? 'MULTI' : resp.statements[0].kind,
          elapsedMs: resp.elapsedMs,
          rowCount: historyRowCount(resp.statements),
          success: true,
          errorMessage: null,
        })
      } catch (e) {
        const message = e instanceof Error ? e.message : String(e)
        const errorTab: ResultTab = {
          id: nextResultId(),
          ordinal: 1,
          kind: 'ERROR',
          title: zhCN.query.errorTab,
          errorMessage: message,
        }
        this.resultsByTab[tabId] = [errorTab]
        this.activeResultByTab[tabId] = errorTab.id
        this.resultsRev += 1
        toast('error', message)
        void appendHistory({
          sql,
          connectionName,
          database: tab.db,
          kind: 'ERROR',
          elapsedMs: 0,
          rowCount: null,
          success: false,
          errorMessage: message,
        })
      } finally {
        this.executing = false
      }
    },

    /** 从后端缓存取一页（翻页 / 改每页行数共用）；结果过期时后端返回 1002 */
    async loadPage(tab: ResultTab, page: number, pageSize?: number): Promise<void> {
      const stmt = tab.statement
      if (!stmt?.queryId) return
      if (!this.activeMainTabId || this.activeMainTabId === 'log') return
      const owner = this.activeMainTabId
      try {
        const resp = await getQueryRows(stmt.queryId, page, pageSize ?? stmt.pageSize ?? 100)
        const current = this.resultsByTab[owner]?.find((t) => t.id === tab.id)
        if (!current?.statement) return
        current.statement.rows = resp.rows
        current.statement.page = resp.page
        current.statement.pageSize = resp.pageSize
      } catch (e) {
        toast('error', e instanceof Error ? e.message : String(e))
      }
    },

    async changePage(tab: ResultTab, page: number): Promise<void> {
      await this.loadPage(tab, page)
    },

    /** 改每页行数后回到第 1 页（spec-04 状态条） */
    async changePageSize(tab: ResultTab, pageSize: number): Promise<void> {
      await this.loadPage(tab, 1, pageSize)
    },

    // ===== 表数据编辑（spec-16） =====

    /** 结果页签的编辑态（懒创建） */
    editStateOf(resultTabId: string): EditState {
      if (!this.edits[resultTabId]) this.edits[resultTabId] = createState()
      return this.edits[resultTabId]
    },

    toggleEdit(resultTabId: string): void {
      const state = this.editStateOf(resultTabId)
      state.unlocked = !state.unlocked
    },

    /** 记录单元格改动（pk 为该行主键原值快照，供生成 WHERE） */
    recordCell(
      resultTabId: string,
      payload: {
        rowIndex: number
        column: string
        value: string | null
        original: string | null
        pk?: Record<string, string | null>
      },
    ): void {
      setCell(
        this.editStateOf(resultTabId),
        payload.rowIndex,
        payload.column,
        payload.value,
        payload.original,
        payload.pk,
      )
    },

    markRowDeleted(
      resultTabId: string,
      rowIndex: number,
      pk?: Record<string, string | null>,
    ): void {
      markDeleted(this.editStateOf(resultTabId), rowIndex, pk)
    },

    addEditRow(resultTabId: string): number {
      return addInsertRow(this.editStateOf(resultTabId))
    },

    discardEdits(resultTabId: string): void {
      discard(this.editStateOf(resultTabId))
    },

    /** 保存改动：一个事务提交后重跑当前 SQL 刷新结果 */
    async saveEdits(resultTabId: string): Promise<void> {
      const owner = this.sqlTabs.find((t) =>
        (this.resultsByTab[t.id] ?? []).some((r) => r.id === resultTabId),
      )
      const stmt = owner
        ? this.resultsByTab[owner.id]?.find((r) => r.id === resultTabId)?.statement
        : undefined
      if (!owner || !stmt?.editable) return

      let payload
      try {
        payload = buildPayload(this.editStateOf(resultTabId), stmt.editable, owner.db || undefined)
      } catch (e) {
        toast('error', e instanceof Error ? e.message : String(e))
        return
      }

      this.savingEdit = true
      try {
        const resp = await applyMutations(owner.connectionId, payload)
        toast('success', zhCN.edit.saved(resp.applied))
        const state = this.editStateOf(resultTabId)
        discard(state)
        state.unlocked = false
        await this.execute(owner.id, this.drafts[owner.id] ?? '')
      } catch (e) {
        // 失败保留改动（后端已整体回滚），用户可修正后重试
        toast('error', e instanceof Error ? e.message : String(e))
      } finally {
        this.savingEdit = false
      }
    },

    /** 请求编辑器在光标处插入文本（由 MonacoEditor 监听执行） */
    requestInsert(text: string): void {
      insertSeq += 1
      this.insertRequest = { text, seq: insertSeq }
    },
  },
})

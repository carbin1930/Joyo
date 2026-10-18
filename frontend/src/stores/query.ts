import { defineStore } from 'pinia'
import { executeQuery, getQueryRows } from '@/api/query'
import { toast } from '@/composables/use-toast'
import type { StatementResult } from '@/types/api'
import { zhCN } from '@/locales/zh-CN'

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
    maxRows: 1000,
    insertRequest: null as { text: string; seq: number } | null,
    resultsRev: 0,
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
      try {
        const resp = await executeQuery(tab.connectionId, {
          sql,
          maxRows: this.maxRows,
          database: tab.db ? tab.db : undefined,
        })
        this.resultsByTab[tabId] = resp.statements.map((s) => toTab(s, resp.statements.length))
        const first = this.resultsByTab[tabId][0]
        this.activeResultByTab[tabId] = first ? first.id : null
        this.resultsRev += 1
      } catch (e) {
        const message = e instanceof Error ? e.message : String(e)
        const tab: ResultTab = {
          id: nextResultId(),
          ordinal: 1,
          kind: 'ERROR',
          title: zhCN.query.errorTab,
          errorMessage: message,
        }
        this.resultsByTab[tabId] = [tab]
        this.activeResultByTab[tabId] = tab.id
        this.resultsRev += 1
        toast('error', message)
      } finally {
        this.executing = false
      }
    },

    async changePage(tab: ResultTab, page: number): Promise<void> {
      const stmt = tab.statement
      if (!stmt?.queryId || !stmt.pageSize) return
      if (!this.activeMainTabId || this.activeMainTabId === 'log') return
      const owner = this.activeMainTabId
      try {
        const resp = await getQueryRows(stmt.queryId, page, stmt.pageSize)
        const current = this.resultsByTab[owner]?.find((t) => t.id === tab.id)
        if (!current?.statement) return
        current.statement.rows = resp.rows
        current.statement.page = resp.page
      } catch (e) {
        toast('error', e instanceof Error ? e.message : String(e))
      }
    },

    /** 请求编辑器在光标处插入文本（由 MonacoEditor 监听执行） */
    requestInsert(text: string): void {
      insertSeq += 1
      this.insertRequest = { text, seq: insertSeq }
    },
  },
})

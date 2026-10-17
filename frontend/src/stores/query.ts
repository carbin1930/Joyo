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

let tabSeq = 1
function nextTabId(): string {
  return `tab-${tabSeq++}`
}

function toTab(s: StatementResult, total: number): ResultTab {
  const title =
    s.kind === 'SELECT'
      ? zhCN.query.resultTab(s.ordinal, total)
      : `${zhCN.query.messageTab(s.ordinal, total)} - ${s.kind}`
  return {
    id: nextTabId(),
    ordinal: s.ordinal,
    kind: s.kind,
    title,
    statement: s,
  }
}

/**
 * 查询编辑器状态（spec-03）：
 * - drafts：每个连接独立的编辑器草稿（内存态，切换连接不丢失）
 * - tabs：当前结果 Tab 集合（多语句 → 多 Tab；失败 → 错误 Tab）
 * - maxRows：行数限制下拉（默认 1000）
 */
export const useQueryStore = defineStore('query', {
  state: () => ({
    drafts: {} as Record<string, string>,
    executing: false,
    tabs: [] as ResultTab[],
    activeTabId: null as string | null,
    maxRows: 1000,
  }),

  actions: {
    draft(connectionId: string): string {
      return this.drafts[connectionId] ?? ''
    },

    setDraft(connectionId: string, text: string): void {
      this.drafts[connectionId] = text
    },

    async execute(connectionId: string, sql: string): Promise<void> {
      if (this.executing) return
      if (!sql.trim()) {
        toast('error', zhCN.query.emptySql)
        return
      }
      this.executing = true
      try {
        const resp = await executeQuery(connectionId, { sql, maxRows: this.maxRows })
        this.tabs = resp.statements.map((s) => toTab(s, resp.statements.length))
        this.activeTabId = this.tabs[0]?.id ?? null
      } catch (e) {
        const message = e instanceof Error ? e.message : String(e)
        const tab: ResultTab = {
          id: nextTabId(),
          ordinal: 1,
          kind: 'ERROR',
          title: zhCN.query.errorTab,
          errorMessage: message,
        }
        this.tabs = [tab]
        this.activeTabId = tab.id
        toast('error', message)
      } finally {
        this.executing = false
      }
    },

    /** 结果 Tab 翻页：服务端分页（queryId） */
    async changePage(tab: ResultTab, page: number): Promise<void> {
      const stmt = tab.statement
      if (!stmt?.queryId || !stmt.pageSize) return
      try {
        const resp = await getQueryRows(stmt.queryId, page, stmt.pageSize)
        stmt.rows = resp.rows
        stmt.page = resp.page
      } catch (e) {
        toast('error', e instanceof Error ? e.message : String(e))
      }
    },
  },
})

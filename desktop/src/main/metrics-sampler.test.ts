import { describe, expect, it } from 'vitest'
import { summarizeElectron, type MetricsTick } from './metrics-sampler'
import type { ProcessMetric } from 'electron'

/** 构造一个进程指标（仅取 summarizeElectron 用到的字段） */
function proc(pid: number, memKb: number, cpu: number): ProcessMetric {
  return {
    id: pid,
    pid,
    type: 'renderer',
    creationTime: 0,
    memory: { workingSetSize: memKb, peakWorkingSetSize: memKb, privateBytes: memKb },
    cpu: { percentCPUUsage: cpu, cumulativeCPUUsage: 0 },
  } as unknown as ProcessMetric
}

describe('summarizeElectron', () => {
  it('汇总进程数/内存/CPU', () => {
    const summary = summarizeElectron([proc(1, 40960, 0.7), proc(2, 40960, 0.7), proc(3, 10240, 0.7)])

    expect(summary).toEqual({ processCount: 3, memMb: 90, cpuPercent: 2.1 } satisfies MetricsTick['electron'])
  })

  it('空列表归零', () => {
    expect(summarizeElectron([])).toEqual({ processCount: 0, memMb: 0, cpuPercent: 0 })
  })

  it('CPU 保留 1 位小数防抖动', () => {
    const summary = summarizeElectron([proc(1, 51200, 0.12345), proc(2, 51200, 1.11111)])

    expect(summary.memMb).toBe(100)
    expect(summary.cpuPercent).toBe(1.2)
  })
})

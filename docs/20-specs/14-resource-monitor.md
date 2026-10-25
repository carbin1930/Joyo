# spec-14：资源监控

- 规格编号：spec-14
- 功能名称：资源监控（F14）
- 所属版本：v0.2
- 状态：草稿（待确认）
- 依赖 spec：spec-01（/health 轮询基础设施）

## 1. 功能描述

主窗口底部状态栏实时展示**应用全量进程**的资源占用：Electron 全部子进程（`app.getAppMetrics()`）+ Java 后端 JVM（`/health` 扩展返回堆内存与进程 CPU），Main 汇总后 IPC 推送（PRD F14）。用于发现"卡了/内存涨了"时定位是前端还是后端。

## 2. 界面设计

主窗口底部新增全局状态栏（高 24px，所有页面可见）：

- 左侧：后端状态点（绿=在线，灰=离线）+ 版本号
- 右侧：`Electron 3进程 · 128MB · CPU 2.1%　|　JVM 210/512MB · CPU 0.8%`
- hover 状态栏显示 tooltip 明细（各进程内存/CPU 列表）
- 后端离线时 JVM 段灰显为"后端离线"，不弹错误

## 3. 接口依赖

1. **后端**：`GET /health` data 扩展（rest-api.md 已预留）：

```json
{ "jvm": { "heapUsedMb": 210.5, "heapMaxMb": 512.0, "cpuLoad": 0.08 } }
```

- `heapUsedMb/heapMaxMb`：Runtime 各取 max/total-free，保留 1 位小数
- `cpuLoad`：`OperatingSystemMXBean.getProcessCpuLoad()`，Main 内保存上次采样值做增量；首次采样返回负数时输出 `null`

2. **Electron Main**：定时器每 3s 采样一次 → `app.getAppMetrics()` 汇总（内存取 workingSetSize 求和 KB→MB；CPU 取 percentCPUUsage 求和）→ `webContents.send('metrics:tick', payload)`：

```ts
interface MetricsTick {
  backendUp: boolean
  electron: { processCount: number; memMb: number; cpuPercent: number }
  jvm: { heapUsedMb: number | null; heapMaxMb: number | null; cpuLoad: number | null }
}
```

3. **渲染层**：preload 暴露 `onMetrics(cb)`；渲染层只接收不轮询。

## 4. 行为细节

1. 采样与 `/health` 启动轮询互相独立；启动轮询结束后才启动 3s 采样器。
2. 渲染层丢失 IPC（窗口重载）后由 Main 在下次 push 自然恢复，无需请求。
3. 数值四舍五入到整数 MB / 1 位小数百分比，避免高频重绘抖动；数值不变时组件不重渲染。
4. 复用既有 token 鉴权调用 `/health`。

## 5. 边界情况

| 场景 | 期望表现 |
|---|---|
| 后端启动中/离线 | backendUp=false，JVM 段灰显，点状态不打扰 |
| /health 请求失败 | 保持上次 JVM 数据 1 个周期，连续失败 2 次后置离线 |
| cpuLoad 首次为负 | 显示 `--` |
| 子进程数变化（GPU/渲染进程重启） | processCount 实时反映 |

## 6. 验收标准

- [ ] 应用运行时状态栏每 3s 刷新，数值与任务管理器趋势一致（±30%）
- [ ] 杀掉后端进程，状态栏 6 秒内变"后端离线"
- [ ] 重启后端（或重启应用）自动恢复在线与 JVM 数据
- [ ] 空闲 1 分钟内存数值不出现持续跳变（四舍五入生效）
- [ ] 状态栏在连接管理页与查询编辑页均可见

## 7. 扩展点（只设计不实现）

- 折线图历史曲线、告警阈值提醒（v1.0）；后端 GC 次数/线程数指标

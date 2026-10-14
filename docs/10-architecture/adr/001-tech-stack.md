# ADR-001：采用 Electron + Vue 3 + Spring Boot Sidecar 架构

> 状态：已采纳 | 日期：2026-10-15 | 决策人：项目作者

## 背景

Joyo 是一个 MySQL 桌面客户端，需要：跨平台窗口、复杂前端交互（代码编辑器、数据网格）、稳定的数据库连接与元数据处理能力。作者为 Java 背景，项目全程由大模型辅助开发。

## 备选方案对比

| 维度 | A. Electron + Vue3 + **Spring Boot**（已选） | B. Electron + Vue3 + Node(mysql2) | C. Tauri 2 + Vue3 + Rust |
|---|---|---|---|
| 后端生态 | 极强：HikariCP、JDBC 元数据、JSqlParser、EasyExcel、JSch 全是成熟轮子 | 中：mysql2 够用，SQL 解析/导出类库较弱 | 强但 Rust 生态学习成本高 |
| 安装包体积 | 大（~150MB，含裁剪 JRE） | 中（~100MB） | 最小（~15MB） |
| 内存占用 | 高（JVM + Chromium，~400MB） | 中 | 低 |
| 与作者技能匹配 | 最高（Java 熟练，可深度把关后端质量） | 中 | 低（Rust 门槛高，AI 生成 Rust 代码作者难以审查） |
| 对大模型开发的友好度 | 高：Java/Vue 语料充足，AI 生成质量稳定 | 高 | 中：Rust + SQLx 异步模型易出错 |
| 后端可复用性 | 同一后端可扩展为 Web 版 | 低 | 中 |

## 决策

采用 **方案 A**。核心权衡：接受体积与内存的代价，换取与作者技能栈的最大匹配度、Java 生态的成熟轮子、以及对"大模型写代码 + 人类审查"这一开发模式最友好的语言组合。

## 后果与缓解措施

| 代价 | 缓解措施 |
|---|---|
| 安装包 ~150MB | jlink 裁剪定制 JRE（约 40-60MB），打包流水线自动化（`scripts/`） |
| JVM 冷启动慢 | 启动画面 + 健康检查轮询；后端开启 CDS（Class Data Sharing）可选优化 |
| 双进程管理复杂度 | 生命周期机制收敛在 `desktop/src/main/sidecar.ts` 单一模块，见 `overview.md` 第 2 节 |
| 本地服务安全面 | 127.0.0.1 绑定 + 每次启动随机 Token，见 `overview.md` 第 4 节 |

## 与后续 ADR 的关系

- 数据存储设计见 `data-model.md`
- 若未来增加 Web 版，后端可直接复用（届时另立 ADR）

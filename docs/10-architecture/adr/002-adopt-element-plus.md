# ADR-002：前端引入 Element Plus 组件库

- 状态：已接受
- 日期：2026-10-19
- 关联：PRD 全部 UI 功能；docs/90-conventions/coding-frontend.md

## 背景

v0.1 前期约定全手写 CSS + CSS 变量（零依赖、可控）。随着功能增多（表单校验、下拉、右键菜单、消息、对话框），手写组件的样式细节成本持续增长，用户反馈"功能样式不明显"。

## 决策

引入 Element Plus 2.14，全量引入（app.use(ElementPlus) + 全量 CSS）：

- 桌面应用资源本地加载，体积不敏感（约 +1MB 未压缩 / 约 330KB gzip），无需按需导入插件配置
- 全量引入类型与组件开箱即用，测试环境（vitest node）无需额外配置
- 主题融合：main.css 中将 --el-color-primary 等 Element Plus 变量映射到 --joyo-* 设计变量，保持全局视觉一致

## 替换节奏（渐进，不一次性重写）

1. 已替换：对话框基座（BaseDialog 内部改为 el-dialog，对外 API 不变）、下拉框（el-select）、按钮（el-button）
2. 新功能一律优先使用 Element Plus 组件
3. 保留自绘：SQL 页签（需要拖拽排序与自定义形态）、结果网格（spec-04 引入 Tabulator，专业数据网格非 el-table 场景）、Monaco 编辑器
4. 待迁移：Toast（保留现有 use-toast，迁移需 vitest 切 jsdom 环境）、表单（ConnectionDialog 渐进迁移到 el-form）

## 后果

- 正面：交互细节（hover/focus/禁用/校验）开箱即用；暗色主题可借助 Element Plus 变量体系实现
- 负面：包体增加；全局样式注入需注意与自有变量的优先级
- 中性：页签拖拽、结果网格等专业化场景继续自研/专业库，Element Plus 不覆盖

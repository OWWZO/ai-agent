## 1. DeepSearch 对话卡片抽离

- [x] 1.1 梳理 `deep_search` 在 `extend/search/report` 三阶段需要的对话区展示数据，并抽离统一的视图模型或映射辅助逻辑
- [x] 1.2 从现有 `SearchListRenderer` 相关实现中提取可复用的搜索卡片展示能力，使其能够同时服务对话区与工作区
- [x] 1.3 保证共享展示层能够同时表达“正在搜索”加载态和“搜索完成”结果态，而不是只支持工作区结果列表

## 2. 对话区内联渲染接入

- [x] 2.1 调整 `ui/src/components/Dialogue/index.tsx`，让 `deep_search.extend` 与 `deep_search.search` 在主对话区直接渲染搜索卡片组件，而不是只显示紧凑摘要行
- [x] 2.2 为 `extend` 阶段补齐“正在搜索”标题、加载动画和查询分解卡片项，并保持与“搜索完成”阶段一致的整体视觉结构
- [x] 2.3 保持 `report` 阶段继续渲染总结内容，避免总结被错误塞进搜索卡片，同时确认右侧工作区不再是本需求的主可见入口

## 3. 回放与回归验证

- [x] 3.1 为 deep_search 对话区渲染补充测试或 fixture，覆盖 `extend -> search -> report` 的组件切换
- [x] 3.2 补充历史回放场景验证，确认 `deep_search.extend/search` 在重新加载会话后仍以对话区卡片形式展示
- [x] 3.3 运行前端构建与相关测试，确认本次改动未破坏现有对话流、deep_search 工作区辅助查看和历史恢复能力

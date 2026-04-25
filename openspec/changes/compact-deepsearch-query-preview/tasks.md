## 1. DeepSearch 视图模型重整

- [x] 1.1 梳理 `deep_search.extend/search/report` 当前在 `Dialogue`、`ActionPanel`、消息归一化中的数据流，明确左侧概览与右侧详情的职责边界
- [x] 1.2 调整 deep_search 的共享映射逻辑，分别产出“对话区紧凑查询预览模型”和“工作区来源详情模型”
- [x] 1.3 保持按子查询拆分的渲染粒度，使每个子查询都能成为独立的左侧入口和右侧详情项

## 2. 左侧紧凑预览与右侧详情联动

- [x] 2.1 调整 `ui/src/components/Dialogue/index.tsx`，让 `deep_search.extend` 在主对话区按子查询渲染上下堆叠的紧凑搜索预览组件
- [x] 2.2 为 `extend` 预览补齐“正在搜索”标题、加载动画与紧凑样式，同时避免在对话区展开真实来源列表
- [x] 2.3 调整 `deep_search.search` 的左侧完成态入口，确保点击后激活对应 task 并在右侧工作区展示该子查询的来源条目
- [x] 2.4 校正 `SearchListRenderer` / `ActionPanel` 的职责，只在工作区展示来源详情与空结果态，不再把详情内容铺到对话区
- [x] 2.5 保持 `report` 阶段继续渲染总结内容，不让总结态混入查询预览或工作区来源详情入口

## 3. 回放与回归验证

- [x] 3.1 为 deep_search 的紧凑查询预览、按子查询拆分和左侧点击联动右侧详情补充测试或 fixture
- [x] 3.2 补充历史回放验证，确认 `extend/search/report` 在重新加载会话后仍保持“左侧概览、右侧详情”的行为
- [x] 3.3 运行前端相关测试与构建，确认本次调整未破坏现有 deep_search 工作区查看、对话流和历史恢复能力

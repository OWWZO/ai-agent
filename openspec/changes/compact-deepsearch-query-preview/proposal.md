## Why

当前 `deep_search` 的展示重点放错了位置：用户真正想第一时间看到的是“查询分解完成后正在搜索哪些子问题”，而不是把完整搜索结果卡片堆在对话区里。现有方案把搜索完成态直接做进左侧对话流，会占用大量纵向空间，也弱化了右侧工作区作为详情查看面的价值。

这个需求现在需要重新收口，因为 `extend`、`search`、`report` 三阶段数据都已经存在，差的是前端如何分配“概览”和“详情”两种可见层级。把查询分解展示留在对话区，把真实搜索来源继续留在工作区，才能符合用户预期并保持界面密度可控。

## What Changes

- 在主对话区为 `deep_search.extend` 增加紧凑型搜索预览组件，只展示查询分解后的子查询项，不直接展示真实网页来源列表。
- 查询分解后的每个子查询在对话区中独立渲染为一个上下堆叠的搜索预览条目，延续“搜索完成”组件的视觉语言，但保持紧凑尺寸并带加载态。
- `deep_search.search` 阶段保持左侧为可点击的紧凑搜索组件/任务项，用于激活右侧工作区；真实来源条目继续只在右侧工作区展开查看。
- 明确 `extend -> search -> report` 的分层规则：`extend` 在对话区展示查询分解概览，`search` 在工作区展示来源详情并允许从左侧入口打开，`report` 继续渲染总结内容。
- 补充实时流式与历史回放验证，确保查询分解概览、工作区搜索结果入口和总结阶段的切换行为一致。

## Capabilities

### New Capabilities
- `deepsearch-query-preview`: 规范 deep_search 在对话区展示紧凑查询分解预览、在工作区展示搜索结果详情，以及两者之间的交互分工。

### Modified Capabilities

## Impact

- 前端对话区：`ui/src/components/Dialogue/index.tsx` 及其 deep_search 子项渲染逻辑。
- 工作区联动：`ui/src/components/ActionPanel/ActionPanel.tsx`、`SearchListRenderer.tsx` 与左侧点击激活右侧详情的交互链路。
- 消息归一化：`ui/src/utils/chat.ts`、`ui/src/services/agentConversation.ts` 中 deep_search 三阶段数据到对话区/工作区视图模型的映射。
- 验证：deep_search 的实时流式、历史回放、左侧入口激活右侧工作区的相关测试或 fixture。

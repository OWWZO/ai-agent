## Why

当前 `deep_search` 在查询分解完成后，前端虽然能感知到 `extend` 阶段，但对话区里只显示一条简短的“正在搜索/搜索完成”任务行，真正承载网页来源卡片的组件仍然只出现在右侧工作区。用户希望看到的是：在主对话区直接出现一个与“搜索完成”形态一致的搜索组件，只是处于“正在搜索”状态并带加载动画，这样无需切到右侧也能立即理解系统正在搜什么。

这个调整现在值得单独收口，因为当前已有 deep_search 阶段事件、查询分解数据和搜索结果卡片样式，缺口主要是展示位置与状态表达不符合预期。只要把现有视觉能力从工作区复用到对话区，就能明显提升主对话流的可读性。

## What Changes

- 在对话区为 `deep_search` 增加内联搜索状态组件，让 `extend` 阶段直接显示查询分解后的子查询内容。
- 复用现有“搜索完成”组件的视觉结构和来源卡片表现，将 `extend` 阶段改为“正在搜索”标题、加载动画和空结果占位，而不是只显示一行任务摘要。
- 明确 `extend -> search -> report` 在对话区中的状态切换规则：`extend` 显示搜索中组件，`search` 复用同一组件切换为搜索完成并填充来源卡片，`report` 再进入总结内容。
- 将右侧工作区降为辅助查看入口，本次不再把“查询分解内容是否可见”作为工作区主目标，重点放在对话区内联展示。
- 补充针对对话区 deep_search 渲染的验证，确保实时流式和历史回放都能看到一致的搜索中/搜索完成组件。

## Capabilities

### New Capabilities
- `deepsearch-dialogue-progress`: 规范 deep_search 在主对话区内联展示搜索中与搜索完成组件的行为、状态切换与查询分解内容呈现。

### Modified Capabilities

## Impact

- 前端对话区：`ui/src/components/Dialogue/index.tsx` 及其相关渲染辅助逻辑。
- 搜索组件复用：现有 `ui/src/components/ActionPanel/SearchListRenderer.tsx` 及 deep_search 内容归一化/映射逻辑，可能需要抽离为可同时服务对话区与工作区的通用展示层。
- 消息处理：`ui/src/utils/chat.ts`、`ui/src/services/agentConversation.ts` 中 deep_search 阶段与查询/文档数据归一化逻辑。
- 验证：补充针对对话区 deep_search 组件状态流转与历史回放的测试或 fixture。

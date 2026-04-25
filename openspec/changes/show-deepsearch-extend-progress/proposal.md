## Why

当前 `deep_search` 工具在 Python 侧已经会在 `query_decompose()` 完成后先输出 `messageType=extend`，但前端任务面板仍要等到后续真实检索完成后才出现可见反馈，导致 React 与 PlanSolve 模式下用户在 3 到 8 秒内只能看到“无结果等待”。这让深度搜索的阶段性进度被浪费，也削弱了用户对系统仍在工作的感知。

现在补齐这个能力的时机已经成熟，因为现有 Python、Java 持久化与历史回放链路都已识别 `extend/search/report` 阶段，缺口主要集中在前端渲染与交互约定，改动范围可控且能直接改善体验。

## What Changes

- 复用现有 `deep_search` 的 `extend` 阶段语义，在查询分解完成后立即让前端渲染“正在搜索”任务组件，而不是等 `search` 阶段返回文档后再展示。
- 在“正在搜索”组件中展示 `query_decompose()` 输出的子查询列表，使用户能实时看到系统将要检索的方向，即使此时 `docs` 仍为空。
- 明确 `extend -> search -> report` 三个阶段的前端状态切换规则，确保搜索中、搜索完成、总结中的视觉反馈一致且不会重复插入任务项。
- 补齐历史回放与已持久化事件的展示约定，保证重新打开会话时，`extend` 阶段事件仍能按统一规则被恢复和展示。
- 为深度搜索阶段渲染补充针对性验证，避免后续在消息归一化或任务列表聚合时再次把 `extend` 阶段过滤掉。

## Capabilities

### New Capabilities
- `deepsearch-progress-visibility`: 规范 deep_search 在查询分解、实际检索、结果总结三个阶段的前端可见反馈与状态流转。

### Modified Capabilities

## Impact

- 前端：`ui/src/utils/chat.ts`、`ui/src/components/Dialogue/index.tsx`、`ui/src/components/ActionView/FilePreview.tsx`、`ui/src/services/agentConversation.ts` 的 deep_search 阶段归一化与展示逻辑。
- 后端协议与工具：复用现有 `reactor-tool/reactor_tool/tool/deepsearch.py` 输出的 `messageType=extend/search/report`，原则上不新增接口字段。
- 会话历史：需确认现有会话事件回放、任务聚合与侧边面板展示对 `extend` 阶段保持一致表现。
- 测试与样例：补充 deep_search 阶段渲染或事件归一化相关测试/fixture，覆盖 React、PlanSolve 共用展示链路。

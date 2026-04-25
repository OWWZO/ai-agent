## 1. DeepSearch 阶段聚合

- [x] 1.1 调整 `ui/src/utils/chat.ts` 中 deep_search 任务列表聚合逻辑，让 `messageType=extend` 进入可见任务流而不是被过滤掉
- [x] 1.2 复查 `handleDeepSearchMessage`、`processDeepSearchTask` 与相关 task/tool 更新逻辑，确保 `extend -> search -> report` 始终更新同一条 deep_search 工具流，不产生重复占位任务
- [x] 1.3 校验 `ui/src/services/agentConversation.ts` 的阶段归一化逻辑，确保实时事件与历史事件都稳定解析为 `extend`、`search`、`report`

## 2. 前端展示联动

- [x] 2.1 调整 `ui/src/components/Dialogue/index.tsx` 的 deep_search 行内展示规则，让 `extend` 阶段立即显示“正在搜索”状态
- [x] 2.2 复查 `ui/src/components/ActionView/FilePreview.tsx` 与相关详情面板逻辑，保证 `extend` 阶段在 `docs` 为空时也能展示查询分解内容和正确标题
- [x] 2.3 验证 React 与 PlanSolve 共享对话链路下，deep_search 的查询分解内容都能在搜索未完成前被用户看到

## 3. 回归验证

- [x] 3.1 为 deep_search 阶段归一化或任务渲染补充测试/fixture，覆盖 `extend` 可见、`search` 完成、`report` 总结的阶段切换
- [x] 3.2 补充历史回放场景验证，确认持久化的 `deep_search.extend` 事件重新加载后仍展示“正在搜索”和子查询内容
- [x] 3.3 运行前端构建与相关测试，确认本次改动未破坏现有 deep_search、任务面板和会话回放能力

## ADDED Requirements

### Requirement: DeepSearch query decomposition progress SHALL be visible immediately
系统在收到 `deep_search` 的 `extend` 阶段事件后，必须立即展示深度搜索任务的“正在搜索”状态，不能等待后续 `search` 阶段返回真实文档后才首次显示该任务。

#### Scenario: Live extend event appears in the task panel
- **WHEN** React 或 PlanSolve 对话流收到 `messageType=extend` 的 `deep_search` 事件，且 `searchResult.query` 至少包含一个子查询
- **THEN** 前端必须立即创建或更新对应的 deep_search 任务项为“正在搜索”状态
- **THEN** 该任务项必须进入用户可见的任务列表或详情面板

#### Scenario: Extend stage remains visible even when docs are empty
- **WHEN** `deep_search.extend` 事件中的 `searchResult.docs` 全部为空数组
- **THEN** 前端仍必须展示“正在搜索”状态
- **THEN** 前端不得因为当前没有检索文档而隐藏该任务或误判为“搜索完成”

### Requirement: DeepSearch extend stage SHALL display decomposed sub-queries
系统在 `deep_search` 的 `extend` 阶段必须展示查询分解得到的子查询内容，让用户在真实搜索完成前即可看到系统准备检索的方向。

#### Scenario: Decomposed queries are shown during searching
- **WHEN** `deep_search.extend` 事件携带 `searchResult.query=[sub_query1, sub_query2, ...]`
- **THEN** 前端必须在“正在搜索”的展示组件中渲染这些子查询内容
- **THEN** 前端展示不得依赖 `docs` 已返回

#### Scenario: Query structure is preserved for later search results
- **WHEN** 某个 deep_search 任务先收到 `extend`，随后收到同一任务的 `search`
- **THEN** 前端必须沿用同一组子查询结构承接后续检索结果
- **THEN** 前端不得因为阶段切换而丢失、重排或错误拼接已有子查询文本

### Requirement: DeepSearch stage transitions SHALL remain consistent across live streaming and replay
系统必须对 `extend -> search -> report` 阶段使用一致的归一化和渲染规则，保证实时流式与历史回放看到的 deep_search 状态一致。

#### Scenario: Search stage updates the existing deepsearch task
- **WHEN** 同一 deep_search 工具流在 `extend` 之后收到 `messageType=search`
- **THEN** 前端必须把已有任务从“正在搜索”更新为“搜索完成”或对应完成态
- **THEN** 前端不得额外制造与原任务冲突的重复占位项

#### Scenario: Replay restores extend stage behavior
- **WHEN** 会话历史中存在 `deep_search` 的 `extend` 事件并被重新加载
- **THEN** 前端必须按与实时流式相同的规则恢复“正在搜索”状态和子查询展示
- **THEN** 历史回放后的 deep_search 阶段表现必须与实时接收时保持一致

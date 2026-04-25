## ADDED Requirements

### Requirement: DeepSearch extend stage SHALL render an inline search component in the dialogue area
系统在收到 `deep_search` 的 `extend` 阶段事件后，必须在主对话区直接渲染搜索中组件，而不能只显示一条工具摘要或要求用户切换到右侧工作区查看查询分解内容。

#### Scenario: Extend stage is visible in the assistant dialogue
- **WHEN** 对话流收到 `deep_search.extend` 事件，且 `searchResult.query` 包含至少一个子查询
- **THEN** 主对话区必须渲染一个可见的 deep_search 搜索中组件
- **THEN** 该组件必须展示查询分解后的子查询内容
- **THEN** 用户无需打开右侧工作区也能看到这些内容

#### Scenario: Extend stage uses loading presentation instead of completed presentation
- **WHEN** 对话区渲染 `deep_search.extend`
- **THEN** 组件标题或状态文案必须表达“正在搜索”而不是“搜索完成”
- **THEN** 组件必须包含明确的加载态视觉反馈

### Requirement: DeepSearch search stage SHALL reuse the same dialogue component structure
系统在 `deep_search` 的 `search` 阶段必须沿用与搜索中组件一致的对话区结构，只更新状态和内容，而不是切换回完全不同的消息形态。

#### Scenario: Search stage reuses the same card layout
- **WHEN** 同一 deep_search 工具流从 `extend` 进入 `search`
- **THEN** 对话区必须沿用同一类搜索组件容器
- **THEN** 组件状态必须从“正在搜索”切换为“搜索完成”
- **THEN** 子查询占位内容必须被真实搜索来源内容替换或补齐

#### Scenario: Search results are readable in the dialogue area
- **WHEN** `deep_search.search` 返回来源文档列表
- **THEN** 主对话区必须直接展示这些来源卡片或等价结果列表
- **THEN** 用户不应依赖右侧工作区作为唯一查看入口

### Requirement: DeepSearch report stage SHALL remain separate from the inline search component
系统在 `deep_search` 的 `report` 阶段必须切回总结内容渲染，而不是把总结继续堆叠在搜索中/搜索完成组件内部。

#### Scenario: Report stage exits the search-status component
- **WHEN** deep_search 进入 `report` 阶段
- **THEN** 对话区必须显示总结内容
- **THEN** 总结内容不得伪装成“正在搜索”或“搜索完成”卡片

### Requirement: Dialogue-area DeepSearch progress SHALL be consistent for live streaming and replay
系统必须对实时流式和历史回放使用一致的对话区 deep_search 渲染规则，保证 `extend/search/report` 三阶段表现一致。

#### Scenario: Replay restores extend-stage inline component
- **WHEN** 历史事件中存在 `deep_search.extend` 并被重新加载
- **THEN** 主对话区必须恢复搜索中组件和对应的查询分解内容
- **THEN** 回放结果不得退化为只有一条摘要文本

#### Scenario: Replay restores search-stage result component
- **WHEN** 历史事件中存在 `deep_search.search`
- **THEN** 主对话区必须恢复搜索完成组件及其来源列表
- **THEN** 该组件结构必须与实时流式展示保持一致

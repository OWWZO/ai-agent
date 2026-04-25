## ADDED Requirements

### Requirement: DeepSearch extend stage SHALL render compact query preview items in the dialogue area
系统在收到 `deep_search.extend` 后，必须在主对话区展示查询分解后的紧凑搜索预览项，而不是把查询分解内容只放在右侧工作区或只显示一条笼统摘要。

#### Scenario: Each decomposed query is rendered as an individual compact preview item
- **WHEN** 对话流收到 `deep_search.extend`，且 `searchResult.query` 包含多个子查询
- **THEN** 主对话区必须按子查询逐条渲染紧凑搜索预览项
- **THEN** 如果查询被拆成两条，主对话区必须显示上下两个独立组件
- **THEN** 每个组件必须直接展示对应的子查询内容

#### Scenario: Extend-stage preview remains compact and loading-oriented
- **WHEN** 主对话区渲染 `deep_search.extend`
- **THEN** 组件必须表达“正在搜索”或等价加载态
- **THEN** 组件必须沿用搜索组件视觉语言但保持紧凑尺寸
- **THEN** 组件不得在对话区直接展开真实网页来源列表

### Requirement: DeepSearch search stage SHALL keep source details in the workspace
系统在 `deep_search.search` 阶段必须把真实来源条目保留在右侧工作区展示，而不是直接在主对话区平铺来源详情。

#### Scenario: Search results are shown in the workspace instead of the dialogue stream
- **WHEN** `deep_search.search` 返回来源文档列表
- **THEN** 每个来源条目必须在右侧工作区展示
- **THEN** 主对话区不得直接渲染完整来源列表
- **THEN** 主对话区必须继续保持紧凑入口形态

#### Scenario: Empty search result still keeps the left preview and right empty state
- **WHEN** 某个子查询进入 `deep_search.search` 但没有匹配来源
- **THEN** 左侧主对话区仍必须保留对应的紧凑入口
- **THEN** 右侧工作区必须可展示空结果态

### Requirement: DeepSearch search preview SHALL activate workspace details on click
系统在 `deep_search.search` 阶段必须允许用户通过左侧对话区中的搜索完成入口，打开并查看右侧工作区中的对应来源详情。

#### Scenario: Clicking a completed preview opens the matching workspace detail
- **WHEN** 用户点击左侧对话区中的某个 `deep_search.search` 紧凑入口
- **THEN** 系统必须激活对应的 deep_search 子项
- **THEN** 右侧工作区必须展示该子项对应的来源条目列表

#### Scenario: Each decomposed query keeps an independent detail entry
- **WHEN** 一个 deep_search 请求被拆分为多个子查询并完成搜索
- **THEN** 每个子查询必须保留独立的左侧点击入口
- **THEN** 每个入口打开的右侧工作区内容必须与该子查询的来源结果一一对应

### Requirement: DeepSearch report and replay SHALL preserve the left-overview right-detail model
系统必须在 `report` 阶段以及历史回放中保持一致的职责边界：左侧对话区负责查询概览入口，右侧工作区负责来源详情。

#### Scenario: Report stage exits query preview mode
- **WHEN** deep_search 进入 `report` 阶段
- **THEN** 对话区必须切回总结内容渲染
- **THEN** 总结内容不得伪装成查询分解预览或搜索结果详情

#### Scenario: Replay restores compact previews and workspace-driven details
- **WHEN** 历史会话被重新加载，且包含 `deep_search.extend` 或 `deep_search.search`
- **THEN** `extend` 阶段必须恢复为左侧紧凑查询预览项
- **THEN** `search` 阶段必须恢复为左侧紧凑入口加右侧工作区详情的交互模型

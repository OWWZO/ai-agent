## ADDED Requirements

### Requirement: Users SHALL access a dedicated MRAG workspace for file-level knowledge base operations
系统必须提供一个独立的 MRAG Workspace 页面，用于围绕知识库和文件进行可视化操作，而不是要求用户通过命令行、接口工具或聊天主链路间接完成这些操作。

#### Scenario: User opens the dedicated workspace
- **WHEN** 用户进入 MRAG Workspace 入口
- **THEN** 系统必须展示一个独立页面
- **THEN** 页面必须至少包含知识库区域、文件区域和检索调试区域

#### Scenario: Workspace keeps MRAG concerns separated from chat runtime
- **WHEN** 用户在 MRAG Workspace 中执行知识库或检索操作
- **THEN** 系统不得要求用户先进入聊天会话
- **THEN** 系统不得修改现有聊天主链路的运行时行为作为前置条件

### Requirement: Users SHALL view and manage knowledge bases from the workspace
系统必须允许用户在 Workspace 中查看已有知识库、创建知识库并切换当前操作的目标知识库。

#### Scenario: Workspace loads existing knowledge bases
- **WHEN** 用户打开 MRAG Workspace
- **THEN** 系统必须展示可用知识库列表
- **THEN** 系统必须允许用户选择一个知识库作为当前工作对象

#### Scenario: User creates a new knowledge base
- **WHEN** 用户提交新的知识库名称及可选描述
- **THEN** 系统必须创建该知识库
- **THEN** 新知识库必须出现在知识库列表中
- **THEN** 系统必须允许用户立即切换到该知识库继续操作

### Requirement: Users SHALL ingest and inspect source files for the selected knowledge base
系统必须允许用户针对当前知识库添加本地文件或网页链接，并展示该知识库下的文件列表及其处理状态。

#### Scenario: User adds local files into the selected knowledge base
- **WHEN** 用户为当前知识库选择一个或多个本地文件并提交入库
- **THEN** 系统必须为这些文件发起入库任务
- **THEN** 文件列表中必须出现对应文件项
- **THEN** 每个文件项必须显示当前处理状态

#### Scenario: User adds a web page into the selected knowledge base
- **WHEN** 用户输入网页链接并提交到当前知识库
- **THEN** 系统必须为该链接发起入库任务
- **THEN** 文件列表中必须出现对应记录
- **THEN** 该记录必须显示当前处理状态

#### Scenario: File processing status is refreshed until completion
- **WHEN** 当前知识库存在处理中状态的文件
- **THEN** 系统必须刷新文件列表状态
- **THEN** 文件状态必须最终反映为成功或失败等终态

### Requirement: Users SHALL preview or download original source materials from the file list
系统必须为知识库文件列表中的资料提供原始资料访问入口，以便用户确认知识库实际收录了哪些文件或链接。

#### Scenario: Uploaded document can be previewed or downloaded
- **WHEN** 文件列表中的记录来源于上传的本地文件
- **THEN** 系统必须提供可访问该原始文件的入口
- **THEN** 如果该文件支持在线预览，系统必须提供预览能力
- **THEN** 系统必须提供下载原始文件的能力

#### Scenario: URL-based source can be opened from the file list
- **WHEN** 文件列表中的记录来源于网页链接入库
- **THEN** 系统必须提供打开原始链接的入口

### Requirement: Users SHALL run MRAG retrieval debugging against the selected knowledge base
系统必须允许用户针对当前选中的知识库输入问题并发起 MRAG 检索调试，查看流式结果和最终回答。

#### Scenario: Query uses the selected knowledge base context
- **WHEN** 用户在检索调试区输入问题并发起查询
- **THEN** 系统必须基于当前选中的知识库执行该查询

#### Scenario: Query result is streamed to the workspace
- **WHEN** MRAG 查询返回流式结果
- **THEN** 系统必须在页面中持续展示增量内容
- **THEN** 用户必须能够看到最终完成的回答结果

#### Scenario: Query errors are visible to the user
- **WHEN** MRAG 查询失败、超时或返回异常
- **THEN** 系统必须在检索调试区展示明确的失败提示
- **THEN** 系统不得让页面停留在无反馈的加载状态

### Requirement: First-phase workspace SHALL stay at file-level observability instead of chunk-level browsing
首期 MRAG Workspace 必须聚焦文件级资料可视化和查询调试，不得向用户承诺或伪造不存在的 chunk 明细浏览能力。

#### Scenario: Workspace does not present chunk detail views
- **WHEN** 用户查看知识库和文件列表
- **THEN** 系统必须展示文件级信息和状态
- **THEN** 系统不得展示未经后端稳定提供的 chunk、OCR、caption 或召回分数字段

#### Scenario: Query result view focuses on returned answers
- **WHEN** 用户完成一次 MRAG 查询
- **THEN** 系统必须展示本次查询返回的流式内容和最终结果
- **THEN** 系统不得把不存在的召回命中明细伪装成真实检索结果

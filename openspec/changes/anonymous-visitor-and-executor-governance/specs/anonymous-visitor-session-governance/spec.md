## ADDED Requirements

### Requirement: Browser conversation requests SHALL resolve a stable anonymous visitor identity
真实浏览器入口和会话相关 HTTP 接口 MUST 通过 `ai_agent_visitor_token` HttpOnly Cookie 解析匿名访客身份；当请求没有有效 token 时，系统 SHALL 创建新的匿名访客并回写新的 Cookie，而不是继续让前端自行声明访客身份。

#### Scenario: First browser request receives a new visitor cookie
- **WHEN** 浏览器首次访问 `/web/api/v1/gpt/queryAgentStreamIncr`、会话历史接口或其他受 visitor 保护的会话接口，且请求中没有有效的 `ai_agent_visitor_token`
- **THEN** 系统必须创建新的匿名访客身份并在当前请求上下文中绑定对应 `visitorId`
- **THEN** 响应必须回写 `ai_agent_visitor_token` Cookie，且包含 `HttpOnly`、`Path=/` 以及配置驱动的 `Secure` 与 `SameSite`

#### Scenario: Invalid visitor token is rotated instead of trusted
- **WHEN** 请求携带的 `ai_agent_visitor_token` 无法解析、已失效或找不到对应访客记录
- **THEN** 系统不得继续信任该 token
- **THEN** 系统必须创建新的匿名访客身份并回写新的 Cookie

### Requirement: Credentialed visitor cookies SHALL only be issued to configured same-site origins
当会话接口启用带凭证的浏览器访问时，系统 MUST 只对白名单 Origin 返回允许携带凭证的 CORS 响应，不能继续使用宽泛 `Origin` 模式与凭证同时放开。

#### Scenario: Whitelisted origin can use credentialed conversation APIs
- **WHEN** 请求来自配置中的前端 Origin，且浏览器访问会话相关 API 时携带凭证
- **THEN** 系统必须返回匹配该 Origin 的 CORS 允许头
- **THEN** 系统必须允许凭证参与请求和响应中的 Cookie 交换

#### Scenario: Non-whitelisted origin cannot receive credentialed CORS allowance
- **WHEN** 请求来自未配置的 Origin
- **THEN** 系统不得返回该 Origin 的凭证型 CORS 允许头
- **THEN** 系统不得为了兼容而退回到 `*` 或等价的宽泛模式

### Requirement: Conversation sessions SHALL be bound to the current visitor on first use and rejected on cross-visitor access
在前端继续自生成 `sessionId` 的前提下，后端 MUST 在会话首次进入真实对话主链路时建立 `visitorId -> sessionId` 归属绑定；后续任何会话访问都 SHALL 校验当前访客是否拥有该会话。

#### Scenario: First use binds session ownership
- **WHEN** 当前 `visitorId` 首次访问一个尚未绑定归属的 `sessionId`
- **THEN** 系统必须把该 `sessionId` 绑定到当前 `visitorId`
- **THEN** 后续同一访客继续访问该会话时必须视为合法访问

#### Scenario: Another visitor cannot reuse an existing session
- **WHEN** 某个 `sessionId` 已经绑定到 `visitor-001`，另一个 `visitor-002` 尝试用同一 `sessionId` 发起对话、读取历史或上传附件
- **THEN** 系统必须拒绝该访问
- **THEN** 系统不得把该会话重新绑定到新的 `visitorId`

### Requirement: Visitor identity SHALL be propagated through the browser entry and internal `/AutoAgent` relay
`/web/api/v1/gpt/queryAgentStreamIncr` 在构造真实对话请求时 MUST 把当前解析出的 `visitorId` 写入 `AgentRequest`，并在内部转发到 `/AutoAgent` 时继续透传；服务端当前请求上下文解析出的 `visitorId` MUST 优先于调用方伪造的访客字段。

#### Scenario: Browser entry forwards the resolved visitor identity
- **WHEN** 浏览器请求进入 `/web/api/v1/gpt/queryAgentStreamIncr`
- **THEN** 系统必须把当前请求解析得到的 `visitorId` 写入内部 `AgentRequest`
- **THEN** 内部 `/AutoAgent` 转发请求必须继续携带同一个 `visitorId`

#### Scenario: Caller-supplied visitor id cannot override the server-resolved identity
- **WHEN** 请求体中带有与当前请求上下文不一致的 `visitorId`
- **THEN** 系统必须以服务端当前请求上下文解析出的 `visitorId` 为准
- **THEN** 系统不得因为调用方伪造的 `visitorId` 绕过会话归属校验

### Requirement: Conversation history and file upload SHALL enforce visitor ownership before accessing session-scoped data
会话列表、详情回放和会话附件上传 MUST 在进入历史重放或文件落库逻辑之前完成 visitor 归属校验，避免知道别人 `sessionId` 的请求直接访问他人资源。

#### Scenario: Session list only returns sessions owned by the current visitor
- **WHEN** 当前匿名访客请求最近会话列表
- **THEN** 系统只能返回归属于当前 `visitorId` 的会话
- **THEN** 其他访客的会话不得出现在列表结果中

#### Scenario: Session detail is blocked before replay when ownership fails
- **WHEN** 当前访客请求的 `sessionId` 不属于自己
- **THEN** 系统必须在进入 `ConversationHistoryReplayService` 之前拒绝该请求
- **THEN** 系统不得回放或泄露该会话的任何历史内容

#### Scenario: File upload is rejected for non-owner sessions
- **WHEN** 当前访客向不属于自己的 `sessionId` 上传附件
- **THEN** 系统必须拒绝该上传请求
- **THEN** 系统不得为该附件创建会话关联记录

### Requirement: Dialogue session and run ledgers SHALL persist visitor identity and support visitor-scoped reads
真实对话主链路创建 `DialogueSession`、`DialogueRun` 和 execution ledger 查询结果时 MUST 显式记录 `visitorId`，并且所有会话列表、详情前置查询与 run 审计读取 SHALL 以 `visitorId` 为过滤条件之一。

#### Scenario: New session and run records capture visitor ownership
- **WHEN** 当前访客发起一次新的对话 run
- **THEN** 系统必须把该 `visitorId` 写入对应的 `DialogueSession` 和 `DialogueRun` 持久化记录
- **THEN** 后续审计和查询结果必须能够读取该 `visitorId`

#### Scenario: Ledger queries do not reveal another visitor's sessions
- **WHEN** `visitor-002` 查询最近会话列表或指定 `sessionId` 详情，而该会话归属于 `visitor-001`
- **THEN** 系统必须返回空结果或受控拒绝结果
- **THEN** 系统不得仅因为 `sessionId` 命中就返回别人的 ledger 视图

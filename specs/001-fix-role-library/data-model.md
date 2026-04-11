# Data Model: Fix 模式 AI 角色库

## 1. FixRole（只读投影）

来源：`ai_agent` + `ai_agent_flow_config` + 默认角色配置

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `agentId` | `string` | `ai_agent.agent_id` | 角色唯一标识 |
| `agentName` | `string` | `ai_agent.agent_name` | 角色名称 |
| `description` | `string` | `ai_agent.description` | 角色简介，前端角色列表直接复用 |
| `defaultRole` | `boolean` | 配置派生 | 是否为 chat 默认角色 |
| `sortIndex` | `number` | 查询派生 | 默认角色固定第一，其余按稳定规则排序 |
| `flowStepCount` | `number` | `ai_agent_flow_config` 聚合 | 可执行步骤数量，仅后端内部校验使用 |

### 业务约束

- `FixRole` 不是独立持久化实体，而是现有智能体数据的只读投影。
- 只有满足 Fix 角色入库规则的智能体才会出现在角色库中。
- `defaultRole=true` 最多只会有一个；配置缺失时由后端动态回退。

## 2. AgentConversation（扩展后）

来源：`ai_agent_conversation`

| 字段 | 类型 | 现状 | 变更 | 说明 |
|------|------|------|------|------|
| `id` | `Long` | 已有 | 不变 | 主键 |
| `sessionId` | `String` | 已有 | 不变 | 会话 UUID |
| `deviceId` | `String` | 已有 | 不变 | 设备标识 |
| `title` | `String` | 已有 | 不变 | 会话标题 |
| `agentType` | `Integer` | 已有 | 不变 | 0/1/2 |
| `productType` | `String` | 已有 | 不变 | `chat/html/docs/ppt/table/dataAgent` |
| `aiAgentId` | `String` | 新增 | 新增列 | chat 会话绑定的角色 ID |
| `aiAgentNameSnapshot` | `String` | 新增 | 新增列 | 创建/补齐时的角色名称快照 |
| `messageCount` | `Integer` | 已有 | 不变 | 消息轮数 |
| `lastMessagePreview` | `String` | 已有 | 不变 | 列表预览 |

### 业务约束

- 对于本特性上线后创建的 chat 会话，`aiAgentId` 必须有值。
- 对于非 chat 会话，`aiAgentId` 与 `aiAgentNameSnapshot` 允许为空。
- 对于历史 chat 会话，两字段允许为空；读取时按默认角色回退，继续发送时可懒补齐。
- 一段会话只能绑定一个角色；角色变更只能通过新会话实现。
- `aiAgentNameSnapshot` 仅用于历史展示，不作为执行事实源。

## 3. ConversationRoleView（接口返回对象）

用于 `会话列表 / 会话详情 / 角色面板当前值` 的统一角色摘要。

| 字段 | 类型 | 说明 |
|------|------|------|
| `agentId` | `string` | 会话当前绑定的角色 ID |
| `agentName` | `string` | 优先返回快照名，必要时回退在线角色名 |
| `available` | `boolean` | 角色当前是否仍满足 Fix 可执行条件 |
| `defaultRole` | `boolean` | 是否为当前默认 chat 角色 |

### 返回规则

- chat 会话必须返回 `role`；非 chat 会话返回 `null`。
- 当在线角色已失效时，`agentName` 仍显示快照名，但 `available=false`。

## 4. MessageSendCommand（发送请求扩展）

来源：`MessageSendReqVO`

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | `string` | 会话 ID |
| `requestId` | `string` | 本轮请求 ID |
| `query` | `string` | 用户消息 |
| `outputStyle` | `string` | 当前输出模式 |
| `deepThink` | `number` | 现有深度思考标记 |
| `filesJson` | `string` | 附件 JSON |
| `aiAgentId` | `string?` | 仅 chat 模式使用的目标角色 ID |

### 业务约束

- 已有 chat 会话发送时，`aiAgentId` 可以为空；若传值则必须与会话已绑定角色一致。
- 会话不存在时，`aiAgentId` 用于创建并绑定角色；若仍为空，则回退默认角色。
- 非 chat 模式忽略 `aiAgentId`。

## 5. 生命周期与状态转换

### A. 新建 chat 草稿

1. 前端创建本地草稿会话
2. 若用户未选角色，前端显示默认角色
3. 若用户在空白草稿中改选角色，仅更新草稿元数据，不影响其他会话

### B. 首次发送消息

1. 后端根据 `sessionId + aiAgentId + 默认角色` 解析最终绑定角色
2. 若远端会话还不存在，则创建 `ai_agent_conversation`
3. 持久化 `aiAgentId + aiAgentNameSnapshot`
4. `FixedAgentExecuteStrategy` 基于绑定角色读取 FlowConfig 执行

### C. 继续对话

1. 根据 `sessionId` 读取会话绑定角色
2. 若请求中 `aiAgentId` 不一致，拒绝本次请求并提示新建会话
3. 若角色仍可用，沿用原角色继续对话

### D. 角色失效

1. 历史会话列表/详情仍可打开
2. `ConversationRoleView.available=false`
3. 继续发送时返回结构化终态错误，不进入 Fix 执行链

## 6. 兼容性策略

- **历史会话兼容**: 已存在 chat 会话即使没有 `aiAgentId`，也能通过默认角色继续工作。
- **默认角色回退**: 若默认角色配置暂未设置，角色库服务自动回退到第一个可用 Fix 角色。
- **执行事实源**: 真正驱动 Fix 执行的仍是会话绑定的 `aiAgentId`，而不是前端展示字段。

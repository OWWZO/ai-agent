# Data Model: 对话历史持久化精简重构

## 1. ConversationSummary (`ai_agent_conversation`)

- **Purpose**: 作为历史侧栏和会话归属管理的轻量摘要，不承担详细回放职责。
- **Fields**:
  - `id`: 主键
  - `sessionId`: 前端会话唯一标识，用户级可读 ID
  - `deviceId`: 匿名设备归属
  - `userId`: 登录用户归属，可空
  - `title`: 会话标题
  - `agentType`: 会话模式（`CHAT / PLAN_SOLVE / REACT`）
  - `productType`: 展示形态（`chat/html/docs/ppt/table`）
  - `aiAgentId`: chat 角色绑定 ID
  - `aiAgentNameSnapshot`: 角色名称快照
  - `messageCount`: 单轮消息数
  - `pinned`: 是否置顶
  - `lastMessagePreview`: 最近一轮摘要
  - `createTime` / `updateTime` / `deleted`
- **Validation**:
  - `sessionId` 全局唯一
  - `deviceId` 或 `userId` 至少要能确定一个归属维度
  - 列表查询只允许返回归属匹配的数据

## 2. ConversationTurn (`ai_agent_message`)

- **Purpose**: 表示一次用户请求及其完成状态，是事件流的父级账本。
- **Fields**:
  - `id`: 主键
  - `conversationId`: 所属会话 ID
  - `requestId`: 单轮请求唯一标识
  - `sortOrder`: 会话内顺序
  - `query`: 用户输入
  - `filesJson`: 输入附件
  - `agentType`: 执行模式
  - `response`: 单轮最终回答/上下文文本，仅用于 chat 上下文和少量派生展示，不作为 rich replay 数据源
  - `status`: `STREAMING / COMPLETED / ERROR / FORCE_STOPPED`
  - `forceStop`: 是否强制停止
  - `metricsJson`: request 级执行指标
  - `startedAt` / `finishedAt`
  - `createTime` / `updateTime` / `deleted`
- **Validation**:
  - `requestId` 全局唯一
  - 同一 `conversationId` 下 `sortOrder` 单调递增
  - 不再保存 `thought/plan/tasks/render snapshot` 等会话细节字段

## 3. ReplayEvent (`ai_agent_message_event`)

- **Purpose**: 历史回放的唯一权威来源，按顺序承载 thought、task、tool、summary、result 等节点。
- **Fields**:
  - `id`: 主键
  - `messageId`: 所属 turn
  - `seqNo`: turn 内事件顺序
  - `eventType`: 事件大类，如 `plan_thought`、`task`、`tool_result`、`result`
  - `eventSubType`: 事件子类，如 `search`、`report`
  - `displayArea`: 事件展示区域
  - `taskId`: 关联任务标识，可空
  - `taskOrder`: 任务内顺序，可空
  - `title`: 展示标题
  - `contentText`: 可直接展示的短文本；不承载大体量工作区正文
  - `payloadJson`: 结构化扩展内容，包含前端展示所需 metadata、artifactRefs、未来新增展示字段
  - `isFinal`: 是否终态事件
  - `status`: `completed / partial / error`
  - `startedAt` / `endedAt`
  - `createTime` / `deleted`
- **Validation**:
  - `(messageId, seqNo)` 唯一
  - 只通过 `messageId` 建立父子关系，不再额外冗余 `conversationId/sessionId/requestId`
  - `payloadJson` 必须允许前向扩展，避免新显示类型再次改表

## 4. ArtifactReference (embedded in `ReplayEvent.payloadJson`)

- **Purpose**: 表示事件引用的稳定外部内容，如搜索总结、报告、工作区生成文件。
- **Fields**:
  - `artifactType`: `report/html/markdown/file/search_summary/...`
  - `displayName`: 前端展示名称
  - `resourceKey`: 稳定资源标识（对象 key、文件服务主键或等价稳定定位符）
  - `downloadUrl`: 稳定下载地址
  - `previewUrl`: 预览地址，可空
  - `fileSize`: 文件大小，可空
  - `mimeType`: 类型，可空
  - `missing`: 是否已不可读取
  - `missingReason`: 缺失原因，可空
- **Validation**:
  - 不允许使用工作区临时路径作为唯一定位信息
  - 缺失时必须能返回明确状态，供前端展示

## 5. FrontendConversationSummaryState

- **Purpose**: 前端内存中的服务端摘要列表镜像。
- **Fields**:
  - `sessionId`
  - `title`
  - `productType`
  - `deepThink`
  - `role`
  - `createdAt` / `updatedAt`
  - `messageCount`
  - `lastMessagePreview`
- **Rule**:
  - 只来源于服务端列表接口
  - 不夹带详情 `chatList`

## 6. FrontendConversationDetailCache

- **Purpose**: 只在用户打开某个历史会话时缓存 turn + events 详情。
- **Fields**:
  - `sessionId`
  - `turns[]`
  - `loadedAt`
  - `version`
- **Rule**:
  - 不参与历史列表排序
  - 可按需失效重拉

## 7. FrontendDraftConversation

- **Purpose**: 保存未发送草稿和流式中的本地运行态。
- **Fields**:
  - `sessionId`
  - `inputInfo`
  - `chatList`
  - `streamingThoughtMap`
  - `loading`
- **Rule**:
  - 不等价于服务端历史会话
  - 仅在本地承接草稿与进行中消息

## Relationships

- `ConversationSummary` 1:N `ConversationTurn`
- `ConversationTurn` 1:N `ReplayEvent`
- `ReplayEvent` 0:N `ArtifactReference`
- `FrontendConversationSummaryState` 与服务端 `ConversationSummary` 一一对应
- `FrontendConversationDetailCache` 按 `sessionId` 关联 `ConversationSummary`
- `FrontendDraftConversation` 可以与服务端 `ConversationSummary` 同 sessionId 共存，但职责不同

## State Transitions

### ConversationTurn Status

- `STREAMING` → `COMPLETED`
- `STREAMING` → `ERROR`
- `STREAMING` → `FORCE_STOPPED`

### ReplayEvent Status

- `partial`：流式中或中断前片段
- `completed`：事件和引用都可正常回放
- `error`：事件本身失败或引用不可读取

### Frontend Cache Flow

- 打开历史页面：加载 `ConversationSummary` 列表
- 选中某个会话：懒加载 `ConversationDetailCache`
- 新建未发送对话：只创建 `FrontendDraftConversation`
- 首次发送成功：草稿状态并入服务端 turn/event 详情

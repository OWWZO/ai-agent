# 会话记忆压缩与上下文对话记忆简化设计

## 背景

当前 `AgentStreamPersistServiceImpl`（1890行）同时承担 HTTP 客户端、SSE 流处理、事件投影转换、消息持久化、会话守卫等多重职责。`SessionTurnMemory` 同时保留 `blocks`（新结构化）和 `userMessage`/`assistantMessage`/`finalAnswer`（旧扁平）两套表示，导致大量 try-catch fallback 兼容逻辑。`ConversationEventPayloadNormalizer` 中多层 `resultMap` 嵌套解析进一步增加了代码复杂度。

旧数据可直接删除，所有兼容代码均可彻底移除。

---

## 目标

1. 删除全部兼容代码和兼容字段
2. 拆分 `AgentStreamPersistServiceImpl` 为职责单一的组件
3. 简化表结构，删除无意义字段
4. 每个类控制在 300-500 行以内，确保可独立测试

## 非目标

- 不改 Agent 执行引擎核心逻辑（Planning/ReAct/Executor 主循环）
- 不改 SSE 流式协议和前端交互格式
- 不引入新功能，只做简化和重构

---

## 表结构变更

### ai_agent_session_memory

| 变更 | 字段 | 原因 |
|------|------|------|
| 删除 | `facts_json` | 注释明确标注"兼容性 facts 投影 JSON"，旧数据可删 |
| 删除 | `boundary_message_id` | `boundary_sort_order` 已足够定位压缩边界，message_id 为自增主键不如 sort_order 稳定 |

### ai_agent_message

| 变更 | 字段 | 原因 |
|------|------|------|
| 保留（角色调整） | `generated_files_json` | 由事件聚合后写入的只读缓存，避免每次读取扫描事件表。代码中不再作为"兜底" |

### ai_agent_message_event

无变更。`structured_data_json`（标准化）与 `payload_json`（扩展）的区分有意义，保留。

---

## 领域模型变更

### SessionTurnMemory

删除以下兼容字段：

- `userMessage` — 旧版扁平表示
- `assistantMessage` — 旧版扁平表示
- `finalAnswer` — 旧版扁平表示

`blocks: List<TranscriptContextBlock>` 成为唯一的 transcript 表示。

保留字段：
- `messageId`、`requestId`、`sortOrder` — 定位信息
- `blocks` — 有序 transcript 块
- `artifactRefs` — 本轮聚合的产物引用

### SessionWorkingMemory

无字段变更。删除内部 fallback 逻辑即可。

---

## 代码拆分方案

### AgentStreamPersistServiceImpl → 3个组件

#### 1. StreamExecutor（~250行）

纯技术层。职责：HTTP 请求构建、SSE 流读取、逐行数据分发。

```
方法：
- execute(AgentRequest, StreamCallback) → 发起异步流
- buildHttpRequest(AgentRequest) → Request

回调接口 StreamCallback：
- onHeartbeat(GptProcessResult)
- onAgentResponse(AgentResponse, GptProcessResult)
- onFinished()
- onError(IOException)
```

#### 2. EventProjector（~500行）

领域层。职责：AgentResponse → OrderedEvent 投影转换。

```
方法：
- project(AgentResponse, AtomicInteger seqCounter) → List<OrderedEvent>
```

包含当前所有 `resolveXxx`/`buildXxx`/`extractXxx` 投影逻辑。由于删除兼容逻辑，不再使用 `findRaw`/`findNestedRaw`/`candidateObjects` 等多层 JSON 扒字段方法。直接按 AgentResponse 标准结构读取。

#### 3. PersistCoordinator（~200行）

协调层。职责：流结束后的统一持久化。

```
方法：
- persistTurn(Long messageId, AgentConversation conversation,
              List<OrderedEvent> events, String response, String thought,
              String status)
```

内部调用 `messageService`、`messageEventService`、`conversationDao`。

### 记忆相关类简化

| 类 | 简化内容 |
|------|------|
| `SessionWorkingMemoryAssembler` | 删除 `buildRecentTurns` try-catch fallback；删除 `parseFacts` |
| `SessionMemoryCompactionService` | 删除 `toTurnMemories` 退化逻辑；删除 `parseFacts`；`CompactionResult` 删除 `factsJson` 字段及赋值 |
| `SessionTranscriptBlockAssembler` | `generated_files_json` 不再作为兜底，仅保留事件驱动的 block 构建逻辑 |
| `ConversationEventPayloadNormalizer` | 删除多层 `resultMap` 兼容解析；删除 legacy `fileInfo`/`fileList` 处理；只处理标准化单层结构 |
| `SessionArtifactRestoreSupport` | 删除字段别名兼容（`fileName`\|`name`、`ossUrl`\|`downloadUrl`\|`url` 等）；统一使用标准字段名 |

---

## 数据流

```
[前端请求]
    │
    ▼
[AgentStreamPersistServiceImpl] ──→ 会话解析/守卫检查
    │
    ├──→ [SessionMemoryService] ──→ 工作记忆准备/压缩
    │       │
    │       └──→ [SessionWorkingMemoryAssembler]
    │       └──→ [SessionMemoryCompactionService]
    │
    ├──→ 构建 AgentRequest（含 working memory）
    │
    ├──→ [StreamExecutor.execute] ──→ HTTP/SSE 流
    │       │
    │       └──→ 逐行回调
    │               │
    │               ├──→ [EventProjector.project] ──→ OrderedEvent
    │               │
    │               └──→ 发送前端
    │
    └──→ 流结束 ──→ [PersistCoordinator.persist] ──→ DB
```

---

## 关键决策

### 1. 保留 `generated_files_json` 作为缓存

事件账本已包含所有产物引用，但从事件实时聚合有性能代价。保留 `generated_files_json` 作为流结束时一次性聚合的只读缓存，读取时直接取用。

### 2. EventProjector 直接读取标准结构

AgentResponse 是内部协议，结构可控且稳定。投影器不再做多层嵌套兼容解析，直接按标准字段路径读取。

### 3. 删除 `boundary_message_id`

`boundary_sort_order` 是唯一实际用于筛选近期消息的条件。`boundary_message_id` 从未参与业务逻辑判断。

### 4. 熔断器逻辑保留在 AgentSessionMemoryServiceImpl

熔断器（guardrail）是压缩失败后的降级保护，与压缩本身正交，保留在原位置。

---

## 删除清单

### 方法删除

| 所在类 | 删除方法 | 原因 |
|--------|----------|------|
| `AgentStreamPersistServiceImpl` | `projectFinalDetailEvents` 及全部相关私有方法 | 提取到 EventProjector |
| `AgentStreamPersistServiceImpl` | `buildWorkingMemoryMessages` fallback 分支 | blocks 是唯一表示 |
| `AgentStreamPersistServiceImpl` | `appendFallbackTurnMessages` | blocks 是唯一表示 |
| `SessionWorkingMemoryAssembler` | `buildRecentTurns` try-catch fallback | blocks 是唯一表示 |
| `SessionMemoryCompactionService` | `toTurnMemories` try-catch fallback | blocks 是唯一表示 |
| `SessionMemoryCompactionService` | `parseFacts` | facts_json 字段删除 |
| `SessionWorkingMemoryAssembler` | `parseFacts` | facts_json 字段删除 |
| `ConversationEventPayloadNormalizer` | 多层 resultMap 遍历 | 删除兼容逻辑 |
| `SessionArtifactRestoreSupport` | 字段别名兼容 | 统一标准字段名 |

### 字段删除

| 所在类 | 删除字段 | 原因 |
|--------|----------|------|
| `SessionTurnMemory` | `userMessage` | 旧版兼容 |
| `SessionTurnMemory` | `assistantMessage` | 旧版兼容 |
| `SessionTurnMemory` | `finalAnswer` | 旧版兼容 |
| `CompactionResult` | `factsJson` | facts_json 字段删除 |

### 数据库字段删除

| 所在表 | 删除字段 |
|--------|----------|
| `ai_agent_session_memory` | `facts_json` |
| `ai_agent_session_memory` | `boundary_message_id` |

---

## 风险与应对

| 风险 | 应对 |
|------|------|
| 删除兼容字段后旧代码引用报错 | 编译期即可发现，一并修改 |
| `EventProjector` 对 AgentResponse 结构假设不成立 | AgentResponse 是内部 DTO，结构完全可控 |
| `generated_files_json` 缓存与事件账本不一致 | 缓存由事件聚合后写入，写入前事件已确定 |
| 测试用例大量依赖旧字段 | 旧数据可删，测试同步更新 |

---

## 文件变更清单

### 修改

- `AgentStreamPersistServiceImpl.java` — 精简为协调层，调用 StreamExecutor/EventProjector/PersistCoordinator
- `AgentSessionMemoryServiceImpl.java` — 删除 facts 相关逻辑
- `SessionWorkingMemoryAssembler.java` — 删除 fallback 和 parseFacts
- `SessionMemoryCompactionService.java` — 删除 fallback 和 factsJson
- `SessionTranscriptBlockAssembler.java` — 删除 generated_files_json 兜底逻辑
- `ConversationEventPayloadNormalizer.java` — 删除多层嵌套兼容
- `SessionArtifactRestoreSupport.java` — 删除字段别名兼容
- `AgentSessionMemory.java`（entity）— 删除 factsJson、boundaryMessageId
- `SessionTurnMemory.java` — 删除 userMessage、assistantMessage、finalAnswer
- `CompactionResult.java`（内部类）— 删除 factsJson

### 新增

- `StreamExecutor.java`
- `EventProjector.java`
- `PersistCoordinator.java`

### SQL 迁移

- `Vxxx__drop_session_memory_compat_columns.sql`

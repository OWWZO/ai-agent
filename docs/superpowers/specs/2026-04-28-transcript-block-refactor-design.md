# 会话历史持久化重构设计 —— TranscriptBlock 扁平化方案

## 1. 背景与目标

### 1.1 当前问题

当前 `reactor` 包下的会话历史持久化存在严重的兼容逻辑堆积：

- **数据转换链过长**（6+层）：`AgentResponse → OrderedEvent → AgentMessageEvent → SessionTurnMemory → TranscriptContextBlock → String`
- **5个专门的兼容类**：`EventProjector`、`ConversationEventPayloadNormalizer`、`ConversationEventFactSupport`、`SessionArtifactRestoreSupport`、`SessionTranscriptBlockAssembler`
- **表结构设计缺陷**：
  - `ai_agent_message` 的 `files_json`/`generated_files_json` 与 `ai_agent_message_event` 的 `artifact_refs_json` 同时存储文件信息，导致恢复时需要多源合并+去重
  - `ai_agent_message_event` 的 `payload_json`/`structured_data_json`/`artifact_refs_json` 三个 JSON 字段职责模糊，互相兜底
  - 事件类型无约束，`EventProjector` 硬编码 10+ 种 `messageType` 映射

### 1.2 设计目标

1. **彻底去除兼容逻辑**：新表新结构，不兼容旧数据
2. **缩短数据转换链**：从 6 层降至 3 层
3. **表结构职责单一**：每个字段有且只有一种用途
4. **类型约束前置**：数据库/枚举层面约束，无运行时兜底

### 1.3 约束条件

- 只处理 LLM 上下文构建链路（链路 1），前端历史回放（链路 2）后续独立重构
- 保留压缩摘要机制
- 未知消息类型直接抛异常，不兜底

---

## 2. 新表结构设计

### 2.1 ai_agent_turn（替换 ai_agent_message）

```sql
CREATE TABLE ai_agent_turn (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    request_id      VARCHAR(64)  NOT NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    query           TEXT         NOT NULL,
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0=流式中 1=完成 2=错误 3=停止',
    started_at      DATETIME     NULL,
    finished_at     DATETIME     NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_id (request_id),
    UNIQUE KEY uk_conversation_sort (conversation_id, sort_order)
) ENGINE=InnoDB COMMENT='对话轮次';
```

**删除的字段**：`files_json`、`generated_files_json`、`agent_type`、`response`、`metrics_json`、`force_stop`

**为什么可以删除**：
- `files_json` → 用户上传的文件归入第一个 `USER_INPUT` block 的 `artifact_refs`
- `generated_files_json` → 生成的文件归入 `ARTIFACT_REFERENCE` block
- `response` → 助手最终回答归入 `ASSISTANT_ANSWER` block
- `agent_type` → 会话级别统一，不需要每轮重复

### 2.2 ai_agent_transcript_block（替换 ai_agent_message_event）

```sql
CREATE TABLE ai_agent_transcript_block (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    turn_id         BIGINT       NOT NULL,
    seq_no          INT          NOT NULL COMMENT '轮内顺序',
    block_type      VARCHAR(32)  NOT NULL COMMENT 'USER_INPUT|ASSISTANT_THOUGHT|TOOL_USE|TOOL_RESULT|ARTIFACT_REFERENCE|ASSISTANT_ANSWER',
    role            VARCHAR(16)  NULL COMMENT 'user|assistant',
    text            MEDIUMTEXT   NULL,
    tool_use_id     VARCHAR(128) NULL,
    tool_name       VARCHAR(128) NULL,
    tool_arguments  JSON         NULL COMMENT '工具参数',
    result_payload  JSON         NULL COMMENT '工具结果',
    artifact_refs   JSON         NULL COMMENT '产物引用',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_turn_seq (turn_id, seq_no),
    KEY idx_turn (turn_id, deleted, seq_no)
) ENGINE=InnoDB COMMENT='对话语义块';
```

**删除的字段**：`event_sub_type`、`display_area`、`task_id`、`task_order`、`title`、`content_text`、`reference_only`、`status`、`payload_json`、`structured_data_json`

**为什么可以删除**：
- `payload_json` / `structured_data_json` → 职责拆分到明确的列
- `event_sub_type` → `block_type` 足够表达语义
- `display_area` / `task_id` / `task_order` → 前端展示字段，链路 1 不需要
- `title` / `content_text` → 统一为 `text`
- `reference_only` → 大内容不存就是 reference，不需要额外标记

### 2.3 ai_agent_session_memory（保留简化）

```sql
CREATE TABLE ai_agent_session_memory (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id     BIGINT       NOT NULL,
    session_id          VARCHAR(64)  NOT NULL,
    boundary_sort_order INT          NOT NULL DEFAULT -1,
    summary_text        MEDIUMTEXT   NULL,
    artifact_refs       JSON         NULL,
    source_turn_count   INT          NOT NULL DEFAULT 0,
    last_compacted_at   DATETIME     NULL,
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conversation_boundary (conversation_id, deleted, boundary_sort_order)
) ENGINE=InnoDB COMMENT='会话记忆快照';
```

### 2.4 artifact_refs 标准格式（所有表统一）

```json
[
  {
    "type": "file",
    "name": "report.pdf",
    "url": "https://cdn.example.com/report.pdf",
    "previewUrl": "https://cdn.example.com/report.pdf?preview=1",
    "mimeType": "application/pdf",
    "size": 102400
  }
]
```

不再有 `ossUrl`/`domainUrl`/`resourceKey` 多字段，不再有 `fileInfo`/`fileList` 遗留格式。

---

## 3. 领域模型

### 3.1 TranscriptBlockType（枚举约束）

```java
public enum TranscriptBlockType {
    USER_INPUT,           // 用户输入（含上传文件引用）
    ASSISTANT_THOUGHT,    // 助手思考过程
    TOOL_USE,             // 工具调用声明
    TOOL_RESULT,          // 工具执行结果
    ARTIFACT_REFERENCE,   // 产物引用（文件、图片等）
    ASSISTANT_ANSWER      // 助手最终回答
}
```

只有这 6 种，没有"其他"。如果上游 Agent 产生无法映射的消息，**直接抛异常**，不兜底。

### 3.2 TranscriptBlock（核心实体）

```java
@Data
public class TranscriptBlock {
    private Long id;
    private Long turnId;
    private Integer seqNo;
    private TranscriptBlockType blockType;
    private String role;           // "user" | "assistant"
    private String text;
    private String toolUseId;      // TOOL_USE / TOOL_RESULT 用
    private String toolName;       // TOOL_USE 用
    private String toolArgumentsJson;
    private String resultPayloadJson;
    private String artifactRefsJson;
}
```

每个字段有且只有一种用途，没有"扩展字段"。

### 3.3 Turn（轮次元数据）

```java
@Data
public class Turn {
    private Long id;
    private Long conversationId;
    private String requestId;
    private Integer sortOrder;
    private String query;
    private TurnStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}

public enum TurnStatus {
    STREAMING(0), COMPLETED(1), ERROR(2), STOPPED(3)
}
```

### 3.4 模型关系

```
Conversation (1)
  ├── Turn (N) —— 只存元数据
  │     └── TranscriptBlock (N) —— 按 seq_no 排序
  │
  └── SessionMemory (N) —— 压缩快照
```

---

## 4. 数据流设计

### 4.1 写入流（Agent 流式响应 → 持久化）

```
AgentResponse (流式消息)
  ↓
TranscriptBlockMapper.map(response)
  ↓ 直接映射，没有投影概念
List<TranscriptBlock>
  ↓
TurnWriter.save(turn) + TranscriptBlockWriter.batchInsert(blocks)
  ↓
数据库
```

**原始类型 → BlockType 归类映射**：

| 原始流式类型 | 语义角色 | 映射到 BlockType |
|---|---|---|
| `user_input` | 用户输入 | `USER_INPUT` |
| `plan_thought` | 计划思考 | `ASSISTANT_THOUGHT` |
| `tool_thought` | 工具调用前思考 | `ASSISTANT_THOUGHT` |
| `plan` | 计划快照（步骤描述） | `ASSISTANT_THOUGHT` |
| `task` | 任务执行声明 | `TOOL_USE` |
| `result`（无 artifact） | 工具执行结果 | `TOOL_RESULT` |
| `result`（有 artifact） | 产物生成 | `ARTIFACT_REFERENCE` |
| `deep_search` | 搜索结果 | `TOOL_RESULT` |
| `knowledge` | 知识库结果 | `TOOL_RESULT` |
| `browser` | 浏览器结果 | `TOOL_RESULT` |
| `data_analysis` | 数据分析结果 | `TOOL_RESULT` |
| `html` | HTML 产物 | `ARTIFACT_REFERENCE` |
| `markdown` | Markdown 产物 | `ARTIFACT_REFERENCE` |
| `code` | 代码产物 | `ARTIFACT_REFERENCE` |
| `ppt` | PPT 产物 | `ARTIFACT_REFERENCE` |
| `file` | 文件产物 | `ARTIFACT_REFERENCE` |
| `assistant_answer` | 最终回答 | `ASSISTANT_ANSWER` |

**归类逻辑（按语义角色分发，不是兜底兼容）**：

```java
public class TranscriptBlockMapper {
    public List<TranscriptBlock> map(AgentResponse response) {
        String type = response.getMessageType();

        if (isThoughtType(type)) {
            return List.of(assistantThoughtBlock(response));
        }
        if (isToolUseType(type, response)) {
            return List.of(toolUseBlock(response));
        }
        if (isArtifactType(type, response)) {
            return List.of(artifactBlock(response));
        }
        if (isToolResultType(type, response)) {
            return List.of(toolResultBlock(response));
        }
        if ("assistant_answer".equals(type)) {
            return List.of(answerBlock(response));
        }
        if ("user_input".equals(type)) {
            return List.of(userInputBlock(response));
        }

        throw new IllegalArgumentException("Unknown message type: " + type);
    }

    private boolean isThoughtType(String type) {
        return Set.of("plan_thought", "tool_thought", "plan").contains(type);
    }

    private boolean isToolUseType(String type, AgentResponse response) {
        return "task".equals(type) || response.getToolUseId() != null;
    }

    private boolean isArtifactType(String type, AgentResponse response) {
        return Set.of("html", "markdown", "code", "ppt", "file").contains(type)
            || ("result".equals(type) && response.hasArtifact());
    }

    private boolean isToolResultType(String type, AgentResponse response) {
        return Set.of("result", "deep_search", "knowledge", "browser", "data_analysis")
            .contains(type) && !response.hasArtifact();
    }
}
```

**关键区别：归类 vs 兼容**
- **兼容**：不知道是什么，fallback 到一个默认值
- **归类**：明确知道原始类型的语义，分到对应的语义桶
- 新增原始类型时必须明确语义归属，否则抛异常

### 4.2 读取流（构建 LLM 上下文）

```
需要构建 LLM 上下文时：
  1. SessionMemoryDao.queryLatest(conversationId)
     → SessionMemory
  2. TurnDao.queryAfterSortOrder(conversationId, boundarySortOrder)
     → List<Turn>
  3. TranscriptBlockDao.queryByTurnIds(turnIds)
     → List<TranscriptBlock> (已按 turn_id, seq_no 排序)
  4. TranscriptPromptFormatter.format(memory, blocks)
     → String (LLM prompt)
```

**格式化逻辑**：

```java
public class TranscriptPromptFormatter {
    public String format(SessionMemory memory, List<TranscriptBlock> blocks) {
        StringBuilder sb = new StringBuilder();

        if (memory != null && memory.getSummaryText() != null) {
            sb.append("=== 历史摘要 ===\n").append(memory.getSummaryText()).append("\n\n");
        }

        if (memory != null && memory.getArtifactRefs() != null) {
            sb.append("=== 可复用文件 ===\n");
            for (ArtifactRef ref : memory.getArtifactRefs()) {
                sb.append("- ").append(ref.getName()).append(": ").append(ref.getUrl()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("=== 最近对话 ===\n");
        for (TranscriptBlock block : blocks) {
            sb.append(formatBlock(block));
        }

        return sb.toString();
    }

    private String formatBlock(TranscriptBlock block) {
        return switch (block.getBlockType()) {
            case USER_INPUT -> "User: " + block.getText() + "\n";
            case ASSISTANT_THOUGHT -> "Thought: " + block.getText() + "\n";
            case TOOL_USE -> "Tool[" + block.getToolName() + "]: " + block.getText() + "\n";
            case TOOL_RESULT -> "Result: " + block.getText() + "\n";
            case ARTIFACT_REFERENCE -> "Artifact: " + block.getText() + "\n";
            case ASSISTANT_ANSWER -> "Assistant: " + block.getText() + "\n";
        };
    }
}
```

### 4.3 压缩流（生成摘要）

```
需要压缩时：
  1. 确定压缩边界
  2. TranscriptBlockDao.queryByTurnIds(turnIds) → List<TranscriptBlock>
  3. TranscriptPromptFormatter.formatBlocks(blocks) → String
  4. LlmSummaryGenerator.generate(text) → summaryText
  5. ArtifactRefExtractor.extract(blocks) → List<ArtifactRef>
  6. SessionMemoryDao.insert(new SessionMemory(...))
```

---

## 5. 类变更清单

### 5.1 删除的旧类（约 10 个）

| 类名 | 删除原因 |
|------|---------|
| `EventProjector` | 10+ 种 messageType 硬编码映射，新设计直接映射 6 种 block_type |
| `OrderedEvent` | 中间投影模型，不再需要 |
| `AgentMessage` | 旧实体，被 `Turn` 替代 |
| `AgentMessageEvent` | 旧实体，被 `TranscriptBlock` 替代 |
| `SessionTranscriptBlockAssembler` | 复杂恢复逻辑，新设计直接读取 |
| `SessionArtifactRestoreSupport` | 多源文件恢复，新设计 block 自包含 |
| `ConversationEventPayloadNormalizer` | 遗留字段处理，新设计无遗留字段 |
| `ConversationEventFactSupport` | 事实投影+兜底，新设计无投影概念 |
| `SessionWorkingMemoryAssembler` | 复杂组装逻辑，新设计直接查询 |
| `SessionMemorySummaryBuilder` | 摘要结构校正，新设计 LLM 直接输出标准格式 |

### 5.2 新增的核心类（约 5 个）

| 类名 | 职责 |
|------|------|
| `Turn` / `TranscriptBlock` / `SessionMemory` | 新实体 |
| `TranscriptBlockType` | 6 种标准类型枚举 |
| `TranscriptBlockMapper` | AgentResponse → TranscriptBlock 直接映射 |
| `TranscriptBlockDao` / `TurnDao` / `SessionMemoryDao` | 新 DAO |
| `TranscriptPromptFormatter` | Block → LLM prompt 简单拼接 |

---

## 6. 迁移策略

- **旧表**：`ai_agent_message`、`ai_agent_message_event` 废弃，不再写入
- **新表**：`ai_agent_turn`、`ai_agent_transcript_block` 创建
- **历史数据**：不迁移，清空（方案 A）
- **前端回放（链路 2）**：不在本次范围内，后续独立重构

---

## 7. 数据转换链对比

| 环节 | 当前（6 层） | 新设计（2 层） |
|------|-------------|--------------|
| 流式响应 → 内存对象 | `EventProjector.project()` → `OrderedEvent` | `TranscriptBlockMapper.map()` → `TranscriptBlock` |
| 内存对象 → 数据库 | `AgentMessageEvent` | `TranscriptBlock` |
| 数据库 → 工作记忆 | `SessionWorkingMemoryAssembler`（多源恢复+去重） | 直接查询 |
| 工作记忆 → LLM prompt | `SessionMemoryPromptFormatter`（复杂格式化） | `TranscriptPromptFormatter`（简单拼接） |

---

## 8. 风险与应对

| 风险 | 应对 |
|------|------|
| 上游 Agent 产生未定义消息类型 | **设计决策**：直接抛异常，迫使上游标准化 |
| 前端回放（链路 2）暂时不可用 | 后续独立重构，本次不影响 |
| 历史数据丢失 | **设计决策**：方案 A 接受此代价 |
| 新增 block_type 需求 | 修改枚举 + 新增映射分支即可，不需要兼容逻辑 |

---

*设计完成日期：2026-04-28*
*方案：A（扁平化 TranscriptBlock）*

# 项目改造提示词：Claude Code 式上下文记忆架构

## 角色设定

你是一个资深 Java 架构师，精通 DDD 分层架构、Spring AI 和 Agent 系统设计。你的任务是将 `ai-agent-station-study` 项目的会话上下文记忆机制从当前规则驱动式升级为 **Claude Code 式的多阶段语义压缩架构**。

改造必须严格遵循现有 DDD 分层规范，不可破坏现有 API 契约，所有变更必须向后兼容。

---

## 一、项目背景

### 1.1 项目结构
```
ai-agent-station-study/
  ai-agent-station-study-types/    # 基础类型
  ai-agent-station-study-api/      # DTO、接口契约
  ai-agent-station-study-domain/   # 核心业务逻辑（改造主战场）
  ai-agent-station-study-infrastructure/  # 数据访问、DAO
  ai-agent-station-study-trigger/  # HTTP 接口、Controller
  ai-agent-station-study-app/      # 启动模块、配置
```

### 1.2 技术栈
- Spring Boot 3.4.3 + Java 17
- Spring AI 1.1.4
- MyBatis-Plus + MySQL
- OKHttp3 (SSE 客户端)

### 1.3 当前架构已实现的机制
- `ai_agent_conversation`：会话表
- `ai_agent_message`：消息账本（query/response/status）
- `ai_agent_message_event`：消息事件表（工具输出、计划、搜索结果等）
- `ai_agent_session_memory`：会话压缩快照表（摘要+边界）
- `AgentStreamPersistServiceImpl`：流式请求入口，负责构建 AgentRequest
- `SessionWorkingMemoryAssembler`：重建工作记忆
- `SessionMemoryCompactionService`：压缩服务（模板拼接摘要）
- `SessionMemoryPromptFormatter`：格式化 prompt
- `SessionArtifactRestoreSupport`：文件恢复

---

## 二、当前架构诊断（必须修复的缺陷）

### P0 - 工具输出完全丢失
**现状**：工具调用产生的输出（deep_search 结果、代码执行、MCP 调用）只写入 `ai_agent_message_event` 表，但后续续聊时完全不读取这些事件。
**影响**：LLM 无法感知自己之前做过什么，用户说"继续搜索"时 LLM 会从头开始。

### P0 - 文件只有名没有 URL
**现状**：`SessionMemoryPromptFormatter.formatFiles()` 只输出文件名和描述，不输出 `fileUrl`。
**影响**：LLM 看到"report.pdf"但无法访问其内容，历史文件无法复用。
**代码位置**：`ai-agent-station-study-domain/.../SessionMemoryPromptFormatter.java:65-82`

### P1 - 消息模型过于简单
**现状**：`AgentRequest.Message` 只有 `role` + `content`（字符串），无法表达 tool_use/tool_result/thinking 等结构化内容。
**影响**：无法传递完整的工具调用链给 LLM。

### P1 - 压缩是模板拼接而非语义理解
**现状**：`SessionMemorySummaryBuilder` 用模板拼接"第 X 轮：用户提出...系统回应..."。
**影响**：压缩后的摘要没有语义价值，丢失了关键决策和约束。

### P1 - 事件表内容浪费
**现状**：`ai_agent_message_event` 存了大量 `content_text` 和 `payload_json`，但 `SessionWorkingMemoryAssembler` 只从中提取 `artifactRefs`，完全忽略了 `content_text` 中的搜索结果、工具输出等内容。

### P2 - 无微压缩（Microcompact）
**现状**：Claude Code 有 microcompact 机制（静默清理大体积工具结果），当前项目没有。

### P2 - 无多阶段压缩流水线
**现状**：Claude Code 有 snip → microcompact → autocompact → collapse 多阶段流水线，当前项目只有单阶段压缩。

---

## 三、目标架构规格

参考 Claude Code 的上下文管理架构，实现以下核心能力：

### 3.1 消息模型升级（Content Blocks）

将 `AgentRequest.Message` 从简单的字符串升级为支持多种内容块的结构：

```java
@Data
@Builder
public class AgentRequest {
    // ... 现有字段保持不变 ...
    
    /** 历史消息（含工具调用链） */
    private List<Message> messages;
    
    @Data
    @Builder
    public static class Message {
        private String role;  // user | assistant | system
        private List<ContentBlock> content;  // 内容块列表，替代单一字符串
        private String commandCode;
        private List<FileInformation> uploadFile;
        private List<FileInformation> files;
    }
    
    /** 内容块基类 */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = TextBlock.class, name = "text"),
        @JsonSubTypes.Type(value = ToolUseBlock.class, name = "tool_use"),
        @JsonSubTypes.Type(value = ToolResultBlock.class, name = "tool_result"),
        @JsonSubTypes.Type(value = ThinkingBlock.class, name = "thinking"),
        @JsonSubTypes.Type(value = FileBlock.class, name = "file"),
    })
    public interface ContentBlock {
        String getType();
    }
    
    @Data
    @Builder
    public static class TextBlock implements ContentBlock {
        private String text;
        public String getType() { return "text"; }
    }
    
    @Data
    @Builder
    public static class ToolUseBlock implements ContentBlock {
        private String id;       // 工具调用ID
        private String name;     // 工具名
        private Map<String, Object> input;  // 输入参数
        public String getType() { return "tool_use"; }
    }
    
    @Data
    @Builder
    public static class ToolResultBlock implements ContentBlock {
        private String toolUseId;  // 对应的 tool_use ID
        private List<ContentBlock> content;  // 结果内容（可以是 text/file）
        private Boolean isError;   // 是否执行失败
        public String getType() { return "tool_result"; }
    }
    
    @Data
    @Builder
    public static class ThinkingBlock implements ContentBlock {
        private String thinking;  // 推理内容
        private String signature; // 签名（可选）
        public String getType() { return "thinking"; }
    }
    
    @Data
    @Builder
    public static class FileBlock implements ContentBlock {
        private String fileName;
        private String fileUrl;   // 可访问地址
        private String fileDesc;  // 描述
        public String getType() { return "file"; }
    }
}
```

**注意**：为了保持向后兼容，需要保留字符串形式的 `content` 字段（加 `@Deprecated`），并在序列化时同时支持新旧格式。

### 3.2 工具输出进入上下文

**改造点**：`SessionWorkingMemoryAssembler` 在重建工作记忆时，必须读取 `ai_agent_message_event` 中的工具输出内容，并将其转换为 `ToolResultBlock` 纳入消息链。

**具体规则**：
1. 对每轮消息的 event 进行解析
2. `eventType = deep_search` 且 `eventSubType = search`：提取 `content_text` 中的搜索 query 和结果摘要，生成 `ToolResultBlock`
3. `eventType = task` 且包含工具调用：提取 `payload_json` 中的工具名和结果，生成 `ToolUseBlock` + `ToolResultBlock`
4. `eventType = file`：提取文件信息，生成 `FileBlock`
5. `eventType = code`：提取代码执行结果，生成 `ToolResultBlock`

**新增组件**：`EventToContentBlockConverter`（事件到内容块转换器）

### 3.3 文件 URL 修复

**改造点**：`SessionMemoryPromptFormatter.formatFiles()` 必须输出文件 URL。

```java
private String formatFiles(List<FileInformation> files) {
    StringBuilder builder = new StringBuilder("## 可继续复用的历史文件");
    for (FileInformation file : files) {
        builder.append("\n- ").append(file.getFileName());
        if (StringUtils.hasText(file.getFileUrl())) {
            builder.append(" [访问：").append(file.getFileUrl()).append("]");
        }
        if (StringUtils.hasText(file.getFileDesc())) {
            builder.append("：").append(file.getFileDesc().trim());
        }
    }
    return builder.toString();
}
```

### 3.4 多阶段压缩流水线

参考 Claude Code 的流水线，在你的项目中实现以下阶段：

```
原始消息历史
    ↓
[阶段1: Microcompact]      ← 清理可压缩工具结果（大体积的 Read/Grep/Bash 输出）
    ↓
[阶段2: Snip]              ← 从旧消息开始截断，保留最近窗口
    ↓
[阶段3: LLM Summary]       ← 用 LLM 生成语义摘要（替代模板拼接）
    ↓
[阶段4: Boundary Persist]  ← 持久化压缩边界到 ai_agent_session_memory
    ↓
重建工作记忆
```

**各阶段规格**：

#### 阶段1: Microcompact（微压缩）
- **触发条件**：单条 tool_result 内容超过 5000 token
- **处理逻辑**：将大体积工具结果替换为摘要（保留前 200 字 + "... [内容已压缩，共 X 字]")
- **适用工具**：deep_search、code_interpreter、file_tool 的读取结果
- **实现位置**：新增 `SessionMemoryMicrocompactService`

#### 阶段2: Snip（裁剪）
- **触发条件**：总 token 超过阈值（12000）且轮次超过保留窗口（2轮）
- **处理逻辑**：从 oldest 消息开始删除，直到总 token 低于阈值
- **注意**：不能删除包含未闭合 tool_use 的消息对
- **实现位置**：`SessionMemoryCompactionService` 现有逻辑基础上增强

#### 阶段3: LLM Summary（LLM 语义摘要）
- **触发条件**：snip 后仍超过阈值，或轮次超过 10 轮
- **处理逻辑**：调用 LLM 生成结构化摘要
- **摘要格式**（9段式，参考 Claude Code）：
  ```
  ## 会话摘要
  
  ### 目标与约束
  {用户的核心目标和已明确的约束条件}
  
  ### 已完成的步骤
  {1. ... 2. ... 3. ...}
  
  ### 关键决策
  {用户做过的选择和原因}
  
  ### 生成的文件/产物
  {文件名、用途、存放位置}
  
  ### 待续事项
  {尚未完成的子任务}
  
  ### 技术上下文
  {项目使用的技术栈、关键配置}
  
  ### 已知问题
  {遇到的错误和解决方案}
  
  ### 用户偏好
  {输出格式偏好、语言偏好等}
  
  ### 时间线
  {每轮的核心动作（非常简短）}
  ```
- **实现位置**：新增 `SessionMemoryLlmSummaryService`

### 3.5 事件表内容纳入工作记忆

**改造点**：`SessionWorkingMemoryAssembler` 中 `buildRecentTurns` 方法需要重构。

**当前逻辑**：
```java
// 当前：只取 query + response
turns.add(SessionTurnMemory.builder()
    .userMessage(message.getQuery())
    .assistantMessage(message.getResponse())
    .build());
```

**目标逻辑**：
```java
// 目标：构建完整的 content blocks 列表
List<ContentBlock> userBlocks = new ArrayList<>();
userBlocks.add(new TextBlock(message.getQuery()));
// 加上传文件
if (files != null) userBlocks.addAll(files.stream().map(f -> new FileBlock(...)).toList());

List<ContentBlock> assistantBlocks = new ArrayList<>();
// 1. 添加 assistant 文本响应
if (message.getResponse() != null) {
    assistantBlocks.add(new TextBlock(message.getResponse()));
}
// 2. 从 event 表中提取工具调用链
List<AgentMessageEvent> events = eventMap.get(message.getId());
if (events != null) {
    for (AgentMessageEvent event : events) {
        List<ContentBlock> eventBlocks = eventToContentBlockConverter.convert(event);
        assistantBlocks.addAll(eventBlocks);
    }
}
```

---

## 四、数据模型变更

### 4.1 新增字段（不改现有字段，保持向后兼容）

#### `ai_agent_message_event` 表
```sql
-- 新增字段用于标识内容块类型和关联
ALTER TABLE ai_agent_message_event ADD COLUMN tool_use_id VARCHAR(64) COMMENT '关联的 tool_use ID';
ALTER TABLE ai_agent_message_event ADD COLUMN tool_name VARCHAR(128) COMMENT '工具名称';
ALTER TABLE ai_agent_message_event ADD COLUMN is_compressible TINYINT DEFAULT 1 COMMENT '是否可被压缩（1=可压缩，0=不可压缩）';
ALTER TABLE ai_agent_message_event ADD COLUMN compacted_content TEXT COMMENT '压缩后的内容摘要';
```

#### `ai_agent_session_memory` 表
```sql
-- 新增字段用于多阶段压缩
ALTER TABLE ai_agent_session_memory ADD COLUMN microcompact_boundary INT DEFAULT -1 COMMENT '微压缩边界 sort_order';
ALTER TABLE ai_agent_session_memory ADD COLUMN llm_summary_version INT DEFAULT 0 COMMENT 'LLM摘要版本号';
ALTER TABLE ai_agent_session_memory ADD COLUMN compressed_tool_results_json TEXT COMMENT '被压缩的工具结果索引';
```

### 4.2 新增实体类

在 `domain/agent/reactor/model/memory/` 包下新增：

- `ContentBlock.java` - 内容块接口
- `TextBlock.java` - 文本块
- `ToolUseBlock.java` - 工具调用块
- `ToolResultBlock.java` - 工具结果块
- `ThinkingBlock.java` - 思考块
- `FileBlock.java` - 文件块

---

## 五、核心模块改造路线

### 阶段一：基础设施（数据模型）
1. 新增 ContentBlock 类型体系（6个类 + 1个接口）
2. 修改 `AgentRequest.Message`，支持 `List<ContentBlock> contentBlocks`
3. 数据库 migration（新增字段）
4. 更新 MyBatis Mapper XML

### 阶段二：事件转换（打通 event → context）
1. 实现 `EventToContentBlockConverter`
   - `convert(AgentMessageEvent event)` → `List<ContentBlock>`
   - 处理所有 eventType：deep_search, task, tool_result, file, code, html, markdown, data_analysis
2. 改造 `SessionWorkingMemoryAssembler`
   - `buildRecentTurns` 返回带 content blocks 的 turn
3. 改造 `SessionWorkingMemory`
   - `recentTurns` 从 `List<SessionTurnMemory>` 升级为带 content blocks 的结构

### 阶段三：文件 URL 修复
1. 修改 `SessionMemoryPromptFormatter.formatFiles()`
2. 修改 `FileInformation`（如果缺少 fileUrl 字段则添加）
3. 确保 `SessionArtifactRestoreSupport` 恢复文件时携带 URL

### 阶段四：微压缩（Microcompact）
1. 实现 `SessionMemoryMicrocompactService`
   - 识别可压缩的工具结果（基于 event type + content length）
   - 生成压缩摘要（保留前 N 字 + 统计信息）
   - 标记 `ai_agent_message_event.is_compressible = 0`
2. 集成到 `SessionMemoryCompactionService` 之前

### 阶段五：LLM 语义摘要
1. 实现 `SessionMemoryLlmSummaryService`
   - 构造 Summary Prompt（9段式）
   - 调用 LLM 生成结构化摘要
   - 解析 LLM 输出为 `summaryText` + `facts`
2. 替换 `SessionMemorySummaryBuilder` 的模板拼接逻辑

### 阶段六：Prompt 格式化升级
1. 改造 `SessionMemoryPromptFormatter`
   - 支持 content blocks 的文本化渲染
   - tool_use/tool_result 渲染为可读的 markdown 格式
   - 保持 `{{history_dialogue}}` 占位符兼容

### 阶段七：AgentRequest 构建升级
1. 改造 `AgentStreamPersistServiceImpl`
   - `buildContextMessages`：支持 content blocks
   - `buildWorkingMemoryMessages`：支持 content blocks
   - `trimToTokenBudget`：基于 content blocks 的 token 估算

### 阶段八：测试与验收
1. 单元测试覆盖
2. 集成测试（参考 quickstart.md 的验收用例）

---

## 六、代码规范

1. **DDD 分层**：所有改造逻辑放在 `domain` 模块，数据访问放在 `infrastructure`
2. **包命名**：`org.wwz.ai.domain.agent.reactor.service.support.memory.*`
3. **向后兼容**：
   - `AgentRequest.Message.content` 保留，新增 `contentBlocks`
   - 序列化时优先使用 `contentBlocks`，回退到 `content`
   - 数据库 migration 使用 `ALTER TABLE ADD COLUMN`，不删除现有字段
4. **配置开关**：
   ```yaml
   autobots:
     autoagent:
       session-memory:
         llm-summary-enabled: true        # LLM 语义摘要开关
         microcompact-enabled: true       # 微压缩开关
         microcompact-threshold-tokens: 5000  # 微压缩触发阈值
   ```
5. **日志规范**：所有压缩/重建操作必须打印结构化日志
   ```
   log.info("[MemoryPipeline] sessionId={}, stage={}, inputTokens={}, outputTokens={}, details={}", ...)
   ```

---

## 七、关键文件清单

### 必须修改的文件
| 文件路径 | 修改内容 |
|---------|---------|
| `domain/.../model/req/AgentRequest.java` | Message 类增加 contentBlocks |
| `domain/.../model/memory/SessionWorkingMemory.java` | 支持 content blocks |
| `domain/.../model/memory/SessionTurnMemory.java` | 支持 content blocks |
| `domain/.../service/support/SessionWorkingMemoryAssembler.java` | 读取 event 内容块 |
| `domain/.../service/support/SessionMemoryCompactionService.java` | 集成多阶段流水线 |
| `domain/.../service/support/SessionMemoryPromptFormatter.java` | 修复文件URL，支持 content blocks 渲染 |
| `domain/.../service/impl/AgentStreamPersistServiceImpl.java` | 升级消息构建逻辑 |
| `domain/.../config/ReactorConfig.java` | 新增配置项 |
| `infrastructure/.../mapper/*.xml` | 数据库字段映射 |

### 新增文件
| 文件路径 | 职责 |
|---------|------|
| `domain/.../model/memory/block/ContentBlock.java` | 内容块接口 |
| `domain/.../model/memory/block/TextBlock.java` | 文本块 |
| `domain/.../model/memory/block/ToolUseBlock.java` | 工具调用块 |
| `domain/.../model/memory/block/ToolResultBlock.java` | 工具结果块 |
| `domain/.../model/memory/block/ThinkingBlock.java` | 思考块 |
| `domain/.../model/memory/block/FileBlock.java` | 文件块 |
| `domain/.../service/support/EventToContentBlockConverter.java` | event → content block 转换 |
| `domain/.../service/support/SessionMemoryMicrocompactService.java` | 微压缩服务 |
| `domain/.../service/support/SessionMemoryLlmSummaryService.java` | LLM 语义摘要服务 |

---

## 八、验收标准

完成改造后，必须通过以下测试：

### 8.1 工具输出可见性测试
1. 用户发起一轮 REACT 会话，要求"搜索 Spring AI 最新版本"
2. 等待搜索完成
3. 用户追问"把刚才搜索到的 MCP 相关内容整理成表格"
4. **预期**：LLM 能正确引用上一轮搜索结果中的具体内容（不重新搜索）

### 8.2 文件 URL 测试
1. 用户要求"生成一份报告"
2. 系统生成文件并返回 URL
3. 用户追问"把报告里的第 3 节扩展一下"
4. **预期**：LLM 能通过 URL 访问历史文件内容并正确扩展

### 8.3 压缩质量测试
1. 连续进行 10 轮以上对话
2. 触发压缩后检查 `ai_agent_session_memory.summary_text`
3. **预期**：摘要包含"目标、已完成步骤、关键决策、待续事项"等语义信息，而非"第1轮...第2轮..."的流水账

### 8.4 微压缩测试
1. 执行一次大文件读取或大规模搜索
2. 检查 `ai_agent_message_event.compressed_content`
3. **预期**：大体积内容被替换为摘要，但关键信息保留

### 8.5 向后兼容测试
1. 使用旧格式（无 contentBlocks）的客户端发起请求
2. **预期**：系统正常处理，不抛异常

---

## 九、注意事项

1. **不要修改 `ai_agent_message` 表结构**：当前表结构已经满足账本需求，改造重点在 event 表的利用和消息模型的升级
2. **不要破坏 CHAT 模式**：CHAT 模式的滑动窗口逻辑保持不变，只升级消息结构
3. **LLM 摘要调用要异步**：`SessionMemoryLlmSummaryService` 的 LLM 调用应该在流结束后异步执行，不阻塞用户响应
4. **Token 估算要准确**：基于 content blocks 的 token 估算要考虑中文 1:1、英文 1:0.75 的混合比例
5. **错误降级**：LLM 摘要生成失败时，回退到模板拼接，不能中断会话

---

## 十、参考文档

- 当前项目架构文档：`ai-agent-station-study/CLAUDE.md`
- 当前会话记忆规格：`specs/006-session-context-memory/spec.md`
- 当前验收脚本：`specs/006-session-context-memory/quickstart.md`
- Claude Code 消息处理：`free-code-main/src/utils/messages.ts`（参考 normalizeMessagesForAPI）
- Claude Code 压缩机制：`free-code-main/src/services/compact/compact.ts`
- Claude Code 微压缩：`free-code-main/src/services/compact/microCompact.ts`

# 对话数据持久化记录设计

## 1. 背景与目标

### 1.1 背景

项目后端 Agent 执行引擎（Reactor）通过 `LLM.ask()` 和 `LLM.askTool()` 与大模型交互，通过 `BaseTool.execute()` 执行各类工具（代码解释器、深度搜索、文件操作等）。当前缺乏系统化的持久化机制来记录：

- LLM 调用的技术指标（token 消耗、耗时、模型名称等）
- 工具调用的入参、出参和执行结果
- 工具执行过程中产生的文件

### 1.2 目标

设计一套持久化方案，满足以下查询场景：

- **A 类查询（统一聚合）**：按会话/轮次查询所有 LLM 调用和工具执行记录
- **B 类查询（深度分析）**：按工具类型深入查询（如"代码解释器历史执行"、"搜索工具结果质量"）
- **C 类查询（文件管理）**：按轮次/会话查询产生的所有文件

### 1.3 非目标

- 不记录对话语义内容的完整历史（如用户输入文本、助手回答文本），该职责由对话历史模块负责
- 不做异步/消息队列解耦，采用同步实时写入

## 2. 设计决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 工具结果存储 | 统一 JSON 表 | 10+ 个工具，分散表维护成本高；JSON 解析即可满足 B 类查询 |
| LLM 与工具关联 | 通过 `request_id` | `AgentContext` 中天然有 `request_id`，无需额外传递 `call_id` |
| 文件关联 | 多态关联 + `turn_id` 冗余 | `ref_type` + `ref_id` 精确关联，`turn_id` 提供便捷的轮次聚合查询 |
| 写入策略 | 同步实时写入 | 简单直接，异常不阻断主流程（try-catch + 日志） |
| 写入封装 | 领域接口 + infrastructure 实现 | 不污染 domain 层业务代码，符合 DDD 分层 |

## 3. 表结构设计

### 3.1 agent_llm_call（LLM 调用记录）

记录每次 `LLM.ask()` 和 `LLM.askTool()` 的技术指标。

```sql
CREATE TABLE agent_llm_call (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    request_id          VARCHAR(64)     NOT NULL COMMENT 'AgentContext.requestId',
    turn_id             BIGINT          NULL COMMENT '对话轮次ID',
    call_type           VARCHAR(16)     NOT NULL COMMENT 'ask | askTool',
    model_name          VARCHAR(64)     NULL,
    system_prompt_hash  VARCHAR(64)     NULL COMMENT '系统提示词哈希，用于审计对比',
    prompt_tokens       INT             NULL DEFAULT 0,
    completion_tokens   INT             NULL DEFAULT 0,
    total_tokens        INT             NULL DEFAULT 0,
    duration_ms         BIGINT          NULL COMMENT 'LLM 接口调用耗时',
    finish_reason       VARCHAR(32)     NULL COMMENT 'stop | tool_calls | length | error',
    status              VARCHAR(16)     NOT NULL DEFAULT 'success' COMMENT 'success | error | timeout',
    error_msg           TEXT            NULL,
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_id_call (request_id, call_type),
    KEY idx_turn_id (turn_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='LLM 调用记录';
```

### 3.2 agent_tool_execution（工具执行记录）

记录每次工具调用的入参、出参和执行结果，统一使用 JSON 存储工具专属数据。

```sql
CREATE TABLE agent_tool_execution (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    request_id      VARCHAR(64)     NOT NULL COMMENT '关联 agent_llm_call.request_id',
    tool_name       VARCHAR(64)     NOT NULL,
    tool_input_json JSON            NOT NULL COMMENT '工具入参（原始 JSON 结构）',
    tool_output_json JSON           NULL COMMENT '工具输出（原始 JSON 结构或文本）',
    duration_ms     BIGINT          NULL COMMENT '工具执行耗时',
    status          VARCHAR(16)     NOT NULL DEFAULT 'success' COMMENT 'success | error | timeout',
    error_msg       TEXT            NULL,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_request_id (request_id),
    KEY idx_tool_name (tool_name),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='工具执行记录';
```

**JSON 示例：**

`deep_search` 的 `tool_output_json`：
```json
{
  "query": "Spring Boot 启动原理",
  "results": [
    {"title": "...", "url": "...", "summary": "..."}
  ],
  "result_count": 5
}
```

`code_interpreter` 的 `tool_output_json`：
```json
{
  "stdout": "Chart generated successfully",
  "stderr": "",
  "exit_code": 0,
  "files": ["chart_001.png"]
}
```

### 3.3 agent_tool_file（文件关联表）

记录工具产生的文件和用户上传的文件。

```sql
CREATE TABLE agent_tool_file (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    turn_id     BIGINT          NULL COMMENT '冗余：方便查询某轮对话的所有文件',
    request_id  VARCHAR(64)     NULL COMMENT '冗余：方便查询某次请求的所有文件',
    ref_type    VARCHAR(32)     NOT NULL COMMENT 'execution | session | user_upload',
    ref_id      VARCHAR(64)     NOT NULL COMMENT '对应表的主键ID或标识符',
    file_name   VARCHAR(256)    NOT NULL,
    file_path   VARCHAR(512)    NOT NULL COMMENT '存储路径或 URL',
    file_size   BIGINT          NULL DEFAULT 0,
    mime_type   VARCHAR(64)     NULL,
    source      VARCHAR(32)     NOT NULL COMMENT 'code_interpreter | file_tool | user_upload | mcp',
    metadata_json JSON          NULL COMMENT '额外元数据（如图片尺寸、代码语言等）',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_turn_id (turn_id),
    KEY idx_request_id (request_id),
    KEY idx_ref (ref_type, ref_id),
    KEY idx_source (source)
) ENGINE=InnoDB COMMENT='工具及用户文件记录';
```

**多态关联说明：**

| ref_type | ref_id 指向 | 场景 |
|----------|------------|------|
| `execution` | `agent_tool_execution.id` | 代码解释器生成的图片/文件 |
| `session` | `AgentContext.sessionId` | 会话级产物、用户上传的文件 |
| `user_upload` | 用户ID或 `"0"` | 用户直接上传的附件 |

## 4. 数据流设计

### 4.1 整体数据流

```
用户提问
   |
   ▼
+-----------+     +-----------------+
| LLM.ask() |---->| agent_llm_call  |  (记录调用元数据)
|askTool()  |     +-----------------+
+-----------+              |
        |                  ▼
        |           +-----------------+
        |           | 返回 ToolCalls  |
        |           +-----------------+
        |                  |
        ▼                  ▼
+-----------+     +---------------------+
|executeTool|---->| agent_tool_execution|  (记录工具入参/出参 JSON)
|           |     +---------------------+
+-----------+              |
        |                  ▼
        |           +-----------------+
        |           | 产生文件？       |
        |           +-----------------+
        |                  |
        ▼            是 /      \ 否
   +--------+      ▼            ▼
   | 返回结果|  +-----------+  (结束)
   |格式化进 |  |agent_tool_file| (记录文件)
   | prompt |  +-----------+
   +--------+
```

### 4.2 写入点

| 写入点 | 位置 | 写入表 | 说明 |
|--------|------|--------|------|
| 1 | `LLM.ask()` 返回后 | `agent_llm_call` | 记录 LLM 调用元数据 |
| 2 | `LLM.askTool()` 返回后 | `agent_llm_call` | 同上，`call_type = askTool` |
| 3 | `BaseAgent.executeTool()` 执行后 | `agent_tool_execution` | 记录工具入参、出参、耗时 |
| 4 | `BaseAgent.run()` 返回前 | `agent_tool_file` | 记录 `context.productFiles` 中的文件 |

## 5. 代码设计

### 5.1 领域层接口（domain 层）

```java
package org.wwz.ai.domain.agent.reactor.agent.recorder;

public interface AgentExecutionRecorder {
    
    /**
     * 记录 LLM 调用
     * @return 生成的 call_id，失败返回 null
     */
    Long recordLlmCall(LlmCallRecord record);
    
    /**
     * 记录工具执行
     * @return 生成的 execution_id，失败返回 null
     */
    Long recordToolExecution(ToolExecutionRecord record);
    
    /**
     * 批量记录文件
     */
    void recordFiles(List<ToolFileRecord> files);
}
```

```java
package org.wwz.ai.domain.agent.reactor.agent.recorder;

@Data
@Builder
public class LlmCallRecord {
    private String requestId;
    private Long turnId;
    private String callType;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long durationMs;
    private String finishReason;
    private String status;
    private String errorMsg;
}
```

```java
package org.wwz.ai.domain.agent.reactor.agent.recorder;

@Data
@Builder
public class ToolExecutionRecord {
    private String requestId;
    private String toolName;
    private String toolInputJson;
    private String toolOutputJson;
    private Long durationMs;
    private String status;
    private String errorMsg;
}
```

```java
package org.wwz.ai.domain.agent.reactor.agent.recorder;

@Data
@Builder
public class ToolFileRecord {
    private Long turnId;
    private String requestId;
    private String refType;
    private Long refId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String mimeType;
    private String source;
    private String metadataJson;
}
```

### 5.2 AgentContext 扩展

本设计不需要修改 `AgentContext` 的字段。工具执行记录通过 `request_id`（`AgentContext` 中已有）与 LLM 调用关联，无需额外传递 `call_id`。

> 注：如果未来需要精确关联"某次工具调用是由哪次 LLM 调用触发的"（如同一 `request_id` 下有多次 `askTool`），可在 `ToolExecutionRecord` 和 `agent_tool_execution` 表中增加 `call_id` 字段。

### 5.3 LLM 层写入点

```java
// LLM.java

public CompletableFuture<String> ask(AgentContext context, 
                                      List<Message> messages,
                                      List<Message> systemMsgs,
                                      boolean stream,
                                      Double temperature) {
    long start = System.currentTimeMillis();
    ChatRequest request = buildRequest(messages, systemMsgs, temperature);
    
    return asyncClient.chat(request).thenApply(resp -> {
        long duration = System.currentTimeMillis() - start;
        
        // 写入点 1：记录 LLM 调用
        LlmCallRecord record = LlmCallRecord.builder()
            .requestId(context.getRequestId())
            .turnId(resolveTurnId(context))
            .callType("ask")
            .modelName(modelConfig.getName())
            .promptTokens(resp.getUsage().getPromptTokens())
            .completionTokens(resp.getUsage().getCompletionTokens())
            .totalTokens(resp.getUsage().getTotalTokens())
            .durationMs(duration)
            .finishReason(resp.getChoices().get(0).getFinishReason())
            .status("success")
            .build();
        
        recorder.recordLlmCall(record);
        
        return resp.getChoices().get(0).getMessage().getContent();
        
    }).exceptionally(ex -> {
        // 错误也记录
        recorder.recordLlmCall(LlmCallRecord.builder()
            .requestId(context.getRequestId())
            .turnId(resolveTurnId(context))
            .callType("ask")
            .status("error")
            .errorMsg(ex.getMessage())
            .build());
        throw new RuntimeException(ex);
    });
}
```

`askTool()` 逻辑相同，`callType = "askTool"`。

### 5.4 BaseAgent 层写入点

```java
// BaseAgent.java

public String executeTool(ToolCall command) {
    String name = command.getFunction().getName();
    Object args = parseArgs(command.getFunction().getArguments());
    
    long start = System.currentTimeMillis();
    Object result;
    String status = "success";
    String errorMsg = null;
    
    try {
        result = availableTools.execute(name, args);
    } catch (Exception e) {
        result = formatError(e);
        status = "error";
        errorMsg = e.getMessage();
    }
    
    long duration = System.currentTimeMillis() - start;
    
    // 写入点 3：记录工具执行
    ToolExecutionRecord execRecord = ToolExecutionRecord.builder()
        .requestId(context.getRequestId())
        .toolName(name)
        .toolInputJson(JSON.toJSONString(args))
        .toolOutputJson(result instanceof String ? (String) result : JSON.toJSONString(result))
        .durationMs(duration)
        .status(status)
        .errorMsg(errorMsg)
        .build();
    
    Long executionId = recorder.recordToolExecution(execRecord);
    
    // 提取并记录文件
    extractAndRecordFiles(executionId, name, args, result);
    
    return formatResult(result);
}

private void extractAndRecordFiles(Long executionId, String toolName, 
                                    Object input, Object result) {
    List<ToolFileRecord> files = new ArrayList<>();
    
    // 从工具输出中提取文件引用
    if (result instanceof Map) {
        Object fileList = ((Map<?, ?>) result).get("files");
        if (fileList instanceof List) {
            for (Object f : (List<?>) fileList) {
                files.add(ToolFileRecord.builder()
                    .turnId(resolveTurnId(context))
                    .requestId(context.getRequestId())
                    .refType("execution")
                    .refId(executionId)
                    .source(toolName)
                    .filePath(f.toString())
                    .build());
            }
        }
    }
    
    if (!files.isEmpty()) {
        recorder.recordFiles(files);
    }
}
```

### 5.5 产物文件记录

```java
// BaseAgent.java 或 SummaryAgent 中

protected void recordProductFiles() {
    if (CollectionUtils.isEmpty(context.getProductFiles())) {
        return;
    }
    
    List<ToolFileRecord> files = context.getProductFiles().stream()
        .map(file -> ToolFileRecord.builder()
            .turnId(resolveTurnId(context))
            .requestId(context.getRequestId())
            .refType("session")
            .refId(parseSessionId(context.getSessionId()))
            .source("user_upload")
            .fileName(file.getName())
            .filePath(file.getPath())
            .fileSize(file.length())
            .build())
        .collect(Collectors.toList());
    
    recorder.recordFiles(files);
}
```

### 5.6 Infrastructure 实现

```java
package org.wwz.ai.infrastructure.agent.recorder;

@Component
@Slf4j
public class AgentExecutionRecorderImpl implements AgentExecutionRecorder {
    
    @Autowired
    private AgentLlmCallMapper llmCallMapper;
    @Autowired
    private AgentToolExecutionMapper toolExecutionMapper;
    @Autowired
    private AgentToolFileMapper toolFileMapper;
    
    @Override
    public Long recordLlmCall(LlmCallRecord record) {
        try {
            AgentLlmCallPO po = convertToPO(record);
            llmCallMapper.insert(po);
            return po.getId();
        } catch (Exception e) {
            log.error("记录 LLM 调用失败, requestId={}", record.getRequestId(), e);
            return null;
        }
    }
    
    @Override
    public Long recordToolExecution(ToolExecutionRecord record) {
        try {
            AgentToolExecutionPO po = convertToPO(record);
            toolExecutionMapper.insert(po);
            return po.getId();
        } catch (Exception e) {
            log.error("记录工具执行失败, requestId={}", record.getRequestId(), e);
            return null;
        }
    }
    
    @Override
    public void recordFiles(List<ToolFileRecord> files) {
        if (CollectionUtils.isEmpty(files)) {
            return;
        }
        try {
            List<AgentToolFilePO> pos = files.stream()
                .map(this::convertToPO)
                .collect(Collectors.toList());
            toolFileMapper.insertBatch(pos);
        } catch (Exception e) {
            log.error("记录文件失败, count={}", files.size(), e);
        }
    }
    
    // ... convert 方法 ...
}
```

## 6. 查询示例

### 6.1 查某轮对话的所有文件

```sql
SELECT * FROM agent_tool_file WHERE turn_id = ? ORDER BY create_time;
```

### 6.2 查某次请求的所有工具调用

```sql
SELECT * FROM agent_tool_execution 
WHERE request_id = ? 
ORDER BY create_time;
```

### 6.3 查某次 LLM 调用的元数据

```sql
SELECT * FROM agent_llm_call WHERE request_id = ? AND call_type = 'askTool';
```

### 6.4 查代码解释器的历史执行

```sql
SELECT * FROM agent_tool_execution 
WHERE tool_name = 'code_interpreter' 
ORDER BY create_time DESC 
LIMIT 100;
```

### 6.5 从 JSON 中提取搜索结果的来源 URL（后端解析）

```java
// 查询获取 tool_output_json
AgentToolExecutionPO record = toolExecutionMapper.selectById(executionId);
Map<String, Object> output = JSON.parseObject(record.getToolOutputJson());

// 提取搜索结果
List<Map<String, Object>> results = (List<Map<String, Object>>) output.get("results");
List<String> urls = results.stream()
    .map(r -> (String) r.get("url"))
    .filter(Objects::nonNull)
    .collect(Collectors.toList());

// 提取搜索关键词
String query = (String) output.get("query");
Integer resultCount = (Integer) output.get("result_count");
```

### 6.6 统计某模型的 token 消耗

```sql
SELECT 
    model_name,
    COUNT(*) as call_count,
    SUM(total_tokens) as total_tokens,
    AVG(total_tokens) as avg_tokens,
    AVG(duration_ms) as avg_duration_ms
FROM agent_llm_call 
WHERE create_time > DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY model_name;
```

## 7. 扩展指南

### 7.1 新增工具

新增工具时，不需要修改表结构。只需确保工具的输出中包含需要持久化的数据，`agent_tool_execution` 会自动以 JSON 形式存储。

如果新工具有特殊文件产出，在 `extractAndRecordFiles` 方法中添加该工具的文件提取逻辑即可。

### 7.2 从 JSON 升级到独立表

如果某天某个工具的数据量增长到 JSON 查询成为瓶颈（如代码解释器每天百万次调用），可以：

1. 新建 `tool_exec_xxx_detail` 表
2. 在 `extractAndRecordFiles` 中同时写入 JSON 和 detail 表
3. 历史数据通过离线任务迁移
4. 查询切换到 detail 表

### 7.3 增加缓存层

如果同步写入成为性能瓶颈，可以在 `AgentExecutionRecorderImpl` 中增加内存缓冲：

```java
// 批量缓冲写入
private List<LlmCallRecord> llmCallBuffer = new ArrayList<>();

@Scheduled(fixedRate = 5000)
public void flushBuffer() {
    // 每 5 秒批量写入一次
}
```

注意：缓冲写入在进程崩溃时会丢失数据，需权衡。

## 8. 风险提示

| 风险 | 缓解措施 |
|------|----------|
| 同步写入影响 Agent 响应延迟 | Recorder 实现中异常捕获，确保写入失败不阻塞；后续可评估是否需要异步化 |
| JSON 字段过大导致单表膨胀 | 监控单表大小，必要时按时间分表或归档 |
| 工具输出结构变更导致 JSON 解析失败 | JSON 解析时做好 null 和类型安全检查 |
| `turn_id` 解析逻辑不一致 | 统一在 `resolveTurnId()` 方法中处理，避免多处硬编码 |

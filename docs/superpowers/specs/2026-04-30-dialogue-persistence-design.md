# 对话执行持久化设计

## 1. 背景与目标

### 1.1 背景

当前 Reactor 主链路已经具备比较清晰的运行时结构：

- 一次前端请求对应一个 `requestId`，当前代码里它就是单次执行的 trace / request 唯一标识
- 同一个请求内部会发生多次 `LLM.ask()` / `LLM.askTool()` 调用
- 一次 `askTool()` 可能返回多个 `toolCallId`
- 工具产物已经通过 `ToolArtifactRegistry + toolCallId` 形成稳定归属关系

但是系统仍缺少一套独立、稳定、可查询的执行账本，来回答下面三类问题：

- **运行链路查询**：一次请求里一共调了几次模型、几次工具、耗时如何、最终成功还是失败
- **工具深度分析**：某个工具最近都接收了什么参数、返回了什么结果、是否容易失败
- **文件产物管理**：某次执行或某个会话里产生了哪些文件，它们来自哪个工具调用

### 1.2 目标

设计一套以“单次执行（run）”为聚合根的持久化方案，满足：

1. 能完整记录一次执行的生命周期
2. 能准确表达 `run -> llm invocation -> tool invocation -> artifact` 的因果关系
3. 能直接复用当前代码里已有的 `requestId`、`toolCallId`、`ToolArtifactRegistry`
4. 保持同步实时写入，但不让持久化失败阻断主流程

### 1.3 非目标

- 不替代现有“对话历史正文”模块，不保存完整聊天 transcript
- 不保存 provider 原始响应包，不做逐 chunk 流式落库
- 不引入消息队列、异步批处理、独立审计服务

## 2. 核心设计结论

### 2.1 聚合根选型

本次不再以 `session_id` 或已废弃的 turn 概念作为执行账本聚合根。

执行账本统一以 **run** 为根：

- `request_id` 继续沿用当前主链路里的单次请求唯一标识
- 数据库内部再引入自增主键 `run_id`
- `session_id` 只作为跨 run 的归档和查询维度

### 2.2 表职责边界

- `ai_agent_dialogue_run`：记录一次执行的总览与最终状态
- `ai_agent_llm_invocation`：记录每次模型调用的指标和完整文本响应
- `ai_agent_tool_invocation`：记录每次工具调用的入参、出参、状态与耗时
- `ai_agent_artifact`：记录输入文件与工具输出产物的稳定引用

### 2.3 LLM 表只存“完整思考文本 + 指标”

`ai_agent_llm_invocation` 不再存：

- `response_excerpt`
- `response_payload_ref`
- provider 原始响应 JSON

原因：

- `ask()` 返回值本身就是完整文本
- `askTool()` 返回值经过领域收敛后，本质上也是“思考文本 + toolCalls + 指标”
- 工具入参与出参已经由 `ai_agent_tool_invocation` 独立存储，再在 LLM 表重复保存没有价值

因此 `ai_agent_llm_invocation` 只保留：

- 模型调用元数据
- 完整的 `response_text`
- `tool_call_count`
- token / finishReason / duration 等指标

### 2.4 文件表不做多态关联

`ai_agent_artifact` 不再使用 `ref_type + ref_id` 多态关联。

当前运行时已经天然存在稳定链路：

- 输入文件挂到 `run`
- 输出文件挂到 `tool_invocation`
- 工具侧文件来源由 `toolCallId` 唯一标识

因此文件表直接显式存：

- `run_id`
- `tool_invocation_id`
- `tool_call_id`

这样更清晰，也更方便查询和去重。

### 2.5 写入策略

写入策略采用 **同步实时写入 + 前插后更**：

- 请求进入时先创建 run
- LLM 调用前先插入 `RUNNING` 记录，调用结束后更新
- Tool 执行前先插入 `RUNNING` 记录，执行结束后更新
- Tool 结束后立刻补写该次工具产物
- 整个 run 结束时回写最终状态和聚合指标

不采用“全部执行结束后统一补写”的原因是：

- 中途异常会丢失链路
- 并发工具难以稳定归属
- 文件产物可能已经生成，但还没等到全局收口

### 2.6 Tool 不维护 run 级全局顺序号

`ai_agent_tool_invocation` 不再维护 `run` 级全局递增的 `invocation_seq`。

改为只在同一次 `llm_invocation` 内记录 `dispatch_index`：

- `dispatch_index` 表示当前 tool call 在本次 `askTool()` 返回列表中的原始顺序，从 1 递增
- 同一批 tool calls 的记录由主线程按原始顺序先批量插入，再交给工作线程并发执行
- 工具事实顺序看 `llm_invocation_id + dispatch_index`
- 工具真实执行时间线看 `started_at + id`

这样可以同时满足：

- 保留模型决策时的原始顺序
- 避免并发线程争抢全局序号
- 避免把“展示顺序”和“唯一标识”混到一个字段里

## 3. 表结构设计

### 3.1 ai_agent_dialogue_run

记录一次执行的聚合根。

```sql
CREATE TABLE ai_agent_dialogue_run (
    id                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    run_uid              VARCHAR(64)   NOT NULL COMMENT '运行唯一标识，首期可直接复用 request_id',
    request_id           VARCHAR(64)   NOT NULL COMMENT '当前主链路 requestId / traceId',
    session_id           VARCHAR(64)   NOT NULL COMMENT '会话维度ID',
    entry_agent          VARCHAR(32)   NOT NULL COMMENT '入口执行类型：react | plan_solve | workflow',
    status               TINYINT       NOT NULL DEFAULT 0 COMMENT '0=运行中 1=成功 2=失败 3=超时 4=停止',
    query_text           MEDIUMTEXT    NULL COMMENT '用户本次请求原文',
    final_summary_text   MEDIUMTEXT    NULL COMMENT '最终结果摘要或总结文本',
    llm_call_count       INT           NOT NULL DEFAULT 0 COMMENT '模型调用次数',
    tool_call_count      INT           NOT NULL DEFAULT 0 COMMENT '工具调用次数',
    artifact_count       INT           NOT NULL DEFAULT 0 COMMENT '产物数量',
    prompt_tokens_total  INT           NOT NULL DEFAULT 0 COMMENT '输入token总数',
    completion_tokens_total INT        NOT NULL DEFAULT 0 COMMENT '输出token总数',
    total_tokens_total   INT           NOT NULL DEFAULT 0 COMMENT '总token数',
    error_code           VARCHAR(64)   NULL COMMENT '错误码',
    error_msg            TEXT          NULL COMMENT '错误信息',
    started_at           DATETIME(3)   NOT NULL COMMENT '开始时间',
    finished_at          DATETIME(3)   NULL COMMENT '结束时间',
    duration_ms          BIGINT        NULL COMMENT '总耗时(毫秒)',
    create_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted              TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_uid (run_uid),
    UNIQUE KEY uk_request_id (request_id),
    KEY idx_session_time (session_id, deleted, create_time DESC),
    KEY idx_status_time (status, deleted, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单次对话执行账本';
```

### 3.2 ai_agent_llm_invocation

记录一次执行过程中的每次模型调用。

```sql
CREATE TABLE ai_agent_llm_invocation (
    id                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    run_id               BIGINT        NOT NULL COMMENT 'FK -> ai_agent_dialogue_run.id',
    invocation_seq       INT           NOT NULL COMMENT 'run 内第几次模型调用，从1递增',
    agent_name           VARCHAR(32)   NOT NULL COMMENT '当前调用所在 agent，如 executor/planning/summary',
    step_no              INT           NULL COMMENT '当前 agent step 序号',
    call_kind            VARCHAR(16)   NOT NULL COMMENT 'ask | askTool',
    streaming            TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否流式',
    model_name           VARCHAR(128)  NULL COMMENT '模型名',
    response_text        MEDIUMTEXT    NULL COMMENT '完整文本响应；askTool 场景为思考文本',
    tool_call_count      INT           NOT NULL DEFAULT 0 COMMENT 'askTool 下发的 tool call 数量',
    prompt_tokens        INT           NOT NULL DEFAULT 0,
    completion_tokens    INT           NOT NULL DEFAULT 0,
    total_tokens         INT           NOT NULL DEFAULT 0,
    finish_reason        VARCHAR(32)   NULL COMMENT 'stop | tool_calls | length | error',
    status               TINYINT       NOT NULL DEFAULT 0 COMMENT '0=运行中 1=成功 2=失败 3=超时',
    error_msg            TEXT          NULL COMMENT '错误信息',
    started_at           DATETIME(3)   NOT NULL COMMENT '开始时间',
    finished_at          DATETIME(3)   NULL COMMENT '结束时间',
    duration_ms          BIGINT        NULL COMMENT '耗时(毫秒)',
    create_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted              TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_invocation_seq (run_id, invocation_seq),
    KEY idx_run_time (run_id, deleted, create_time),
    KEY idx_agent_time (agent_name, deleted, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型调用记录';
```

### 3.3 ai_agent_tool_invocation

记录一次模型决策后真正执行的工具调用。

```sql
CREATE TABLE ai_agent_tool_invocation (
    id                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    run_id               BIGINT        NOT NULL COMMENT 'FK -> ai_agent_dialogue_run.id',
    llm_invocation_id    BIGINT        NOT NULL COMMENT 'FK -> ai_agent_llm_invocation.id',
    tool_call_id         VARCHAR(128)  NOT NULL COMMENT '模型返回的 toolCallId',
    dispatch_index       INT           NOT NULL COMMENT '同一次 llm_invocation 返回的第几个 tool call，从1递增',
    agent_name           VARCHAR(32)   NOT NULL COMMENT '当前执行工具的 agent',
    step_no              INT           NULL COMMENT '当前 agent step 序号',
    tool_name            VARCHAR(128)  NOT NULL COMMENT '工具名',
    tool_provider        VARCHAR(64)   NULL COMMENT '工具提供方，如 local/mcp/python',
    input_json           JSON          NOT NULL COMMENT '工具入参',
    output_text          MEDIUMTEXT    NULL COMMENT '字符串型输出',
    output_json          JSON          NULL COMMENT '结构化输出',
    status               TINYINT       NOT NULL DEFAULT 0 COMMENT '0=运行中 1=成功 2=失败 3=超时',
    error_msg            TEXT          NULL COMMENT '错误信息',
    started_at           DATETIME(3)   NOT NULL COMMENT '开始时间',
    finished_at          DATETIME(3)   NULL COMMENT '结束时间',
    duration_ms          BIGINT        NULL COMMENT '耗时(毫秒)',
    create_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted              TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_tool_call_id (run_id, tool_call_id),
    UNIQUE KEY uk_llm_dispatch_index (llm_invocation_id, dispatch_index),
    KEY idx_llm_invocation (llm_invocation_id, deleted),
    KEY idx_run_started (run_id, deleted, started_at, id),
    KEY idx_tool_name_time (tool_name, deleted, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具调用记录';
```

### 3.4 ai_agent_artifact

记录输入文件与工具产物文件。

```sql
CREATE TABLE ai_agent_artifact (
    id                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    run_id               BIGINT        NOT NULL COMMENT 'FK -> ai_agent_dialogue_run.id',
    tool_invocation_id   BIGINT        NULL COMMENT 'FK -> ai_agent_tool_invocation.id；输入文件时为空',
    tool_call_id         VARCHAR(128)  NULL COMMENT '来源工具调用ID；输入文件时为空',
    artifact_role        VARCHAR(16)   NOT NULL COMMENT 'input | output',
    visibility           VARCHAR(16)   NOT NULL DEFAULT 'visible' COMMENT 'visible | internal',
    source_type          VARCHAR(32)   NOT NULL COMMENT 'user_upload | tool_output',
    source_name          VARCHAR(128)  NULL COMMENT '来源名称：工具名或 user_upload',
    file_name            VARCHAR(256)  NOT NULL COMMENT '文件名',
    storage_key          VARCHAR(512)  NULL COMMENT '稳定资源key或对象存储key',
    download_url         VARCHAR(1024) NULL COMMENT '下载地址',
    preview_url          VARCHAR(1024) NULL COMMENT '预览地址',
    mime_type            VARCHAR(128)  NULL COMMENT 'MIME类型',
    file_size            BIGINT        NULL COMMENT '文件大小(字节)',
    file_hash            VARCHAR(128)  NULL COMMENT '文件哈希，可选',
    metadata_json        JSON          NULL COMMENT '扩展元数据',
    create_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted              TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_tool_storage (run_id, tool_call_id, storage_key),
    KEY idx_run_role_time (run_id, artifact_role, deleted, create_time),
    KEY idx_tool_invocation (tool_invocation_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行输入与工具产物记录';
```

## 4. 写入时序设计

### 4.1 总体原则

- 只在生命周期关键节点写库
- 不按流式 chunk 落库
- 采用“前插后更”，保证异常中断时也有运行中记录可查
- 工具产物在单次工具完成后立即落库，不延迟到全局收尾

### 4.2 写入阶段

#### 阶段 1：请求进入后创建 run

**建议挂点：**

- 请求入口统一装配层
- `AgentContext` 构建完成之后，真正执行 agent 之前

**写入表：**

- `ai_agent_dialogue_run`
- `ai_agent_artifact`（输入文件）

**写入数据：**

- run 基础信息：`run_uid/request_id/session_id/entry_agent/query_text/status=运行中/started_at`
- 输入文件：`artifact_role=input`、`source_type=user_upload`、文件稳定引用信息

#### 阶段 2：每次 `LLM.ask()` / `LLM.askTool()` 调用前插入 LLM 记录

**建议挂点：**

- `LLM.ask()`
- `LLM.askTool()`

**写入表：**

- `ai_agent_llm_invocation`

**写入数据：**

- `run_id`
- `invocation_seq`
- `agent_name`
- `step_no`
- `call_kind`
- `streaming`
- `model_name`
- `started_at`
- `status=运行中`

#### 阶段 3：LLM 调用完成后更新 LLM 记录

**建议挂点：**

- 非流式：future 成功返回后
- 流式：`onComplete` / `onError`

**写入表：**

- 更新 `ai_agent_llm_invocation`

**写入数据：**

- `response_text`
- `tool_call_count`
- `prompt_tokens`
- `completion_tokens`
- `total_tokens`
- `finish_reason`
- `status`
- `error_msg`
- `finished_at`
- `duration_ms`

#### 阶段 4：每次 askTool 返回后，主线程先批量插入 Tool 记录

**建议挂点：**

- `LLM.askTool()` 返回后
- `BaseAgent.executeTools()` 并发分发前

**写入表：**

- `ai_agent_tool_invocation`

**写入数据：**

- `run_id`
- `llm_invocation_id`
- `tool_call_id`
- `dispatch_index`
- `agent_name`
- `step_no`
- `tool_name`
- `tool_provider`
- `input_json`
- `started_at`
- `status=运行中`

**执行要求：**

- 主线程按 `toolCalls` 原始列表顺序批量创建 `RUNNING` 记录
- 本期 `started_at` 记为工具进入执行分发阶段的时间；如果后续需要区分线程排队时间，再单独引入 `queued_at/dispatched_at`
- 创建完成后，把 `tool_call_id -> tool_invocation_id` 映射写入运行态上下文
- 后续工作线程只负责执行工具和更新已有记录，不再在线程内分配序号或补插记录

#### 阶段 5：每次工具执行完成后更新 Tool 记录，并立即写产物

**建议挂点：**

- `BaseAgent.executeTool()` 返回后或异常捕获后
- 如果需要更强约束，可在 `executeTools()` 的工作线程包装层统一做 finally 更新

**写入表：**

- 更新 `ai_agent_tool_invocation`
- 新增 `ai_agent_artifact`

**写入数据：**

- 工具记录：`output_text/output_json/status/error_msg/finished_at/duration_ms`
- 文件记录：通过 `ToolArtifactRegistry` 按 `toolCallId` 收口后，写 `artifact_role=output`

#### 阶段 6：整个 run 完成后回写总账

**建议挂点：**

- 顶层 handler 结束时
- `REACT / PLAN_SOLVE / WORKFLOW` 三条链路都应有统一 finalize

**写入表：**

- 更新 `ai_agent_dialogue_run`

**写入数据：**

- `status`
- `final_summary_text`
- `llm_call_count`
- `tool_call_count`
- `artifact_count`
- `prompt_tokens_total`
- `completion_tokens_total`
- `total_tokens_total`
- `error_code/error_msg`
- `finished_at`
- `duration_ms`

## 5. 代码落点设计

### 5.1 建议新增运行态上下文

建议在 `AgentContext` 中新增一个轻量运行态对象，用于贯穿持久化链路：

```java
@Data
@Builder
public class AgentRunState {
    private Long runId;
    private String runUid;
    private String currentAgentName;
    private Integer currentStepNo;
    private Integer nextLlmInvocationSeq;
    private Long currentLlmInvocationId;
    private ConcurrentMap<String, Long> toolInvocationIdByToolCallId;
}
```

作用：

- 让 `LLM` 层知道当前 `runId`
- 让 `BaseAgent.executeTool()` 能准确拿到当前 `llmInvocationId`
- 让并发工具线程按 `toolCallId` 找到已预创建的 `toolInvocationId`
- 避免在方法签名里层层透传一堆持久化参数

### 5.2 建议新增领域接口

```java
package org.wwz.ai.domain.agent.reactor.agent.recorder;

public interface AgentExecutionRecorder {

    Long createRun(DialogueRunRecord record);

    void finishRun(DialogueRunFinishRecord record);

    Long createLlmInvocation(LlmInvocationStartRecord record);

    void finishLlmInvocation(LlmInvocationFinishRecord record);

    Long createToolInvocation(ToolInvocationStartRecord record);

    void finishToolInvocation(ToolInvocationFinishRecord record);

    void recordArtifacts(List<ArtifactRecord> records);
}
```

接口设计原则：

- “开始”和“结束”分开，天然适配前插后更
- 领域层只传明确结构，不暴露 Mapper / PO / SQL 细节

### 5.3 具体挂点

| 挂点 | 责任 |
|------|------|
| 请求入口统一装配层 | 创建 run，登记输入文件 |
| `LLM.ask()` / `LLM.askTool()` | 创建 / 更新 LLM 调用记录 |
| `askTool()` 返回后、`executeTools()` 分发前 | 按 `toolCalls` 原始顺序批量创建工具调用记录 |
| `BaseAgent.executeTool()` | 更新工具调用记录 |
| `ToolArtifactRegistry` 查询收口 | 按 `toolCallId` 写产物 |
| 顶层 handler | 回写 run 最终状态和聚合指标 |

## 6. 查询示例

### 6.1 查询某次执行的完整链路

```sql
SELECT *
FROM ai_agent_dialogue_run
WHERE request_id = ? AND deleted = 0;
```

```sql
SELECT *
FROM ai_agent_llm_invocation
WHERE run_id = ? AND deleted = 0
ORDER BY invocation_seq;
```

```sql
SELECT *
FROM ai_agent_tool_invocation
WHERE run_id = ? AND deleted = 0
ORDER BY llm_invocation_id, dispatch_index;
```

```sql
SELECT *
FROM ai_agent_artifact
WHERE run_id = ? AND deleted = 0
ORDER BY create_time;
```

如果要看实际执行时间线，则按开始时间排序：

```sql
SELECT *
FROM ai_agent_tool_invocation
WHERE run_id = ? AND deleted = 0
ORDER BY started_at, id;
```

### 6.2 查询某个工具最近的调用情况

```sql
SELECT *
FROM ai_agent_tool_invocation
WHERE tool_name = 'code_interpreter'
  AND deleted = 0
ORDER BY create_time DESC
LIMIT 100;
```

### 6.3 查询某个会话下最近的执行列表

```sql
SELECT *
FROM ai_agent_dialogue_run
WHERE session_id = ?
  AND deleted = 0
ORDER BY create_time DESC;
```

## 7. 风险与取舍

### 7.1 为什么不把所有内容都塞进一张表

因为当前真实链路天然是分层的：

- 一次 run 有多次 LLM 调用
- 一次 LLM 调用可能有多个 tool call
- 一次 tool call 可能产出多个文件

把它们平铺到一张表，会让唯一键、排序、归属关系和扩展性都迅速恶化。

### 7.2 为什么不记录逐 chunk 流式内容

当前目标是执行账本，不是聊天 transcript 账本。

逐 chunk 落库会带来：

- 写放大严重
- 数据噪音高
- 对排查价值有限

保留最终 `response_text` 和完整工具结果即可满足当前目标。

### 7.3 为什么不在 LLM 表重复保存 toolCalls 明细

因为 toolCalls 的真实执行明细已经在 `ai_agent_tool_invocation` 中有更强约束的事实源：

- 有 `tool_call_id`
- 有实际入参
- 有实际出参
- 有执行状态和耗时

LLM 表只需记录这次决策“返回了几个工具调用”和“当时的思考文本”即可。

### 7.4 为什么不坚持 Tool 侧 run 级 invocation_seq

因为在当前实现里，同一次 `askTool()` 返回的多个工具会并发执行。

如果仍然要求 `ai_agent_tool_invocation` 维护 `run` 级全局递增 `invocation_seq`，会出现三个问题：

- 线程调度顺序不等于模型决策顺序
- 在线程里抢号会引入竞争、空洞和补偿复杂度
- 这个字段会同时承担“唯一标识”和“展示顺序”两种职责，语义不稳定

因此本期改为：

- `LLM` 侧保留 `run` 级 `invocation_seq`
- `Tool` 侧改为 `llm_invocation_id + dispatch_index`
- 真正的执行时间线通过 `started_at + id` 还原

## 8. 后续扩展

以下能力不纳入本期主设计，但后续可按需增加：

- `system_prompt_hash`：做 prompt 版本效果归因
- `request_payload_ref`：需要保留完整 prompt 请求体时再引入
- 独立投影表：如果后端或前端需要专门的运行时间线展示，可在源表稳定后追加读模型
- 数据归档 / 分表：当 `ai_agent_tool_invocation` 数据量大幅增长时再做

## 9. 最终结论

本期推荐方案是：

1. 以 `run` 为唯一执行聚合根
2. 用 4 张表拆分运行总账、LLM 调用、Tool 调用、文件产物
3. LLM 表只存完整文本响应和指标，不存 excerpt / raw payload
4. Tool 表承担入参与出参真相源
5. Tool 表不维护 run 级全局 seq，而是使用 `llm_invocation_id + dispatch_index`
6. 文件表直接显式关联到 `run` 和 `tool_invocation`
7. 采用“前插后更”的同步实时写入策略，工具记录由主线程预插入，工作线程只更新

这套方案与当前代码实际结构一致，约束清晰，后续扩展成本也最低。

# 工具输出重构为独立表实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 8 个 rich tool 的结构化输出从 `ToolResultPayload.outputJson` 与 `ai_agent_tool_invocation.output_json` 中彻底移除，改为强类型输出模型 + 独立工具输出表持久化；历史回放链路只读新表，不保留任何 dual-read / converter / 兼容分支。

**Architecture:** rich tool 不再产出 `output_json` 字符串，而是直接产出 `ToolStructuredOutput` 子类型；`BaseAgent -> ToolInvocationFinishRecord -> ToolOutputWriter` 全链路透传强类型输出对象，写入 8 张工具输出表。`deep_search` 采用“主表字段 + `stages_json`”方案保留阶段级回放能力；默认 projector 退化为只基于 `llmObservation` / `errorMsg` 的纯文本 fallback，不再依赖账本 JSON。

**Tech Stack:** Java 17, Spring Boot 3.4.3, MyBatis / MyBatis-Plus 3.5.14, MySQL 8

---

## 设计边界

### 本次重构的硬约束

- 不保留 `ai_agent_tool_invocation.output_json`
- 不做 dual-read：projector 只读新表
- 不做 `output_json -> PO` Converter
- 不做历史兼容分支；本次是主路径重构，不是平滑迁移
- `domain` 层不返回 `infrastructure PO`，读写契约统一使用 `domain` 自己的快照模型
- rich tool 的失败语义一并收口：工具返回失败结果时，账本状态必须明确落为 `FAILED` / `TIMEOUT`，不能再靠“错误文本但状态成功”的隐式行为

### 关键设计决策

1. `ToolResultPayload` 去掉 `outputJson`，改为 `ToolStructuredOutput structuredOutput`
2. `ToolInvocationFinishRecord` 增加 `runId`、`sessionId`、`toolName`、`structuredOutput`
3. `ToolOutputWriter` 直接按 `ToolStructuredOutput` 类型写表，不经过 JSON 解析
4. `ToolOutputReader` 返回 `domain` 快照，不返回 `PO`
5. `ToolInvocationView` 删除 `outputJson`
6. `DefaultToolInvocationProjector` 只基于 `llmObservation` / `errorMsg` 组装 `tool_result`
7. `deep_search` 保留 `stages_json`，不拆 stage 子表

---

## 表结构复核结论

### 统一公共列

8 张工具输出表统一保留下列公共列：

- `id`
- `tool_invocation_id`
- `run_id`
- `request_id`
- `session_id`
- `tool_call_id`
- `status`
- `error_msg`
- `created_at`
- `updated_at`

统一索引策略：

- `UNIQUE KEY uk_tool_invocation (tool_invocation_id)`
- `UNIQUE KEY uk_request_tool_call (request_id, tool_call_id)`
- `KEY idx_run_created (run_id, created_at DESC)`
- `KEY idx_status_created (status, created_at DESC)`

这样设计的原因：

- `tool_invocation_id` 解决 agent 主链路回放查询
- `request_id + tool_call_id` 解决“前端直接调用工具”这类无 `tool_invocation_id` 的独立查询
- `run_id` 支撑单次执行链路聚合
- `status/error_msg` 让每张工具表都能独立表达终态，不再依赖主账本的 `output_json`

### 1. `deep_search`

**建议表：** `ai_agent_tool_output_deep_search`

**字段：**

- `query VARCHAR(512)`
- `answer_summary TEXT`
- `stages_json JSON`

**结论：合理，且应当保留 `stages_json`。**

原因：

- 当前 `DeepSearchToolInvocationProjector` 需要还原 `extend/search/report` 三类阶段事件
- 现有 `DeepSearchStructuredResultBuilder` 的核心结构就是 `query + stages + final answer`
- 如果只留 `query/answer_summary`，历史回放会降级
- 这里不建议再拆 stage 子表，因为 stage 结构本身嵌套多层 `queries/results/docs`，关系化收益低、复杂度高；当前消费方只需要“按 invocation 一次性回放整段 stages”，单列 JSON 更贴合现状

### 2. `file_tool`

**建议表：** `ai_agent_tool_output_file_tool`

**字段：**

- `command VARCHAR(16)`
- `primary_file_name VARCHAR(256)`
- `content_storage_mode VARCHAR(32)`
- `file_refs_json JSON`

**结论：合理，但不要只存 `file_name`。**

原因：

- 当前输出主字段是 `command + fileInfo + contentStorageMode`
- 顶层并没有稳定的 `fileName` 字段，文件信息在数组里
- `primary_file_name` 可以作为单文件主索引字段，便于检索；完整结果仍以 `file_refs_json` 保存

### 3. `code_interpreter`

**建议表：** `ai_agent_tool_output_code_interpreter`

**字段：**

- `code_output MEDIUMTEXT`
- `content MEDIUMTEXT`
- `code MEDIUMTEXT`
- `explain MEDIUMTEXT`
- `file_refs_json JSON`

**结论：合理。**

原因：

- 当前输出就是这 4 段文本 + 文件列表
- `stdout` / `rendered content` / `code` 都可能偏长，使用 `MEDIUMTEXT` 比 `TEXT` 更稳妥

### 4. `report_tool`

**建议表：** `ai_agent_tool_output_report_tool`

**字段：**

- `file_type VARCHAR(32)`
- `summary TEXT`
- `content MEDIUMTEXT`
- `file_refs_json JSON`

**结论：合理。**

原因：

- 当前输出语义是 `fileType + summary + data + fileInfo`
- 本次重构可以把当前 `data` 正名为 `content`
- `file_type` 保留 `html / markdown / ppt`

### 5. `data_analysis`

**建议表：** `ai_agent_tool_output_data_analysis`

**字段：**

- `task TEXT`
- `summary TEXT`
- `content MEDIUMTEXT`
- `file_refs_json JSON`

**结论：合理。**

原因：

- 当前输出语义是 `task + summary + data + fileInfo`
- 与 `report_tool` 一样，`data` 统一落为 `content` 更清晰

### 6. `multimodalagent_tool`

**建议表：** `ai_agent_tool_output_multimodal_agent`

**字段：**

- `summary TEXT`
- `markdown_content MEDIUMTEXT`
- `file_refs_json JSON`

**结论：合理，且表名应改为可读的 snake_case。**

原因：

- 当前输出语义是 `summary + markdown + fileInfo`
- `markdown` 可能明显长于摘要，单独使用 `markdown_content`
- 原计划中的 `multimodalagent` 连写表名可读性较差

### 7. `image_generation_tool`

**建议表：** `ai_agent_tool_output_image_generation`

**字段：**

- `prompt TEXT`
- `mode VARCHAR(32)`
- `summary TEXT`
- `file_refs_json JSON`

**结论：合理。**

原因：

- 当前输出就是 `prompt + mode + summary + fileInfo`
- 图片文件列表天然是数组，保留 `file_refs_json` 合适

### 8. `script_runner_tool`

**建议表：** `ai_agent_tool_output_script_runner`

**字段：**

- `skill_name VARCHAR(128)`
- `script_name VARCHAR(128)`
- `runtime VARCHAR(32)`
- `success TINYINT(1)`
- `exit_code INT`
- `stdout MEDIUMTEXT`
- `stderr MEDIUMTEXT`
- `summary TEXT`
- `file_refs_json JSON`

**结论：合理，但 `stdout/stderr` 应提升为 `MEDIUMTEXT`。**

原因：

- 脚本输出长度不可控，`TEXT` 太容易截断
- `skill_name + script_name` 是直观的业务主键组合，但不替代全局查询键

---

## 文件总览

### 数据库

- Modify: `ai-agent-station-study-app/src/main/resources/db/schema.sql`

### 删除 / 收缩旧契约

- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/ToolResultPayload.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ToolOutputJsonBuilder.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/ToolInvocation.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/ToolInvocationView.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/ToolInvocationFinishRecord.java`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/tool_invocation_ledger_mapper.xml`

### 新增强类型输出模型（domain）

- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ToolStructuredOutput.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ToolFileRef.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/DeepSearchToolOutput.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/DeepSearchStage.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/DeepSearchDoc.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/FileToolOutput.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/CodeInterpreterToolOutput.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ReportToolOutput.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/DataAnalysisToolOutput.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/MultimodalAgentToolOutput.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ImageGenerationToolOutput.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ScriptRunnerToolOutput.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ToolOutputPersistCommand.java`

### 工具输出读写契约（domain）

- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/tooloutput/ToolOutputWriter.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/tooloutput/ToolOutputReader.java`

### 工具输出表 PO（infrastructure）

- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ToolOutputDeepSearchPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ToolOutputFileToolPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ToolOutputCodeInterpreterPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ToolOutputReportToolPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ToolOutputDataAnalysisPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ToolOutputMultimodalAgentPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ToolOutputImageGenerationPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ToolOutputScriptRunnerPO.java`

### Mapper（domain）

- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ToolOutputDeepSearchMapper.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ToolOutputFileToolMapper.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ToolOutputCodeInterpreterMapper.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ToolOutputReportToolMapper.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ToolOutputDataAnalysisMapper.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ToolOutputMultimodalAgentMapper.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ToolOutputImageGenerationMapper.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ToolOutputScriptRunnerMapper.java`

### 读写实现（infrastructure）

- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputWriterImpl.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputReaderImpl.java`

### rich tool 输出构建改造

- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DeepSearchStructuredResultBuilder.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DeepSearchTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/FileTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/CodeInterpreterTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ReportTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DataAnalysisTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/MultiModalAgent.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ImageGenerationTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/ScriptRunnerTool.java`

### 运行链路改造

- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java`

### Projector 改造

- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/DefaultToolInvocationProjector.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/FileToolInvocationProjector.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/DeepSearchToolInvocationProjector.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/CodeInterpreterToolInvocationProjector.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/DataAnalysisToolInvocationProjector.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/ImageGenerationToolInvocationProjector.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/MultiModalToolInvocationProjector.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/ReportToolInvocationProjector.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/ScriptRunnerToolInvocationProjector.java`

### 测试

- Delete: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolOutputJsonBuilderTest.java`
- Delete: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolOutputJsonRuntimeTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolInvocationProjectorTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/DeepSearchLlmObservationTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/MultiModalAgentToolTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ImageGenerationToolTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ScriptRunnerToolTest.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolStructuredOutputWriterTest.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolStructuredOutputReaderTest.java`

---

## 任务拆分

### Task 1: 重写 schema，移除主账本 `output_json`

**Files:**

- Modify: `ai-agent-station-study-app/src/main/resources/db/schema.sql`

- [ ] **Step 1: 删除 `ai_agent_tool_invocation.output_json` 列**

同时同步移除相关索引 / 注释中的 JSON 输出表述，主账本只保留：

- `input_json`
- `llm_oberserve`
- `status`
- `error_msg`

- [ ] **Step 2: 在 schema.sql 追加 8 张工具输出表**

表命名与字段按“表结构复核结论”落地。

其中 `deep_search` 表必须包含：

```sql
query           VARCHAR(512) NULL,
answer_summary  TEXT NULL,
stages_json     JSON NULL
```

`script_runner` 表必须使用：

```sql
stdout          MEDIUMTEXT NULL,
stderr          MEDIUMTEXT NULL
```

- [ ] **Step 3: 为 8 张表加统一唯一键与查询索引**

```sql
UNIQUE KEY uk_tool_invocation (tool_invocation_id),
UNIQUE KEY uk_request_tool_call (request_id, tool_call_id),
KEY idx_run_created (run_id, created_at DESC),
KEY idx_status_created (status, created_at DESC)
```

- [ ] **Step 4: 本地执行 schema 校验**

Run: `mvn -pl ai-agent-station-study-app -DskipTests compile`

Expected: BUILD SUCCESS

### Task 2: 建立强类型工具输出模型，删除 `output_json` 契约

**Files:**

- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/ToolResultPayload.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ToolOutputJsonBuilder.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ToolStructuredOutput.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ToolFileRef.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/*.java`

- [ ] **Step 1: 定义 `ToolStructuredOutput` 根接口**

```java
public interface ToolStructuredOutput {

    String getToolName();
}
```

- [ ] **Step 2: 定义共享文件引用模型 `ToolFileRef`**

字段统一为：

- `fileName`
- `downloadUrl`
- `previewUrl`
- `ossUrl`
- `domainUrl`
- `fileSize`

- [ ] **Step 3: 为 8 个 rich tool 定义独立输出模型**

示例：

```java
@Data
@Builder
public class FileToolOutput implements ToolStructuredOutput {

    private String command;
    private String primaryFileName;
    private String contentStorageMode;
    private List<ToolFileRef> fileRefs;

    @Override
    public String getToolName() {
        return "file_tool";
    }
}
```

```java
@Data
@Builder
public class DeepSearchToolOutput implements ToolStructuredOutput {

    private String query;
    private String answerSummary;
    private List<DeepSearchStage> stages;

    @Override
    public String getToolName() {
        return "deep_search";
    }
}
```

- [ ] **Step 4: 重写 `ToolResultPayload`**

目标结构：

```java
@Data
@Builder
public class ToolResultPayload {

    private String toolResult;

    private String llmObservation;

    private ToolStructuredOutput structuredOutput;

    private boolean failed;

    private String errorMsg;
}
```

- [ ] **Step 5: 提供新的工厂方法**

- `text(String resultText)`
- `rich(String toolResult, String llmObservation, ToolStructuredOutput structuredOutput)`
- `failure(String toolResult, String llmObservation, String errorMsg, ToolStructuredOutput structuredOutput)`

### Task 3: 改造 8 个 rich tool，直接返回强类型输出

**Files:**

- Modify: `DeepSearchStructuredResultBuilder.java`
- Modify: `DeepSearchTool.java`
- Modify: `FileTool.java`
- Modify: `CodeInterpreterTool.java`
- Modify: `ReportTool.java`
- Modify: `DataAnalysisTool.java`
- Modify: `MultiModalAgent.java`
- Modify: `ImageGenerationTool.java`
- Modify: `ScriptRunnerTool.java`

- [ ] **Step 1: `deep_search` 改为返回 `DeepSearchToolOutput`**

`DeepSearchStructuredResultBuilder` 不再生成 `outputJson` 字符串，而是直接构造：

- `query`
- `answerSummary`
- `stages`

同时保留当前紧凑 `llmObservation` 逻辑。

- [ ] **Step 2: `file_tool` 改为返回 `FileToolOutput`**

输出对象中直接填充：

- `command`
- `primaryFileName`
- `contentStorageMode`
- `fileRefs`

- [ ] **Step 3: 其余 6 个 tool 全部停止调用 `ToolOutputJsonBuilder`**

逐个改为 `ToolResultPayload.rich(...)`：

- `CodeInterpreterToolOutput`
- `ReportToolOutput`
- `DataAnalysisToolOutput`
- `MultimodalAgentToolOutput`
- `ImageGenerationToolOutput`
- `ScriptRunnerToolOutput`

- [ ] **Step 4: rich tool 的失败结果必须显式标记 `failed=true`**

不要再返回“看起来是错误 JSON，但 payload 仍是 success”的结构。

### Task 4: 改造运行链路，主账本只记录公共元数据

**Files:**

- Modify: `BaseAgent.java`
- Modify: `ToolInvocationFinishRecord.java`
- Modify: `AgentExecutionRecorderImpl.java`
- Modify: `ToolInvocation.java`
- Modify: `ToolInvocationView.java`
- Modify: `tool_invocation_ledger_mapper.xml`

- [ ] **Step 1: `BaseAgent` 停止构建 / 透传 `output_json`**

`normalizeToolResultPayload` 改为只归一化：

- `toolResult`
- `llmObservation`
- `structuredOutput`
- `failed`
- `errorMsg`

- [ ] **Step 2: `ToolExecutionOutcome` 删除 `outputJson`**

同时增加：

- `ToolStructuredOutput structuredOutput`
- `String toolName`
- `Long runId`
- `String sessionId`

- [ ] **Step 3: `ToolInvocationFinishRecord` 删除 `outputJson`，增加运行期上下文**

最少新增：

- `runId`
- `sessionId`
- `toolName`
- `ToolStructuredOutput structuredOutput`

- [ ] **Step 4: `AgentExecutionRecorderImpl.finishToolInvocation` 只更新主账本公共列**

更新主账本时不再写：

- `output_json`

只写：

- `status`
- `llm_oberserve`
- `error_msg`
- `finished_at`

随后调用：

```java
toolOutputWriter.write(ToolOutputPersistCommand.builder()
        .toolInvocationId(record.getToolInvocationId())
        .runId(record.getRunId())
        .requestId(record.getRequestId())
        .sessionId(record.getSessionId())
        .toolCallId(record.getToolCallId())
        .status(record.getStatus())
        .errorMsg(record.getErrorMsg())
        .structuredOutput(record.getStructuredOutput())
        .build());
```

- [ ] **Step 5: `ToolInvocation` / `ToolInvocationView` / XML mapper 删除 `outputJson` 字段映射**

### Task 5: 实现工具输出表的直接写入与读取

**Files:**

- Create: `ToolOutputWriter.java`
- Create: `ToolOutputReader.java`
- Create: 8 个 `PO`
- Create: 8 个 `Mapper`
- Create: `ToolOutputWriterImpl.java`
- Create: `ToolOutputReaderImpl.java`

- [ ] **Step 1: 定义 `ToolOutputPersistCommand`**

```java
@Data
@Builder
public class ToolOutputPersistCommand {

    private Long toolInvocationId;
    private Long runId;
    private String requestId;
    private String sessionId;
    private String toolCallId;
    private Integer status;
    private String errorMsg;
    private ToolStructuredOutput structuredOutput;
}
```

- [ ] **Step 2: `ToolOutputWriter` 接受强类型持久化命令**

```java
public interface ToolOutputWriter {

    void write(ToolOutputPersistCommand command);
}
```

- [ ] **Step 3: `ToolOutputReader` 返回 domain 输出模型**

```java
public interface ToolOutputReader {

    Optional<DeepSearchToolOutput> readDeepSearch(Long toolInvocationId);

    Optional<FileToolOutput> readFileTool(Long toolInvocationId);

    Optional<CodeInterpreterToolOutput> readCodeInterpreter(Long toolInvocationId);

    Optional<ReportToolOutput> readReportTool(Long toolInvocationId);

    Optional<DataAnalysisToolOutput> readDataAnalysis(Long toolInvocationId);

    Optional<MultimodalAgentToolOutput> readMultimodalAgent(Long toolInvocationId);

    Optional<ImageGenerationToolOutput> readImageGeneration(Long toolInvocationId);

    Optional<ScriptRunnerToolOutput> readScriptRunner(Long toolInvocationId);
}
```

- [ ] **Step 4: `ToolOutputWriterImpl` 直接按类型分发表写入**

这里允许 `instanceof` + 私有方法，不再创建 converter 类。

示例：

```java
if (output instanceof DeepSearchToolOutput deepSearch) {
    saveDeepSearch(command, deepSearch);
    return;
}
```

- [ ] **Step 5: Writer 使用 upsert 语义**

同一个 `tool_invocation_id` 或同一个 `request_id + tool_call_id` 只能保留一条终态记录。

- [ ] **Step 6: `ToolOutputReaderImpl` 负责 `PO <-> domain` 的常规仓储映射**

注意：

- 这是仓储职责，不是兼容 converter
- `domain` 不得直接暴露 `PO`

### Task 6: 改造 replay projector，只读新表

**Files:**

- Modify: 9 个 projector
- Modify: `ReplayProjectorTest.java`
- Modify: `ToolInvocationProjectorTest.java`

- [ ] **Step 1: `DefaultToolInvocationProjector` 删除 `readJson(outputJson)`**

fallback 逻辑改为：

1. 优先 `llmObservation`
2. 再退到 `errorMsg`
3. `toolParam` 仍然读 `inputJson`

- [ ] **Step 2: 8 个 rich projector 注入 `ToolOutputReader`**

每个 projector 都按如下模式改造：

1. 通过 `toolInvocationId` 查询工具输出快照
2. 从快照字段组装 `ProjectedReplayEvent`
3. 不再解析任何 `output_json`

- [ ] **Step 3: `DeepSearchToolInvocationProjector` 基于 `stages_json` 还原阶段事件**

reader 负责把 `stages_json` 反序列化为 `List<DeepSearchStage>`，projector 只做事件投影。

- [ ] **Step 4: 文件类 tool 的 `fileInfo` 统一来自 `file_refs_json + artifact` 合并**

运行期有 artifact 时，继续复用现有稳定链接合并逻辑；
但 rich output 表本身必须保存一份 `file_refs_json`，以支持 direct tool call。

### Task 7: 清理旧测试并补新测试

**Files:**

- Delete: `ToolOutputJsonBuilderTest.java`
- Delete: `ToolOutputJsonRuntimeTest.java`
- Modify / Create: 其余测试文件

- [ ] **Step 1: 删除所有围绕 `output_json` 的断言**

包括：

- `contains("\"schemaVersion\":1")`
- `contains("\"resultType\":\"plain_text\"")`
- `contains("\"resultType\":\"error\"")`

- [ ] **Step 2: rich tool 单测改为断言 `structuredOutput`**

例如：

- `ImageGenerationToolTest` 校验 `payload.getStructuredOutput()` 中的 `prompt/mode/fileRefs`
- `ScriptRunnerToolTest` 校验 `skillName/scriptName/stdout/stderr`
- `DeepSearchLlmObservationTest` 校验 `stages` 与紧凑 `llmObservation`

- [ ] **Step 3: projector 测试改为 mock `ToolOutputReader`**

测试目标改为：

- registry 按 `toolName` 分发
- projector 使用 reader 的快照数据组装事件
- default projector 不依赖 `outputJson`

- [ ] **Step 4: 新增 `ToolStructuredOutputWriterTest`**

校验：

- 8 个输出类型都能命中正确保存分支
- `FAILED/TIMEOUT` 状态会带上 `error_msg`
- `upsert` 不会插入重复行

### Task 8: 回归验证

**Files:**

- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/**`

- [ ] **Step 1: 编译 domain + infrastructure**

Run: `mvn clean compile -pl ai-agent-station-study-domain,ai-agent-station-study-infrastructure -am`

Expected: BUILD SUCCESS

- [ ] **Step 2: 运行账本与 projector 回归**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest,ToolInvocationProjectorTest,ReplayProjectorTest -DskipTests=false`

Expected: 所有测试 PASS

- [ ] **Step 3: 运行 rich tool 相关回归**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=DeepSearchLlmObservationTest,MultiModalAgentToolTest,ImageGenerationToolTest,ScriptRunnerToolTest -DskipTests=false`

Expected: 所有测试 PASS

---

## 自检清单

### 1. 需求覆盖检查

- [x] 不保留主账本 `output_json`
- [x] 不做 dual-read
- [x] 不做 converter
- [x] rich tool 改为强类型结构化输出
- [x] `deep_search` 保留阶段级回放能力
- [x] projector 只读新表
- [x] direct tool call 可通过 `request_id + tool_call_id` 查询
- [x] `domain` 不返回 `infrastructure PO`

### 2. 表结构复核结果

- [x] `deep_search` 增加 `stages_json`
- [x] `file_tool` 使用 `primary_file_name + file_refs_json`
- [x] `code_interpreter` 使用 4 段 `MEDIUMTEXT`
- [x] `report_tool` / `data_analysis` 统一使用 `content`
- [x] `multimodal_agent` 使用 `markdown_content`
- [x] `image_generation` 保留 `prompt/mode/summary`
- [x] `script_runner` 的 `stdout/stderr` 提升为 `MEDIUMTEXT`

### 3. 风险前置说明

- [x] 这是主路径重构，不处理历史旧数据兼容
- [x] 依赖 `output_json` 的旧测试必须整体重写
- [x] rich tool 失败状态语义会被收紧，部分现有测试断言需要同步调整

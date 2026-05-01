# 工具输出拆分独立表设计

## 1. 背景与目标

### 1.1 背景

当前 `ai_agent_tool_invocation` 表的 `output_json` 列以 JSON 格式统一存储所有工具的执行结果。虽然已有 `tool-output-json-unification` 规范对 JSON 结构做了统一约束（`schemaVersion`、`resultType`、不含前端字段等），但本质上仍然是**同一张表、同一个 JSON 列承载异构数据**。

现状的问题：

- 工具输出是"领域事实"，却被压缩在 JSON 列中，无法利用数据库的类型约束和索引能力
- 前端直接调用工具时，数据也必须塞进 `ai_agent_tool_invocation` 的 JSON 列，与 Agent 执行链强耦合
- Projector 读侧需要按 `tool_name` 分发后再解析 JSON，读路径依赖 JSON 反序列化
- 各工具的字段语义被埋在 JSON 中，不利于 BI 分析、运营查询、问题排查

### 1.2 目标

将工具执行结果从 `ai_agent_tool_invocation.output_json` 中解耦出来，为每个 rich tool 建立独立的输出结果表：

1. 每个工具的输出有明确的字段定义，享受关系型数据库的类型约束和索引能力
2. 工具输出成为独立的领域实体，前端直接调用工具时也能直接落库，不依赖 Agent 执行链
3. Projector 读侧直接查字段，不再解析 JSON
4. 彻底废弃 `ai_agent_tool_invocation.output_json` 列

### 1.3 非目标

- 不改造 `ai_agent_tool_invocation` 的其他字段（`input_json`、`llm_oberserve` 等保留）
- 不改动 `ai_agent_artifact` 表结构和职责
- 不改写历史旧数据（旧 `output_json` 保留但不参与新路径）
- 不引入消息队列或异步写入

## 2. 核心设计结论

### 2.1 每个 rich tool 独立一张输出表

为当前 8 个 rich tool 各创建一张输出结果表：

| 表名 | 对应工具 |
|------|---------|
| `tool_output_deep_search` | deep_search |
| `tool_output_file_tool` | file_tool |
| `tool_output_code_interpreter` | code_interpreter |
| `tool_output_report_tool` | report_tool |
| `tool_output_data_analysis` | data_analysis |
| `tool_output_multimodalagent` | multimodalagent_tool |
| `tool_output_image_generation` | image_generation_tool |
| `tool_output_script_runner` | script_runner_tool |

### 2.2 统一公共字段 + 工具特有字段

每张表都包含一套公共字段（关联、状态、时间戳），再加各自工具的特有业务字段。

### 2.3 直接废弃 `output_json`，不设过渡期

新写入路径完全不写 `output_json`，所有 projector 和读侧同步迁移到新表。旧数据中的 `output_json` 保留但不再被新代码读取。

### 2.4 `tool_invocation_id` 可为空

支持前端直接调用工具的场景：工具输出可以独立落库，不强制关联到一次 Agent 执行链中的 `tool_invocation` 记录。

### 2.5 文件引用保留轻量 JSON 列

各工具的文件引用信息（`file_refs_json`）保留为 JSON 列。理由是：

- 文件本体和稳定链接已在 `ai_agent_artifact` 表中
- 输出表中的文件信息只是对 artifact 的轻量引用（文件名、URL、大小）
- 各工具的文件引用结构高度一致，规范化收益有限

## 3. 表结构设计

### 3.1 公共字段说明

以下字段出现在所有工具输出表中：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK AUTO_INCREMENT | 主键 |
| `tool_invocation_id` | BIGINT NULL | 关联 ai_agent_tool_invocation.id，前端直接调用时可为空 |
| `run_id` | BIGINT NOT NULL | 关联 ai_agent_dialogue_run.id，独立调用时可为 0 |
| `tool_call_id` | VARCHAR(128) | 工具调用标识 |
| `status` | TINYINT NOT NULL DEFAULT 0 | 0=RUNNING, 1=SUCCESS, 2=FAILED, 3=TIMEOUT |
| `error_msg` | TEXT | 错误信息 |
| `created_at` | DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) | 创建时间 |
| `updated_at` | DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) | 更新时间 |

### 3.2 tool_output_deep_search

```sql
CREATE TABLE tool_output_deep_search (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id   BIGINT         NULL COMMENT '关联 ai_agent_tool_invocation.id',
    run_id               BIGINT         NOT NULL DEFAULT 0 COMMENT '关联 ai_agent_dialogue_run.id',
    tool_call_id         VARCHAR(128)   NULL COMMENT '工具调用标识',
    query                VARCHAR(512)   NULL COMMENT '搜索查询',
    answer_summary       TEXT           NULL COMMENT '搜索结果摘要（截断后）',
    file_refs_json       JSON           NULL COMMENT '产出文件引用',
    status               TINYINT        NOT NULL DEFAULT 0 COMMENT '0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT',
    error_msg            TEXT           NULL COMMENT '错误信息',
    created_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tool_invocation (tool_invocation_id),
    KEY idx_run (run_id),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='deep_search 工具输出结果';
```

### 3.3 tool_output_file_tool

```sql
CREATE TABLE tool_output_file_tool (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id   BIGINT         NULL COMMENT '关联 ai_agent_tool_invocation.id',
    run_id               BIGINT         NOT NULL DEFAULT 0 COMMENT '关联 ai_agent_dialogue_run.id',
    tool_call_id         VARCHAR(128)   NULL COMMENT '工具调用标识',
    command              VARCHAR(16)    NULL COMMENT 'upload / get',
    file_name            VARCHAR(256)   NULL COMMENT '文件名',
    content_storage_mode VARCHAR(16)    NULL COMMENT 'artifact_only 等',
    file_refs_json       JSON           NULL COMMENT '文件引用',
    status               TINYINT        NOT NULL DEFAULT 0 COMMENT '0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT',
    error_msg            TEXT           NULL COMMENT '错误信息',
    created_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tool_invocation (tool_invocation_id),
    KEY idx_run (run_id),
    KEY idx_command (command),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='file_tool 工具输出结果';
```

### 3.4 tool_output_code_interpreter

```sql
CREATE TABLE tool_output_code_interpreter (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id   BIGINT         NULL COMMENT '关联 ai_agent_tool_invocation.id',
    run_id               BIGINT         NOT NULL DEFAULT 0 COMMENT '关联 ai_agent_dialogue_run.id',
    tool_call_id         VARCHAR(128)   NULL COMMENT '工具调用标识',
    code_output          TEXT           NULL COMMENT '代码执行输出',
    content              TEXT           NULL COMMENT '内容',
    code                 TEXT           NULL COMMENT '执行的代码',
    explain              TEXT           NULL COMMENT '代码解释',
    file_refs_json       JSON           NULL COMMENT '产出文件引用',
    status               TINYINT        NOT NULL DEFAULT 0 COMMENT '0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT',
    error_msg            TEXT           NULL COMMENT '错误信息',
    created_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tool_invocation (tool_invocation_id),
    KEY idx_run (run_id),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='code_interpreter 工具输出结果';
```

### 3.5 tool_output_report_tool

```sql
CREATE TABLE tool_output_report_tool (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id   BIGINT         NULL COMMENT '关联 ai_agent_tool_invocation.id',
    run_id               BIGINT         NOT NULL DEFAULT 0 COMMENT '关联 ai_agent_dialogue_run.id',
    tool_call_id         VARCHAR(128)   NULL COMMENT '工具调用标识',
    file_type            VARCHAR(32)    NULL COMMENT '文件类型 html/markdown/ppt',
    summary              TEXT           NULL COMMENT '内容摘要',
    content              MEDIUMTEXT     NULL COMMENT '报告正文',
    file_refs_json       JSON           NULL COMMENT '产出文件引用',
    status               TINYINT        NOT NULL DEFAULT 0 COMMENT '0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT',
    error_msg            TEXT           NULL COMMENT '错误信息',
    created_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tool_invocation (tool_invocation_id),
    KEY idx_run (run_id),
    KEY idx_file_type (file_type),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='report_tool 工具输出结果';
```

### 3.6 tool_output_data_analysis

```sql
CREATE TABLE tool_output_data_analysis (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id   BIGINT         NULL COMMENT '关联 ai_agent_tool_invocation.id',
    run_id               BIGINT         NOT NULL DEFAULT 0 COMMENT '关联 ai_agent_dialogue_run.id',
    tool_call_id         VARCHAR(128)   NULL COMMENT '工具调用标识',
    task                 TEXT           NULL COMMENT '分析任务描述',
    summary              TEXT           NULL COMMENT '结果摘要',
    content              MEDIUMTEXT     NULL COMMENT '分析结果正文',
    file_refs_json       JSON           NULL COMMENT '产出文件引用',
    status               TINYINT        NOT NULL DEFAULT 0 COMMENT '0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT',
    error_msg            TEXT           NULL COMMENT '错误信息',
    created_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tool_invocation (tool_invocation_id),
    KEY idx_run (run_id),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='data_analysis 工具输出结果';
```

### 3.7 tool_output_multimodalagent

```sql
CREATE TABLE tool_output_multimodalagent (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id   BIGINT         NULL COMMENT '关联 ai_agent_tool_invocation.id',
    run_id               BIGINT         NOT NULL DEFAULT 0 COMMENT '关联 ai_agent_dialogue_run.id',
    tool_call_id         VARCHAR(128)   NULL COMMENT '工具调用标识',
    summary              TEXT           NULL COMMENT '结果摘要',
    markdown             MEDIUMTEXT     NULL COMMENT 'Markdown 格式结果',
    file_refs_json       JSON           NULL COMMENT '产出文件引用',
    status               TINYINT        NOT NULL DEFAULT 0 COMMENT '0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT',
    error_msg            TEXT           NULL COMMENT '错误信息',
    created_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tool_invocation (tool_invocation_id),
    KEY idx_run (run_id),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='multimodalagent_tool 工具输出结果';
```

### 3.8 tool_output_image_generation

```sql
CREATE TABLE tool_output_image_generation (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id   BIGINT         NULL COMMENT '关联 ai_agent_tool_invocation.id',
    run_id               BIGINT         NOT NULL DEFAULT 0 COMMENT '关联 ai_agent_dialogue_run.id',
    tool_call_id         VARCHAR(128)   NULL COMMENT '工具调用标识',
    prompt               TEXT           NULL COMMENT '图片生成提示词',
    mode                 VARCHAR(16)    NULL COMMENT 'images / edits',
    summary              TEXT           NULL COMMENT '结果摘要',
    file_refs_json       JSON           NULL COMMENT '产出图片文件引用',
    status               TINYINT        NOT NULL DEFAULT 0 COMMENT '0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT',
    error_msg            TEXT           NULL COMMENT '错误信息',
    created_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tool_invocation (tool_invocation_id),
    KEY idx_run (run_id),
    KEY idx_mode (mode),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='image_generation_tool 工具输出结果';
```

### 3.9 tool_output_script_runner

```sql
CREATE TABLE tool_output_script_runner (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id   BIGINT         NULL COMMENT '关联 ai_agent_tool_invocation.id',
    run_id               BIGINT         NOT NULL DEFAULT 0 COMMENT '关联 ai_agent_dialogue_run.id',
    tool_call_id         VARCHAR(128)   NULL COMMENT '工具调用标识',
    skill_name           VARCHAR(128)   NULL COMMENT 'Skill 名称',
    script_name          VARCHAR(128)   NULL COMMENT '脚本名称',
    runtime              VARCHAR(32)    NULL COMMENT '运行时环境',
    success              TINYINT(1)     NULL COMMENT '是否成功',
    exit_code            INT            NULL COMMENT '进程退出码',
    stdout               TEXT           NULL COMMENT '标准输出',
    stderr               TEXT           NULL COMMENT '标准错误',
    summary              TEXT           NULL COMMENT '执行摘要',
    file_refs_json       JSON           NULL COMMENT '产出文件引用',
    status               TINYINT        NOT NULL DEFAULT 0 COMMENT '0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT',
    error_msg            TEXT           NULL COMMENT '错误信息',
    created_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tool_invocation (tool_invocation_id),
    KEY idx_run (run_id),
    KEY idx_skill_script (skill_name, script_name),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='script_runner_tool 工具输出结果';
```

## 4. 数据流设计

### 4.1 Agent 执行链写路径

```
工具执行完成
    |
    v
ToolResultPayload（保持现有，但 outputJson 不再落库）
    |
    v
ToolOutputWriter.write(toolName, toolInvocationId, runId, payload)
    |
    v
按 tool_name 从 Registry 获取对应 ToolOutputDao
    |
    v
解析 ToolResultPayload 为对应 PO
    |
    v
写入对应工具输出表
```

### 4.2 前端直接调用工具写路径

```
前端调用 POST /api/tool/{toolName}
    |
    v
直接执行工具逻辑
    |
    v
ToolOutputWriter.write(toolName, null, 0, payload)
    |
    v
写入对应工具输出表（tool_invocation_id 为空）
    |
    v
返回记录 id 给前端
```

### 4.3 Projector 读路径

```
ReplayProjector 需要重建 tool 事件
    |
    v
从 ai_agent_tool_invocation 读出 tool 列表
    |
    v
对每个 tool，调用 ToolInvocationProjector
    |
    v
projector 从对应工具输出表读字段
    |
    v
组装成 ProjectedReplayEvent
```

## 5. 代码结构设计

### 5.1 PO 层

放在 `infrastructure` 模块，每张表一个 PO：

```
org.wwz.ai.infrastructure.po
├── ToolOutputDeepSearchPO.java
├── ToolOutputFileToolPO.java
├── ToolOutputCodeInterpreterPO.java
├── ToolOutputReportToolPO.java
├── ToolOutputDataAnalysisPO.java
├── ToolOutputMultimodalAgentPO.java
├── ToolOutputImageGenerationPO.java
└── ToolOutputScriptRunnerPO.java
```

### 5.2 DAO 层

每张表一个 MyBatis-Plus BaseMapper：

```
org.wwz.ai.infrastructure.repository
├── ToolOutputDeepSearchMapper.java
├── ToolOutputFileToolMapper.java
├── ToolOutputCodeInterpreterMapper.java
├── ToolOutputReportToolMapper.java
├── ToolOutputDataAnalysisMapper.java
├── ToolOutputMultimodalAgentMapper.java
├── ToolOutputImageGenerationMapper.java
└── ToolOutputScriptRunnerMapper.java
```

### 5.3 核心分发组件

**ToolOutputWriter（domain 层接口）**

```java
package org.wwz.ai.domain.agent.reactor.service.tooloutput;

public interface ToolOutputWriter {
    void write(String toolName, Long toolInvocationId, Long runId, ToolResultPayload payload);
}
```

**ToolOutputWriterImpl（infrastructure 层实现）**

```java
@Component
public class ToolOutputWriterImpl implements ToolOutputWriter {
    private final Map<String, ToolOutputConverter<?>> registry;

    @Override
    public void write(String toolName, Long toolInvocationId, Long runId, ToolResultPayload payload) {
        ToolOutputConverter<?> converter = registry.get(toolName);
        if (converter == null) {
            log.warn("未找到工具 [{}] 的输出转换器，跳过持久化", toolName);
            return;
        }
        converter.convertAndSave(toolInvocationId, runId, payload);
    }
}
```

**ToolOutputConverter（各工具实现）**

```java
public interface ToolOutputConverter<T> {
    String getToolName();
    void convertAndSave(Long toolInvocationId, Long runId, ToolResultPayload payload);
}
```

每个工具提供一个实现，负责：
1. 把 `ToolResultPayload` 解析为对应 PO
2. 调用 Mapper 写入数据库

### 5.4 Projector 适配

每个 `ToolInvocationProjector` 实现需要改造：

- 原来是 `readJson(invocation.getOutputJson())` 解析 JSON
- 新方式是 `toolOutputReader.read(toolName, toolInvocationId)` 直接查字段

新增查询接口：

```java
public interface ToolOutputReader {
    Optional<ToolOutputDeepSearchPO> readDeepSearch(Long toolInvocationId);
    Optional<ToolOutputFileToolPO> readFileTool(Long toolInvocationId);
    // ... 其他工具
}
```

### 5.5 ToolResultPayload 调整

`ToolResultPayload` 的 `outputJson` 字段：

- 保留字段但改名为 `rawOutputData`，作为调试/日志用途
- 不再持久化到 `ai_agent_tool_invocation.output_json`
- 持久化统一走 `ToolOutputWriter`

## 6. 迁移策略

### 6.1 数据库迁移

1. 新增 8 张工具输出表
2. `ai_agent_tool_invocation` 的 `output_json` 列保留但不写新数据

### 6.2 代码迁移顺序

一次性完成全部迁移，不设过渡期：

1. **新增表和基础组件**（PO、Mapper、ToolOutputWriter/Reader 框架、全部 8 个 Converter）
2. **同步改造全部 projector**，从查 JSON 改为查新表字段
3. **改造 `BaseAgent`/`AgentExecutionRecorder` 写入点**，调用 `ToolOutputWriter` 同时停止写 `output_json`
4. **全链路验证**：跑通所有工具的端到端测试和 projector 回归测试
5. **一次性合并上线**
6. **清理阶段**（后续可选）：确认稳定后删除 `output_json` 列

### 6.3 旧数据

- 历史 `output_json` 数据保留在表中，不做回填
- 新 projector 只消费新表数据
- 如需查询历史数据，仍可从 `output_json` 读取（但新 projector 不支持）

## 7. 测试策略

### 7.1 每个工具输出表的基础测试

- 写入测试：验证 `ToolOutputWriter` 能正确按 `tool_name` 分发到对应表
- 读取测试：验证 `ToolOutputReader` 能正确查询各工具输出
- 空值/异常测试：验证 `tool_invocation_id` 为空、失败状态、错误信息写入

### 7.2 Projector 回归测试

- 每个工具的 projector 改造后，跑相同的 replay 场景，输出事件应与改造前一致
- 新增测试锁定"查字段而非解析 JSON"的行为

### 7.3 集成测试

- 端到端执行一次 Agent 流程，验证：
  - 工具执行结果写入新表
  - `ai_agent_tool_invocation.output_json` 不再写入
  - projector 能正确重建对话历史

## 8. 风险与取舍

### 8.1 表数量增加

**现状：** 8 张新表 + 8 个 Mapper + 8 个 PO + 8 个 Converter + 8 个 Projector 改造。

**取舍：** 表数量确实增加了，但每张表的职责单一、边界清晰。工具数量是可控的（目前 8 个），且各工具输出字段已稳定。新增一个工具时只需新增一套表+DAO+Converter，模板化后可快速复制。

### 8.2 新增工具时的成本

每新增一个 rich tool，需要：
1. 新增一张输出表
2. 新增 PO、Mapper、Converter
3. 新增/改造 Projector

**缓解：** 可以提供代码模板或脚手架，减少重复工作。非 rich tool（纯文本工具）可以复用通用 Converter，不必单独建表。

### 8.3 文件引用仍为 JSON

`file_refs_json` 保留为 JSON 列，没有完全消除 JSON。

**取舍：** 文件引用结构跨工具高度一致（文件名、URL、大小），规范化成独立表的收益有限，且会增加 JOIN 复杂度。当前方案在"彻底消除 JSON"和"实用复杂度"之间取平衡。

### 8.4 无过渡期直接废弃

选择直接废弃 `output_json` 而不是双写过渡。

**风险：** 如果某个 projector 或调试接口遗漏迁移，会导致功能异常。

**缓解：** 通过全面的集成测试和 projector 回归测试覆盖。改造期间可以先在新分支完成全部迁移，合并前跑通全链路测试。

## 9. 最终结论

本期推荐方案：

1. 为 8 个 rich tool 各建立独立的输出结果表，公共字段统一，特有字段明确
2. 通过 `ToolOutputWriter` + `ToolOutputConverter` Registry 模式实现统一写入分发
3. 通过 `ToolOutputReader` 为 Projector 提供字段级查询能力
4. 直接废弃 `ai_agent_tool_invocation.output_json`，所有读写走新表
5. 支持 `tool_invocation_id` 为空，满足前端直接调用工具的独立落库需求
6. 文件引用保留轻量 `file_refs_json` 列，不额外拆表
7. 逐个工具迁移，每个迁移配套 projector 改造和回归测试

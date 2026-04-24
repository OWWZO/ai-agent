# Data Model

## Overview

本期不新增数据库表结构，重点新增的是 Java 工具调用 DTO、Python 工具服务请求模型，以及围绕 MRAG 流式输出的运行时实体。

## Entities

### 1. MultiModalAgentToolConfig

| Field | Type | Source | Validation / Rule | Notes |
|------|------|--------|-------------------|-------|
| `toolDesc` | `String` | `autobots.autoagent.tool.multimodalagent_tool.desc` | 允许为空，空时回退默认描述 | 供大模型选择工具时使用 |
| `toolParams` | `Map<String, Object>` | `autobots.autoagent.tool.multimodalagent_tool.params` | 需为合法 JSON Schema 对象 | 驱动工具参数定义 |
| `toolList.default` | `String` | `autobots.autoagent.tool_list` | 包含 `multimodalagent` 才默认开放 | 只对 `REACT` / `PlanSolve` 生效 |
| `multiModalAgentUrl` | `String` | `autobots.autoagent.multimodalagent_url` | 非空、可组成合法 HTTP URL | Java 工具调用的 Python 服务基地址 |
| `knowledgeInterval` | `String` | `autobots.autoagent.message_interval.knowledge` | 形如 `first,interval`，缺省回退默认值 | 控制 `knowledge` 流式推送频率 |

### 2. MultiModalAgentRequest

Java 侧发送给 `reactor-tool` 的请求 DTO。

| Field | Type | Required | Validation / Rule | Notes |
|------|------|----------|-------------------|-------|
| `requestId` | `String` | yes | 与当前会话/请求绑定 | 参考实现使用 `sessionId` 适配多轮上下文 |
| `question` | `String` | yes | 非空白；为空时直接失败 | MRAG 检索问题 |
| `query` | `String` | yes | 保留原始用户问题 | 便于日志、命名和最终产物描述 |
| `stream` | `Boolean` | yes | 默认 `true` | Python 端按 SSE 返回 |
| `contentStream` | `Boolean` | yes | 继承当前请求 `isStream` | 控制是否向前端实时透传 |
| `streamMode` | `Map<String, Object>` | yes | 至少包含 `mode` | 典型值为 `{"mode":"token","token":10}` |

### 3. MultiModalAgentResponse

Java 侧消费的上游流式响应 DTO，兼容两种形态。

| Field | Type | Required | Validation / Rule | Notes |
|------|------|----------|-------------------|-------|
| `choices` | `List<Choice>` | no | OpenAI SSE 兼容形态 | 正常增量消费主路径 |
| `choices[].delta.content` | `String` | no | 可为空；为空时忽略 | 增量文本/图片 Markdown 片段 |
| `choices[].finishReason` | `String` | no | `stop` 视为最终完成 | 结束时汇总全量内容 |
| `data` | `String` | no | 兼容备用形态 | 处理非标准自定义片段 |
| `isFinal` | `Boolean` | no | `true` 表示可收口 | 与 `data` 配套使用 |
| `usage` | `Usage` | no | 透传统计信息 | 非核心逻辑字段 |

### 4. MultimodalRAGRequest

Python `reactor-tool` 对外暴露的接口请求模型。

| Field | Type | Required | Validation / Rule | Notes |
|------|------|----------|-------------------|-------|
| `question` | `str` | yes | `min_length >= 1` | 当前期唯一用户必填输入 |
| `image_urls` | `List[str]` | no | 缺省空数组 | 本期前端不直传图片，字段保留兼容 MRAG 内部能力 |
| `kb_id` | `Optional[str]` | no | 为空时回退 `DEFAULT_KB_ID` | 指定知识库 |

### 5. KnowledgeRetrievalArtifact

MRAG 完成后落库/上传的 Markdown 产物抽象。

| Field | Type | Producer | Validation / Rule | Notes |
|------|------|----------|-------------------|-------|
| `requestId` | `String` | Java `FileTool` | 绑定当前请求 | 用于产物归档 |
| `fileName` | `String` | Java `MultiModalAgent` | 必须为合法 Markdown 文件名 | 推荐格式：`{query}的多模态检索结果.md` |
| `description` | `String` | Java `MultiModalAgent` | 取内容前缀截断 | 供工作区与历史列表展示 |
| `content` | `String` | Java `MultiModalAgent` | 非空时才上传 | 保存完整 Markdown 文本 |
| `fileInfo` | `List<Map>` | 现有文件服务 | 由现有链路返回 | 前端与历史回放复用现有 artifact 展示协议 |

### 6. ToolAvailabilityProfile

控制工具是否对当前会话开放的运行时视图。

| Field | Type | Source | Validation / Rule | Notes |
|------|------|--------|-------------------|-------|
| `outputStyle` | `String` | `AgentRequest.outputStyle` | `dataAgent` 保持现状 | 仅非 `dataAgent` 路径开放 |
| `agentMode` | `Enum` | `REACT` / `PLAN_SOLVE` | 两条链路都需开放 | 规格要求默认支持双链路 |
| `toolAliases` | `List<String>` | `tool_list.default` | 包含 `multimodalagent` 时注册工具 | 与运行时真实工具名解耦 |

## Relationships

- `ToolAvailabilityProfile` 决定是否实例化 `MultiModalAgent`。
- 一次 `MultiModalAgentRequest` 会产生 0..N 个 `MultiModalAgentResponse` 流式分片。
- 一次成功的请求最终最多生成 1 个 `KnowledgeRetrievalArtifact`。
- `KnowledgeRetrievalArtifact` 通过现有 `artifactRefs` 与会话消息、历史回放和工作区展示关联。

## State Transitions

### MRAG Tool Execution

| State | Trigger | Next State | Failure Handling |
|------|---------|------------|------------------|
| `prepared` | Agent 选中 `multimodalagent_tool` 且参数校验通过 | `streaming` | 参数缺失时直接进入 `failed` |
| `streaming` | Java 工具成功建立到 `/v1/tool/mragQuery` 的 SSE 连接 | `completed` / `failed` / `timeout` | 心跳、空片段、混合格式需容错解析 |
| `completed` | 收到 `finishReason=stop` 或最终自定义片段 | 结束 | 生成 Markdown 产物并落入工作区 |
| `failed` | 上游不可达、解析失败、显式错误 | 结束 | 返回明确失败信息，不自动降级 |
| `timeout` | 超过 Java 工具包装层超时阈值 | 结束 | 主动取消调用并返回超时失败 |

### Timeline / Workspace Event Routing

| Runtime Message Type | Purpose | Expected Display Area |
|----------------------|---------|-----------------------|
| `knowledge` | MRAG 增量检索内容 | `workspace` 或任务流式区 |
| `markdown` | MRAG 最终 Markdown 内容 | `workspace` |
| `tool_result` | 通用工具结果 | 对 `multimodalagent_tool` 应抑制，避免重复 |

## Validation Notes

- 当前期用户输入只支持文本问题，不要求新增图片上传交互。
- 若 `question` 为空、全空白或上游返回不可恢复空流，Java 侧必须返回确定性失败信息。
- `image_urls` 在本期保留兼容，但不作为前端验收依赖。
- `multimodalagent_tool` 的最终展示以结构化 `knowledge` / `markdown` 为准，不应再额外重复输出同一份通用 `tool_result`。

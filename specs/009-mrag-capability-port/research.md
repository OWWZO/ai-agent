# Phase 0 Research

## Decision 1: Java 侧沿用当前仓库的工具装配入口，而不是照搬参考项目控制器

**Decision**: 在 `ai-agent-station-study-domain` 中通过 `AgentToolCollectionFactory` 注册 `MultiModalAgent`，通过 `ReactorConfig` 暴露配置，而不是引入参考项目的 `GenieController` / `GenieConfig` 结构。  
**Rationale**: 当前仓库的 `REACT` / `PlanSolve` 工具集合已经统一收口在 `AgentToolCollectionFactory`，配置已集中在 `ReactorConfig`。继续沿用这两个扩展点，能够保持 DDD 分层清晰，并避免把旧项目的控制器式装配复制进现有 Reactor 架构。  
**Alternatives considered**:
- 直接移植 `GenieController` / `GenieConfig`：会与当前仓库现有装配体系重复，增加维护面。
- 在调用入口处临时 hardcode 工具注册：实现快，但破坏可配置能力，不满足长期维护要求。

## Decision 2: 新增独立 `multimodalagent_tool`，并复用现有 SSE / 文件产物链路

**Decision**: 新增 Java `MultiModalAgent`、`MultiModalAgentRequest`、`MultiModalAgentResponse`，工具名保持 `multimodalagent_tool`，默认工具别名使用 `multimodalagent`，工具内部调用 `/v1/tool/mragQuery`，并复用当前 `SSEPrinter`、`AgentMessageEventServiceImpl`、`AgentStreamPersistServiceImpl`、`FileTool` 完成流式输出与 Markdown 产物上传。  
**Rationale**: 当前仓库已经具备 `knowledge`、`markdown`、`file` 的展示和持久化语义，MRAG 只需要把上游 SSE 结果适配进现有消息类型即可。这样能复用现有会话历史、工作区产物与 artifact 恢复能力，满足“前端零改动”的约束。  
**Alternatives considered**:
- 复用 `DeepSearchTool` 直接改造成多模态搜索：会混淆两个工具语义，配置和输出协议也不同。
- 仅通过通用 `tool_result` 返回字符串：会丢失结构化工作区展示和 Markdown 产物能力，且容易产生重复结果。

## Decision 3: 结果可见性以“真实工具名 + 现有消息类型”为准，修正参考项目的命名偏差

**Decision**: 在 `ExecutorAgent` 与 `ReactImplAgent` 的通用 `tool_result` 抑制名单中使用真实工具名 `multimodalagent_tool`，并根据流式输出方案补充 `EventResult.streamTaskMessageType` 对 `knowledge` 的识别；最终展示继续沿用 `knowledge` / `markdown`。  
**Rationale**: 参考项目存在“工具真实名称为 `multimodalagent_tool`，但过滤名单写成 `knowledge_tool`”的命名不一致。当前仓库应对齐最终行为，而不是机械复制这个偏差；否则会导致通用 `tool_result` 与专用 `knowledge` / `markdown` 事件重复展示。  
**Alternatives considered**:
- 直接照搬 `knowledge_tool` 名称：与当前真实工具名不一致，属于把参考实现中的命名缺陷原样带入。
- 完全不做抑制：会让前端同时看到专用 MRAG 结果和额外的通用工具结果，体验冗余。

## Decision 4: 配置采用独立 `multimodalagent_url`，不复用已有 `knowledge_url`

**Decision**: 在 `ReactorConfig` 中新增 `autobots.autoagent.multimodalagent_url` 与 `autobots.autoagent.tool.multimodalagent_tool.*`，保持现有 `knowledge_url` 继续只服务 `sopRecall` 等既有知识能力。  
**Rationale**: 当前仓库虽然已经有 `knowledge_url`，但它承担的是另一类知识服务接入。MRAG 是独立能力，使用独立地址更清晰，也更接近参考项目行为，便于后续独立扩容、切换或灰度。  
**Alternatives considered**:
- 复用 `knowledge_url`：配置项更少，但会把 SOP Recall 和 MRAG 强耦合到同一地址语义，不利于演进。
- 把地址 hardcode 在工具类中：不可维护，也不满足 FR-007。

## Decision 5: Python 侧将 MRAG 能力并入现有 `reactor-tool`，只做选择性迁移

**Decision**: 在 `reactor-tool` 中新增 `/v1/tool/mragQuery` 路由与 `MultimodalRAGRequest`，并把参考项目的 MRAG 运行代码选择性迁入 `reactor_tool/tool/mrag/`；依赖按实际 import 最小补齐，不整体复制 `genie-tool`。  
**Rationale**: 规格已明确要求 MRAG Python 能力并入 `reactor-tool` 体系。当前工具服务已经具备 FastAPI、SSE 与文件/环境变量约定，继续复用该服务最符合运维与部署现状。按实际依赖最小补齐，能避免把参考仓库中无关的工具、冗余依赖和长期维护成本一起带入。  
**Alternatives considered**:
- 直接整体复制 `genie-tool`：最省事，但会形成并行服务与重复依赖，违背规格约束。
- 完全重写 MRAG：风险高、工作量大，也不符合“效果对齐参考项目”的要求。

## Decision 6: LLM 图文消息支持和 CountDownLatch 修复视为现状能力，不纳入本期设计增量

**Decision**: 不在本期计划中新增 `LLM.base64Image -> image_url` 支持，也不重复修复 `BaseAgent` 并发释放；仅在实现阶段核对 MRAG 接入是否直接复用这些已有能力。  
**Rationale**: 当前仓库 `LLM.formatMessages()` 已支持 `base64Image`，`BaseAgent.executeTools()` 也已有 `finally { countDown(); }`。继续把它们当成本期新增会造成设计噪声，并误导任务拆分。  
**Alternatives considered**:
- 按参考项目说明再次移植：会制造重复修改和无效 diff。

## Decision 7: 失败策略保持显式失败，不自动回退普通搜索

**Decision**: Java 工具包装层在空输入、SSE 解析失败、上游超时或服务不可达时返回确定性失败信息，并安全结束当前工具调用；不自动切换到 `deep_search` 或其他工具。  
**Rationale**: 规格已明确“明确失败，不自动降级”。同时外部 MRAG 能力属于高不确定链路，必须让失败可观测、可排查，而不是冒充成功结果。  
**Alternatives considered**:
- 自动降级到普通搜索：用户体验看似平滑，但会造成结果语义混淆，且不符合验收基线。

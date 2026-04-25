# Feature Specification: 统一 DataAgent 与 MRAG 的云端向量环境

**Feature Branch**: `[010-unify-cloud-vector-env]`  
**Created**: 2026-04-25  
**Status**: Draft  
**Input**: User description: "统一 DataAgent 与 MRAG 的云端向量环境，复用同一套 Qdrant、文本向量和 Elasticsearch 配置，支持云端 Qdrant TLS，补齐共享 embedding 代理与受控重建机制，并保持旧环境兼容。"

## Existing System Context *(mandatory for brownfield features)*

- **Affected Modules**: `ai-agent-station-study-domain`、`ai-agent-station-study-app`、`reactor-tool`
- **Existing Capabilities to Reuse**: 现有 DataAgent 模型元数据初始化与 schema/列值同步能力、`reactor-tool` 的 `table_rag` 召回链路、MRAG 已具备的云端 Qdrant 接入能力、MRAG 现有文本 embedding 配置契约、现有 FastAPI 工具服务入口与环境变量装配方式
- **Out of Scope**: 不改 `ui/`、不新增数据库表或字段、不重做 MRAG 文档处理链路、不引入第二套长期独立维护的 embedding 服务、不重命名现有 DataAgent 的集合名与索引名、不扩展与本次向量环境统一无关的 Agent 能力
- **Current Constraints**: 必须遵守 DDD 分层边界；Java 主链路与 Python `reactor-tool` 必须共用同一套云端配置契约但保持各自职责清晰；现有本地 host/port 模式和旧 override 配置必须可继续工作；首次从非统一环境迁移到云端统一环境时，必须通过显式刷新动作完成远端数据重建，而不能依赖隐式短路逻辑

## Clarifications

### Session 2026-04-25

- Q: `force-refresh` 的重建数据源以谁为准？ → A: 仅以当前 `model-list` 中声明的模型为准
- Q: 当云端向量环境已显式启用但初始化或校验失败时，系统应该怎么处理？ → A: 常规启动继续，只记录告警，并在运行时把失败能力自动降级为不可用
- Q: `force-refresh` 执行过程中如果某个模型或某个远端步骤失败，应该采用什么策略？ → A: 失败即终止本次刷新；已完成步骤不强制回滚，但整体结果记为失败，运维需重新执行刷新
- Q: 当共享向量或 ES 能力在运行时被降级为不可用后，`DataAgent` 的问数主流程应如何表现？ → A: `DataAgent` 继续服务，但仅关闭失败的增强能力，退回基础 schema 和基础问数模式
- Q: `force-refresh` 完成后，要不要清理远端里那些不再出现在当前 `model-list` 中的陈旧模型数据？ → A: 清理陈旧远端数据，只保留当前 `model-list` 对应的数据

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 单套云端配置启用两条检索链路 (Priority: P1)

作为平台运维或环境维护者，我希望只维护一套共享的云端向量环境配置，就能同时启用 MRAG 与 DataAgent 的向量相关能力，而不需要再额外部署一套单独的 embedding 服务或为两条链路维护两套不一致的连接参数。

**Why this priority**: 这是本次需求的核心价值，直接决定云端部署成本、配置一致性和后续运维复杂度。

**Independent Test**: 填写共享云端配置与 DataAgent 开关后，分别执行一次 MRAG 检索请求和一次 DataAgent 问数请求，验证两条链路都能在同一环境下完成向量相关调用并返回可用结果。

**Acceptance Scenarios**:

1. **Given** 运维已提供共享的云端 Qdrant、文本向量和 Elasticsearch 配置，**When** Java 服务与 `reactor-tool` 启动，**Then** MRAG 与 DataAgent 都必须能够连接到预期的云端服务，而不要求额外独立的 embedding 服务
2. **Given** 同一套部署中同时存在 MRAG 检索请求和 DataAgent 的 schema 召回请求，**When** 两类请求被执行，**Then** 系统必须使用共享配置指向的云端服务完成各自职责范围内的向量或索引访问
3. **Given** 运维更新了共享云端配置，**When** 服务按既有发布方式重新加载配置，**Then** 新请求必须使用更新后的目标服务，而不需要修改前端或数据库结构

---

### User Story 2 - 迁移期间保持兼容与可覆盖 (Priority: P2)

作为维护棕地系统的开发者，我希望在切换到统一云端配置的同时，现有本地环境和历史 override 配置仍然可用，这样可以按环境逐步迁移，而不是一次性推翻旧部署。

**Why this priority**: 当前系统已经存在 DataAgent、`table_rag` 和 MRAG 的不同配置方式，若兼容性处理不好，将直接导致现有环境回归或迁移成本过高。

**Independent Test**: 分别验证“仅旧本地配置”“共享云端配置”“共享配置 + `table_rag` 专属 override”三种模式，确认三种模式都能按预期工作且互不污染。

**Acceptance Scenarios**:

1. **Given** 某个环境仍只配置旧的本地向量服务连接参数，**When** 本期能力上线，**Then** 该环境必须继续保持可用，不因统一云端配置能力的加入而失效
2. **Given** 共享云端配置和 `table_rag` 的专属 override 同时存在，**When** `table_rag` 发生召回，**Then** `table_rag` 必须优先使用自己的 override，而 MRAG 与 DataAgent 继续使用共享默认配置
3. **Given** 运维未调整与本期无关的工具和检索配置，**When** 统一向量环境能力启用后，**Then** 非目标 Agent 流程、普通工具链路和前端可见行为必须保持与基线一致

---

### User Story 3 - 显式重建保障远端数据一致性 (Priority: P3)

作为平台运维，我希望在把环境从旧的本地或半配置状态切换到统一云端环境时，能够通过一次显式的刷新动作重建模型元数据、schema 向量和列值索引，并在配置错误时得到明确失败反馈，而不是出现“服务启动了但远端集合是空的”这种隐性故障。

**Why this priority**: 统一配置只解决连接参数问题，真正影响可用性的是远端数据是否与当前模型元数据一致；没有受控刷新，迁移后很容易出现空集合、空索引或脏数据。

**Independent Test**: 预置一套已存在本地元数据但远端云端存储为空的环境，执行一次显式刷新并验证远端集合和索引被重建；再验证不触发刷新时不会发生破坏性重建。

**Acceptance Scenarios**:

1. **Given** 当前库中已有历史问数模型元数据，但新云端 Qdrant 和 Elasticsearch 中还没有对应数据，**When** 运维显式触发刷新并重启服务，**Then** 系统必须重建模型元数据、schema 向量和列值索引，使远端状态与当前配置一致
2. **Given** 运维没有显式触发刷新，**When** 服务按常规方式启动，**Then** 系统不得默认执行破坏性重建或清空远端数据
3. **Given** 云端配置缺失、认证错误、向量维度不匹配或列值索引缺少前置能力，**When** 启动或刷新过程执行，**Then** 系统必须给出明确且可定位的问题反馈，避免运维误判环境已健康
4. **Given** 某项共享云端能力已被显式启用但在常规启动时初始化失败，**When** 服务完成启动，**Then** 系统必须记录明确告警并将该能力标记为运行时不可用，而不是继续以可用状态对外暴露
5. **Given** 运维显式触发了强制刷新，且某个模型或某个远端步骤执行失败，**When** 本次刷新结束，**Then** 系统必须将整次刷新标记为失败并立即终止后续刷新流程，运维需在修复问题后重新执行刷新
6. **Given** 共享 Qdrant 或 Elasticsearch 增强能力在运行时被降级为不可用，**When** 用户继续发起 DataAgent 问数请求，**Then** 系统必须继续提供基础问数能力，并显式关闭对应的增强召回，而不是整体禁用问数服务
7. **Given** 运维已更新当前 `model-list` 并执行强制刷新，**When** 刷新完成，**Then** 远端 Qdrant 集合与 Elasticsearch 索引中不再属于当前 `model-list` 的模型数据必须被清理，保证远端状态与当前配置严格对齐

### Edge Cases

- 云端 Qdrant 地址包含 `https`、非默认端口或 API Key，但仍需要正确解析 TLS 与连接参数
- 共享配置只填写了一部分字段，且对应能力已经被启用
- 共享配置与 `table_rag` 专属 override 同时存在且目标不一致
- 旧环境中 `chat_model_info`、`chat_model_schema` 已存在，但新的远端集合或索引为空
- 云端 Elasticsearch 可连通，但不具备列值召回所需的分析器能力
- 文本向量模型维度与既有 schema 向量集合预期维度不一致
- 旧的显式 HTTP embedding override 仍被保留，需要在迁移期继续工作
- 强制刷新开关被误留在默认关闭或默认开启之外的状态时，系统必须按明确规则处理，而不是静默执行隐式动作
- 强制刷新过程中某个模型已完成部分远端写入，但后续步骤失败，系统需要明确暴露“本次刷新失败且需重新执行”的结果，而不是误报成功
- 当前 `model-list` 相比旧环境已经删除某些模型时，强制刷新需要避免这些陈旧模型继续残留在远端集合或索引中

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 提供一套共享的云端向量环境配置契约，使 MRAG 与 DataAgent 能复用同一组 Qdrant、文本向量与 Elasticsearch 连接配置
- **FR-002**: 系统 MUST 支持 DataAgent 通过 URL 驱动的云端 Qdrant 连接方式访问托管实例，并正确处理 TLS、端口和 API Key 等连接要素
- **FR-003**: 系统 MUST 允许 DataAgent 复用 MRAG 当前使用的文本向量模型配置，而不是要求运维额外部署一套独立的长期 embedding 服务
- **FR-004**: 系统 MUST 在 `reactor-tool` 中提供一项可被 Java 侧消费的内部文本向量代理能力，使共享文本向量配置能够被 DataAgent 以批量向量契约访问
- **FR-005**: 系统 MUST 定义清晰的共享配置组与专属覆盖配置组，其中共享配置面向 MRAG 与 DataAgent，专属覆盖仅在 `table_rag` 明确配置时生效
- **FR-006**: 系统 MUST 保留现有本地 host/port 模式、旧的显式 embedding override 和 `table_rag` 专属连接配置的兼容能力，确保存量环境可以分阶段迁移
- **FR-007**: 系统 MUST 明确职责归属：Java 侧负责 DataAgent 的元数据初始化、Qdrant/Elasticsearch 重建与同步，`reactor-tool` 负责共享文本向量代理和 Python 侧检索复用
- **FR-008**: 系统 MUST 明确本期需要变更的对外配置契约，包括共享 Qdrant 配置组、共享文本向量配置组、共享 Elasticsearch 配置组，以及 DataAgent 的启用与回调配置组
- **FR-009**: 系统 MUST 允许 `table_rag` 在缺少专属 Qdrant 直连配置时自动回退使用共享 Qdrant 配置，以减少重复配置
- **FR-010**: 系统 MUST 保持 DataAgent 既有的 schema 集合名和列值索引名稳定，避免因命名变更导致现有召回逻辑、运维脚本或排障文档失效
- **FR-011**: 系统 MUST 提供显式的强制刷新开关，用于在迁移到新环境时重建问数模型元数据、schema 向量集合和列值索引
- **FR-012**: 系统 MUST 将强制刷新设计为默认关闭，只有运维显式开启时才允许执行破坏性重建动作
- **FR-012A**: 系统 MUST 在强制刷新时仅以当前 `model-list` 中声明的模型作为重建范围，未声明模型不得被视为本次刷新目标
- **FR-012B**: 系统 MUST 在强制刷新成功完成后清理远端中不再属于当前 `model-list` 的模型数据，使远端集合与索引严格对齐当前配置
- **FR-013**: 系统 MUST 在缺失配置、认证失败、维度不匹配、连接不可达或 Elasticsearch 前置能力不满足时给出明确失败结果，避免系统以“看似启动成功”的状态进入运行
- **FR-013A**: 系统 MUST 在常规启动场景下允许服务继续启动，但对初始化或校验失败的已启用能力执行自动降级，并在运行时以明确失败结果对外表现为不可用
- **FR-013B**: 系统 MUST 在强制刷新过程中采用失败即终止策略；允许已完成步骤保留现状，但整次刷新结果必须被标记为失败，且不得继续执行后续刷新步骤
- **FR-013C**: 系统 MUST 在共享向量或列值增强能力降级为不可用时保持 DataAgent 主流程继续可用，仅关闭失败的增强能力并退回基础问数模式
- **FR-014**: 系统 MUST 保持 MRAG 文档入库、MRAG 检索、普通 Agent 流程、前端行为和数据库结构的向后兼容；当本期能力未被启用或未被调用时，用户不应感知行为变化
- **FR-015**: 系统 MUST 为运维提供可重复执行的环境校验路径，使其能够确认共享配置是否生效、远端集合与索引是否已重建，以及两条链路是否确实连接到目标云端服务

### Key Entities *(include if feature involves data)*

- **Shared Cloud Vector Configuration**: 面向多个检索能力复用的一组云端连接配置，描述 Qdrant、文本向量服务与 Elasticsearch 的统一接入信息
- **Vector Override Profile**: 某个特定检索链路使用的专属覆盖配置，表示它何时应优先于共享配置生效以及适用范围
- **Embedding Proxy Contract**: Java 侧请求批量文本向量、`reactor-tool` 返回批量向量结果的共享接口约定
- **Refresh Execution**: 运维显式触发的一次重建操作，负责让当前问数模型元数据与云端 schema 向量、列值索引保持一致
- **Remote Retrieval State**: 当前环境下远端 Qdrant 集合与 Elasticsearch 索引的有效状态，用于判断 DataAgent 与 MRAG 是否真正处于可用状态

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 在标准云端部署演练中，运维可在 30 分钟内仅通过一套共享向量环境配置和 DataAgent 专属启用项完成 MRAG 与 DataAgent 的环境配置，无需额外部署独立 embedding 服务
- **SC-002**: 在有效云端配置下，100% 的联调验收样本都能证明 MRAG 检索与 DataAgent schema 召回连接到了预期的目标云端服务
- **SC-003**: 在从旧环境迁移到统一云端环境的验收演练中，100% 的样本都能通过一次显式刷新完成远端 schema 向量和列值索引重建，而不需要手工修改数据库表数据
- **SC-004**: 在共享云端配置生效的验收样本中，95% 以上的 MRAG 检索请求和 DataAgent 向量召回请求都能返回可用结果
- **SC-005**: 在缺失配置、错误密钥、维度不匹配和缺少前置分析器的异常演练中，100% 的样本都能在运维判定环境健康之前得到可操作的失败提示
- **SC-006**: 在回归验收中，保留旧本地配置的环境能够继续通过现有冒烟验证，且不出现高严重度兼容性回归

## Assumptions

- 目标云端 Qdrant 支持基于 URL、端口和 API Key 的托管接入方式，并允许 Java 与 Python 运行时从同一网络环境访问
- 共享文本向量服务继续使用 MRAG 当前已经采用的 OpenAI-compatible 协议，不额外引入第二套文本向量提供方契约
- 目标 Elasticsearch 环境具备列值召回所需的分析能力；若不具备，则该环境不属于本期“完整统一环境”的验收通过状态
- 现有 DataAgent 使用的远端集合名与索引名在本期无需做租户隔离或命名演进
- 强制刷新用于受控的迁移或发布窗口，不作为每次常规启动的默认行为

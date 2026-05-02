## Context

当前生图能力存在两条并行实现。工作台链路由 `WorkspaceImageGenerationServiceImpl` 驱动，历史数据依赖遗留 `ai_agent_image_generation_record`；`image_generation_tool` 则通过单独的工具实现调用 Python 生图接口，并把结果写入通用的 tool output / artifact 账本。两条链路虽然都访问同一个 `/v1/tool/image_generation` 上游，但请求归一化、结果结构、错误处理和持久化模型并不一致。

这次改造只覆盖 Java 后端主链路，不改 `ui/` 页面结构，也不改 Python 实际生图实现。关键约束有三点：一是工作台历史必须改读共享账本，但返回 shape 需要保持兼容；二是数据库事务只应覆盖本地持久化，不应包住外部 HTTP 调用；三是现有 `ToolOutputWriter.write(...)` 与 `AgentExecutionRecorder.recordArtifacts(...)` 采用 fail-open 语义，工作台要获得真正的事务回滚能力，必须提供 strict 写入入口。

## Goals / Non-Goals

**Goals:**
- 为工作台与 `image_generation_tool` 提供统一的生图执行内核，统一请求默认值、上游调用和结果归一化。
- 让工作台批次与工具批次共享 `ai_agent_tool_output_image_generation` + `ai_agent_artifact` 账本模型，并支持按 `requestSource` 区分来源。
- 让工作台历史只从共享账本读取 `workspace` 批次，不再依赖 `X-Device-Id` 和遗留 record 表。
- 保持当前前端消费的生成响应和历史返回结构稳定，避免本次改造扩散到 `ui/`。
- 删除遗留 `ai_agent_image_generation_record` 代码与 schema，收敛到单一可维护链路。

**Non-Goals:**
- 不重写 Python 侧图片生成逻辑，也不新增工作台 SSE 进度流。
- 不为历史数据做回填迁移；旧 record 表中的历史默认不导入共享账本。
- 不扩展新的工作台管理能力，例如删除历史、收藏历史或来源筛选 UI。
- 不修改 `reactor-tool/`、`reactor-client/` 的运行模式或引入新的外部依赖。

## Decisions

### Decision 1: 抽离共享执行内核，统一 workspace 与 tool 的上游调用

采用一个独立的 `IImageGenerationExecutionKernel`，输入为统一的 `ImageGenerationExecuteCommand`，输出为统一的 `ImageGenerationExecutionResult`。内核负责填充默认 `size`、`n`、`timeoutSeconds`、透传 `model`，并把 Python gateway 返回的 `fileInfo` 归一化为工作台与工具都能消费的文件列表。

选择这个方案，是因为工作台与工具本质上都只是同一上游能力的不同上下文适配层。若继续保留两套调用逻辑，后续任何字段演进都要双改。备选方案是保留 `ImageGenerationTool` 的专有 SSE 逻辑，只为工作台抽公共方法，但这会让“工具路径”和“工作台路径”继续分叉，无法真正收敛。

### Decision 2: 以共享账本承载批次主记录与图片明细，不再保留 workspace 专属记录表

`ai_agent_tool_output_image_generation` 作为一批请求一行的批次主表，新增 `requestSource`、`size`、`batchCount`、`sourceImageCount`、`maskImageCount`、`usedFallback` 等结构化字段；`ai_agent_artifact` 继续承载一张图一行的文件明细，并补充 `requestId` 以支持非 run 场景关联。

选择这个方案，是因为项目已经有完整的 tool output / artifact 读写投影能力，工作台继续维护专属 record 表只会形成第二套账本。备选方案是新增新的 workspace 专属批次表或保留旧表并做双写，但这会扩大模型面并增加长期迁移成本。

### Decision 3: 将工作台持久化拆为独立事务服务，并为共享 writer / recorder 提供 strict 接口

工作台服务先调用执行内核拿到最终结果，再调用 `IImageGenerationBatchPersistenceService` 完成本地持久化。`persistWorkspaceBatch(...)` 使用 `@Transactional`，内部必须走 `ToolOutputWriter.writeOrThrow(...)` 和 `AgentExecutionRecorder.recordArtifactsOrThrow(...)`，而不是现有 fail-open 包装。

这样设计的原因是，外部 HTTP 调用失败时不应该占用数据库事务，而数据库任一写入失败时则必须整体回滚。只在现有 service 上直接补 `@Transactional` 不够，因为 fail-open 语义会吞掉异常，事务无法感知失败。

### Decision 4: 工作台历史只读取 `request_source = 'workspace'` 的共享批次，并通过 `requestId + toolCallId` 补全 artifact

工作台历史分页直接查询共享批次表，明确过滤 `request_source = 'workspace'`，避免把普通对话中的 `image_generation_tool` 批次混入历史。对于 workspace 批次没有 `runId` 的场景，`ToolOutputReader` 需要在 `runId/toolInvocationId` 为空时，回退按 `requestId + toolCallId` 读取 artifact 明细。

这样可以把历史读取路径与共享账本彻底对齐，同时摆脱设备维度隔离。备选方案是继续保留旧 DAO 作为工作台历史来源，或在历史查询时混读两套数据源，但这会延续一致性问题，也让旧链路无法删除。

### Decision 5: `image_generation_tool` 改用同步 kernel，但保留现有 file 事件与 structured output 语义

`ImageGenerationTool` 从“自己处理上游调用”调整为“组装参数后调用共享 kernel”，成功后继续通过当前 artifact source 发 file 事件，并返回更丰富的 `ImageGenerationToolOutput`。上游调用统一使用同步 JSON 结果，不再消费中间 SSE 片段。

这样做的前提是当前 Java 侧本就只展示最终文件卡片，不依赖生成过程中的 token 级进度。备选方案是保留工具的旧 SSE 路径，只共享部分 DTO，但这会让批次字段和失败行为仍然无法做到一致。

## Risks / Trade-offs

- [共享账本字段扩展会影响既有 tool output 读写映射] → 通过保持新增字段向后兼容、补齐 Mapper / Reader / Writer 回归测试来降低风险。
- [workspace artifact 没有 `runId`，若关联条件不完整会导致历史丢图] → 在 artifact 账本中补 `requestId`，并增加按 `requestId + toolCallId` 的查询回退路径与定向测试。
- [工具链路改走同步 gateway 后丢失中间 SSE 片段] → 当前 Java 产品面只消费最终文件结果，保留最终 file 事件即可，不把中间片段视为本次兼容要求。
- [旧 `ai_agent_image_generation_record` 历史不做回填，切换后无法在新历史页继续展示] → 在方案与上线说明中明确“不迁移旧历史”，并将其视为一次账本收敛的已知取舍。
- [过早删除旧表会压缩回滚窗口] → 迁移顺序上先完成共享账本字段扩展与代码切换，再在同批回归验证后删除旧表；若发布前发现问题，可以先回退代码并保留新增字段。

## Migration Plan

1. 扩展共享账本 schema、PO、DAO、Mapper 与 reader/writer 契约，先让共享账本具备承接 workspace 批次的能力。
2. 引入共享执行内核与 strict 持久化接口，补齐相应的领域层单测与回归测试。
3. 重写 `WorkspaceImageGenerationServiceImpl` 与 controller，把工作台生成、历史查询切换到共享账本链路。
4. 改写 `ImageGenerationTool` 以复用共享执行内核，并补齐 richer structured output。
5. 完成定向回归后删除 `ai_agent_image_generation_record` 相关实体、DAO、Mapper 与 schema 定义。
6. 发布验证重点包括：工作台生成成功/失败、多图批次、历史分页、工具 file 事件、workspace 与 agent 来源隔离。

回滚策略：
在旧表删除前，如发现问题可直接回退应用代码并保留新增共享字段；一旦 schema 清理已执行，回滚将以恢复上一个应用版本并接受“新写入共享账本数据保留、旧历史不恢复”为边界。

## Open Questions

- 是否需要在后续单独补一个后台查询视角，用于同时查看 `workspace` 与 `agent` 两类生图批次；本次工作台历史默认只展示 `workspace`。
- 是否需要在未来为旧 `ai_agent_image_generation_record` 做一次性迁移脚本；本次默认不做，避免扩大范围。

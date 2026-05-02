## Why

当前生图工作台与 `image_generation_tool` 仍然走两套分离的执行与持久化链路：工作台依赖遗留的 `ai_agent_image_generation_record`，工具链路依赖通用的 tool output / artifact 账本，二者在请求归一化、结果结构、历史来源和异常处理上已经开始分叉。这种分叉让后续需求必须重复改两处，也让工作台历史与工具产物无法复用同一套可追溯能力。

现在需要把这条能力收口，因为项目已经具备稳定的 `ai_agent_tool_output_image_generation`、`ai_agent_artifact` 和 `ToolOutputReader/Writer` 基础设施；继续保留遗留表和独立逻辑，只会增加维护成本并放大行为不一致风险。

## What Changes

- 抽离统一的生图执行内核，负责组装请求、调用 `/v1/tool/image_generation`、归一化响应，让工作台与 `image_generation_tool` 复用同一条执行主链路。
- 扩展共享生图账本模型，在 `ai_agent_tool_output_image_generation` 中保存批次主记录，在 `ai_agent_artifact` 中保存图片明细，并补齐 `requestSource`、`requestId` 及批次结构化字段。
- 为工作台新增事务型批次持久化服务，保证工作台批次主记录与图片明细在同一数据库事务内提交，同时避免把外部 HTTP 调用包进事务。
- 将工作台历史查询切换为只读取共享账本中 `request_source = 'workspace'` 的批次，不再依赖 `X-Device-Id` 和遗留 `ai_agent_image_generation_record`。
- 扩展 `image_generation_tool` 的 structured output，使工具与工作台共享一致的批次元数据，包括 `size`、`batchCount`、`sourceImageCount`、`maskImageCount`、`usedFallback`。
- 删除遗留 `ai_agent_image_generation_record` 实体、DAO、Mapper 和表定义，完成生图工作台与工具链路的账本收敛。

## Capabilities

### New Capabilities
- `workspace-image-generation`: 生图工作台必须通过统一执行与共享账本链路生成图片、持久化批次结果，并从共享账本读取历史批次。
- `image-generation-output-ledger`: 生图工作台与 `image_generation_tool` 必须把生图结果写入统一的批次主表与图片明细账本，并暴露一致的结构化输出元数据。

### Modified Capabilities

## Impact

- `ai-agent-station-study-domain`：新增统一执行内核、工作台批次持久化服务，调整工作台服务、工具输出模型、artifact 记录契约与 `ImageGenerationTool`。
- `ai-agent-station-study-infrastructure`：扩展 tool output / artifact 读写实现、DAO / PO / Mapper SQL，以及 Python 生图 gateway 的请求透传字段。
- `ai-agent-station-study-trigger`：更新工作台 controller，移除 `deviceId` 依赖并接入新服务签名。
- `ai-agent-station-study-app`：调整 `schema.sql`、MyBatis Mapper XML 和回归测试，删除遗留 `ai_agent_image_generation_record` 相关文件。
- `ui/` 无计划新增接口字段或页面重构；本次需要保持当前工作台消费的响应形状兼容。

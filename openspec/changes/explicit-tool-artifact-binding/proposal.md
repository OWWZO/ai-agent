## Why

当前 Agent 运行链路里，工具产出的文件只会被追加到 `productFiles` / `taskProductFiles`，而 `toolCallId` 只用于关联工具文本结果，二者没有显式绑定关系。主智能体和 `SummaryAgent` 只能依赖文件名、描述或列表顺序去猜测文件来源，在多工具并行产物、同类文件连续生成等场景下容易误判。

在已废弃旧 transcript/turn/block 方案的前提下，需要基于现行 ReAct / PlanSolve 运行时链路补齐“工具调用 -> 文件产物”的明确归属关系，先解决主智能体和总结阶段的稳定识别问题。

## What Changes

- 在当前运行时上下文中引入显式的工具产物绑定模型，至少记录 `sessionId`、`requestId`、`toolCallId`、`toolName` 与文件引用之间的关系。
- 为所有会生成文件的工具补齐统一的产物登记入口，避免继续由各工具直接向 `productFiles` / `taskProductFiles` 写入裸 `File` 对象。
- 调整主智能体提示词装配与总结阶段文件选择逻辑，使其优先使用显式绑定关系判断文件来源，而不是继续依赖文件名模糊匹配和列表顺序。
- 保持现有前端展示、SSE 事件结构和对外接口不变，本次仅改后端内部识别与归属链路。

## Capabilities

### New Capabilities
- `tool-artifact-binding`: 为当前 Agent 运行链路提供显式的工具文件产物绑定与来源识别能力。

### Modified Capabilities

## Impact

- 影响模块：`ai-agent-station-study-domain`
- 重点代码：`AgentContext`、`Message` / `toolCallId` 消费链、`SummaryAgent`、`FileUtil`、`ReactImplAgent`、`ExecutorAgent`、各类产文件工具
- 影响工具：`file_tool`、`deep_search`、`report_tool`、`code_interpreter`、`data_analysis`、`image_generation`、脚本类工具
- 不涉及前端重写、不依赖已废弃 transcript/turn/block 体系、默认不新增对外接口

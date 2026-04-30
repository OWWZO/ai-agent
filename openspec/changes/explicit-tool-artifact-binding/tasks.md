## 1. 运行时产物绑定基础设施

- [x] 1.1 新增运行时工具产物来源模型（如 `ToolArtifactSource`、`ToolArtifactBinding`），并在 `AgentContext` 中补齐绑定集合、按 `toolCallId` 查询和可交付文件查询能力。
- [x] 1.2 实现统一的 `ToolArtifactRegistrar`，让其在登记来源绑定的同时维护现有 `productFiles` / `taskProductFiles` 兼容视图。
- [x] 1.3 补齐绑定结果格式化工具，支持生成“单次工具调用文件摘要”和“总结阶段来源感知文件上下文”两类文本。

## 2. 执行链路接入显式来源

- [x] 2.1 改造 `BaseAgent.executeTool(...)`，在单次工具执行前创建不可变来源快照，并为同步执行与异步回调都提供可传递的来源上下文。
- [x] 2.2 改造 `ReactImplAgent` 与 `ExecutorAgent`，在写入 `Message.toolMessage(...)` 前附加当前 `toolCallId` 对应的文件摘要，避免混入其他工具调用的文件。
- [x] 2.3 清理执行链路中直接依赖扁平 `productFiles` 猜测来源的逻辑，统一改为通过来源绑定查询文件归属。

## 3. 产文件工具与总结阶段改造

- [x] 3.1 改造 `file_tool` 及其通用上传辅助路径，移除直接追加文件列表的写法，改为统一走 `ToolArtifactRegistrar`。
- [x] 3.2 改造 `report_tool`、`deep_search`、`code_interpreter`、`data_analysis`、`image_generation`、`ScriptRunnerTool`，确保同步和异步产文件路径都能传递正确的来源快照并完成登记。
- [x] 3.3 改造 `SummaryAgent` 的文件上下文构造、总结输出协议和结果解析逻辑，优先按 `toolCallId + fileName` 精确匹配最终文件，并过滤内部中间文件。

## 4. 回归验证

- [x] 4.1 为同步工具、异步工具和多工具并行情形补充测试，验证文件来源绑定不会因线程切换或并发执行丢失。
- [x] 4.2 为主智能体工具结果消息和 `SummaryAgent` 增加同名文件、近似文件名、内部文件混合场景测试，验证不会再靠全局文件名模糊命中。
- [x] 4.3 运行受影响的领域层 / app 层测试，并人工检查最终 `fileList` 对外结构保持兼容。

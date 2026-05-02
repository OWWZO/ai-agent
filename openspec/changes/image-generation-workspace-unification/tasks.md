## 1. 统一生图执行与输出契约

- [x] 1.1 新增 `ImageGenerationExecuteCommand`、`ImageGenerationExecutionResult` 和 `IImageGenerationExecutionKernel`，让 workspace 与 `image_generation_tool` 共用同一条上游调用与结果归一化链路
- [x] 1.2 扩展 gateway 请求、structured output 和相关模型字段，补齐 `model`、`size`、`batchCount`、`sourceImageCount`、`maskImageCount`、`usedFallback`
- [x] 1.3 为共享执行内核与工具 structured output 增加定向测试，覆盖默认值归一化、上游返回映射和 rich output 兼容性

## 2. 共享账本持久化与工作台历史

- [x] 2.1 扩展 `ai_agent_tool_output_image_generation`、`ai_agent_artifact` 的 schema、PO、DAO、Mapper 和 SQL，使其支持 `requestSource`、`requestId`、workspace 批次查询与非 run artifact 关联
- [x] 2.2 为 `ToolOutputWriter` 和 `AgentExecutionRecorder` 增加 strict 写入入口，并实现事务型 `IImageGenerationBatchPersistenceService`
- [x] 2.3 调整 `ToolOutputReader` 与工作台历史组装逻辑，只读取 `request_source = 'workspace'` 批次，并支持按 `requestId + toolCallId` 回补 artifact 明细

## 3. 切换 workspace 与 tool 到共享主链路

- [x] 3.1 重写 `WorkspaceImageGenerationServiceImpl` 与 `IWorkspaceImageGenerationService` 签名，移除 `deviceId` 依赖并接入共享执行内核和事务型持久化服务
- [x] 3.2 更新 `AgentImageGenerationController` 和相关测试，确保工作台生成与历史查询不再要求 `X-Device-Id`，同时保持当前响应结构兼容
- [x] 3.3 改写 `ImageGenerationTool` 以复用共享执行内核，并继续产出 file 事件与一致的 structured output

## 4. 遗留链路清理与回归验证

- [x] 4.1 删除 `ai_agent_image_generation_record` 的实体、DAO、Mapper 与 schema 定义，清理所有遗留引用
- [x] 4.2 运行定向 Maven 测试，覆盖执行内核、workspace service/controller、tool 回归与共享账本查询行为
- [x] 4.3 执行 `mvn -pl ai-agent-station-study-app -am -DskipTests compile`，确认删除旧链路后主工程仍能完成装配

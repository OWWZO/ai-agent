## 1. 账本字段与查询模型调整

- [x] 1.1 修改 `ai_agent_tool_invocation` 表结构与迁移脚本，将 `output_text` 重命名为 `llm_oberserve`，同步更新字段注释。
- [x] 1.2 调整 `ToolInvocation`、`ToolInvocationView`、`ToolInvocationFinishRecord` 及相关 Mapper XML / DAO，使代码层统一使用 `llmObservation` 映射到数据库列 `llm_oberserve`。
- [x] 1.3 更新 `AgentExecutionRecorderImpl`、`ExecutionLedgerQueryServiceImpl` 和相关账本查询输出，移除旧 `outputText` 语义引用。

## 2. 主智能体 observation 收口

- [x] 2.1 重构 `ToolExecutionOutcome`，显式区分 `llmObservation` 与 `outputJson`，避免继续用泛化 `displayResult/outputText` 表达多种语义。
- [x] 2.2 在 `BaseAgent` 中实现统一的 final observation 组装逻辑，集中处理截断、工具产物摘要追加与最终 observation 文本生成。
- [x] 2.3 改造 `executeTool()`、`executeTools()` 与相关持久化时机，确保写入 `llm_oberserve` 的内容与实际写入 `Message.toolMessage(...)` 的内容完全一致。
- [x] 2.4 统一 `PlanningAgent`、`ReactImplAgent`、`ExecutorAgent` 的工具结果写回链路，消除单工具路径与批量工具路径的 observation 行为差异。

## 3. deep_search observation 精简化

- [x] 3.1 保留 `deep_search` 现有完整结构化结果构建逻辑，继续产出最终 `output_json`。
- [x] 3.2 为 `deep_search` 增加独立的主智能体 observation 构建逻辑，输出包含子查询、命中文档 `title`、`link` 与内容摘要的紧凑结果。
- [x] 3.3 为 `deep_search` 增加确定性裁剪规则，限制每个子查询的文档数量和内容摘要长度，避免 observation 再次膨胀成大 JSON。
- [x] 3.4 处理 `deep_search` 超时/失败回退路径，确保无法形成检索摘要时仍能写入可解释的 `llmObservation` 文本。

## 4. 回归验证

- [x] 4.1 为普通工具与文件产物工具补充/更新测试，验证 `llm_oberserve` 与最终主智能体 tool message 内容一致。
- [x] 4.2 为 `deep_search` 补充测试，覆盖成功、裁剪、超时/失败三类 observation 输出，以及 `output_json` 与 `llmObservation` 分离行为。
- [x] 4.3 运行受影响的领域层 / app 层回归测试，并人工检查账本查询结果、`tool_result` 行为与现有前端 SSE 展示未发生协议回归。

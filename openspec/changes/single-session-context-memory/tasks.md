## 1. 扩展执行账本查询能力

- [x] 1.1 为 `ILlmInvocationLedgerDao`、`IToolInvocationLedgerDao`、`IArtifactLedgerDao` 增加按 `runIds`、`llmInvocationIds`、`toolInvocationIds` 的批量查询接口
- [x] 1.2 更新 `llm_invocation_ledger_mapper.xml`、`tool_invocation_ledger_mapper.xml`、`artifact_ledger_mapper.xml`，补齐单会话记忆所需的批量查询 SQL
- [x] 1.3 同步更新 `ExecutionLedgerFixtureFactory` 等测试夹具，保证内存 DAO 与生产 DAO 具备相同查询语义

## 2. 建立单会话记忆组装能力

- [x] 2.1 新增 `SessionHistoryMemory`、`RunHistoryMemory`、`ReactCycleMemory`、`ToolCallMemory`、`FileArtifactMemory` 五个中间模型
- [x] 2.2 新增 `SessionContextMemoryService` 与 `SessionContextMemoryServiceImpl`，完成 run、cycle、tool、artifact 到 `historyDialogue` 的组装与格式化
- [x] 2.3 在服务实现中补齐当前 `requestId` 过滤、空值兜底、`Tool Calls: - none` 与 `Files: - none` 等格式规则

## 3. 接入 Agent 执行入口

- [x] 3.1 在 `ReactAgentExecuteStrategy` 执行前调用 `SessionContextMemoryService`，并把结果写回 `request.historyDialogue`
- [x] 3.2 在 `PlanSolveAgentExecuteStrategy` 执行前接入相同的单会话记忆注入逻辑
- [x] 3.3 保持 `BaseAgent.injectHistoryDialogue(...)` 与后续 `AgentContext` 注入链路不变，只把新增能力限制在策略入口 enrich

## 4. 补齐验证与回归

- [x] 4.1 新增 `SessionContextMemoryServiceTest`，覆盖排序、分组、当前 run 排除、空结构占位和文件元信息格式化
- [x] 4.2 新增 `SessionContextMemoryIntegrationTest`，验证 React / PlanSolve 入口会自动注入同会话历史记忆
- [x] 4.3 运行 `ai-agent-station-study-app` 相关定向 Maven 测试，确认会话记忆组装与主执行链路回归通过

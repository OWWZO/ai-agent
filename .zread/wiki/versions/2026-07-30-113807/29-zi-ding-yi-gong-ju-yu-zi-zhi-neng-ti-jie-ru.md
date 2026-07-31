自定义工具与子智能体接入模块提供 Reactor Agent 平台对外部工具与子 Agent 的动态挂载、过滤、执行隔离和结果同步能力。用户可通过自定义工具（本地 Java 或远程 MCP）或子智能体定义，实现工具/Agent 能力的扩展与复用，同时支持 Plan Mode 下只读约束与主 Agent 工具池的共享复用。

## 工具与子智能体接入核心设计

### 工具接入机制

工具通过 `BaseTool` 接口实现，`AgentToolCollectionFactory` 统一装配本地工具与 MCP 工具。工具定义经 `ToolDefinitionCache` 缓存并通过 `BaseToolCallbackAdapter` 适配为 Spring AI `ToolCallback`，支持稳定 schema 序列化以避免 prompt cache 失效。

Sources: [AgentToolCollectionFactory.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/factory/AgentToolCollectionFactory.java#L1-L250)
Sources: [BaseToolCallbackAdapter.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/llm/BaseToolCallbackAdapter.java#L1-L87)
Sources: [ToolDefinitionCache.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/llm/ToolDefinitionCache.java#L1-L61)

### 子智能体接入机制

`SubAgentDefinition` 定义子 Agent 类型、whenToUse 描述、systemPrompt、allowedTools/disallowedTools 规则及 maxSteps。`SubAgentRegistry` 维护内置类型（Explore / general-purpose）与可配置类型。`AgentDispatchTool` 提供同步派发入口，`SubAgentRunner` 执行 ReactImplAgent 并返回结论文本。

Sources: [SubAgentDefinition.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/subagent/SubAgentDefinition.java#L1-L47)
Sources: [SubAgentRegistry.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/subagent/SubAgentRegistry.java#L1-L191)
Sources: [AgentDispatchTool.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/common/AgentDispatchTool.java#L1-L180)

### 工具过滤与隔离

`SubAgentToolFilter` 对全局禁止工具（AgentDispatchTool 自身、Plan Mode 写工具）、自定义 disallowedTools 及 allowedTools 白名单进行三层过滤。子 Agent 上下文通过 `SubAgentContextFactory` 隔离记忆，仅共享 runtime 依赖与产物登记。

Sources: [SubAgentToolFilter.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/subagent/SubAgentToolFilter.java#L1-L104)
Sources: [SubAgentContextFactory.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/subagent/SubAgentContextFactory.java#L1-L95)

## 自定义工具接入

### 实现步骤

1. 实现 `BaseTool` 接口，覆盖 `getName`、`getDescription`、`toParams`、`execute` 方法。
2. 添加工具到 `ToolCollection`（`addTool`）。
3. 工具执行结果需返回 `ToolResultPayload`（支持 text / fromData / failure）。
4. 如需 MCP 远程支持，参考 `McpTool` 实现远程工具调用。

Sources: [BaseTool.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/BaseTool.java#L1-L16)
Sources: [ToolResultPayload.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/ToolResultPayload.java#L1-L132)
Sources: [McpTool.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/mcp/McpTool.java#L1-L132)

### 示例：自定义工具

```java
public class MyCustomTool implements BaseTool {
    @Override
    public String getName() { return "my_custom_tool"; }
    @Override
    public String getDescription() { return "自定义工具描述"; }
    @Override
    public Map<String, Object> toParams() { /* schema 定义 */ }
    @Override
    public Object execute(Object input) {
        // 执行逻辑，返回 ToolResultPayload
        return ToolResultPayload.text("执行结果");
    }
}
```

## 子智能体接入

### 实现步骤

1. 定义 `SubAgentDefinition`（可通过 DB 持久化或内置）。
2. 注册到 `SubAgentRegistry`（`register` 或 `replaceConfigured`）。
3. 使用 `AgentDispatchTool` 派发任务（需提供 description / prompt）。
4. 子 Agent 自动应用过滤规则与隔离上下文。

Sources: [SubAgentDefinitionUpsertCommand.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/subagent/SubAgentDefinitionUpsertCommand.java#L1-L32)
Sources: [SubAgentDefinitionLoader.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/subagent/SubAgentDefinitionLoader.java#L1-L49)
Sources: [SubAgentResult.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/subagent/SubAgentResult.java#L1-L31)

### 配置子智能体示例

```json
{
  "agentKey": "code-reviewer",
  "whenToUse": "只读代码审查",
  "systemPrompt": "审查代码质量...",
  "allowedTools": ["workspace_read", "workspace_grep"],
  "disallowedTools": ["workspace_write"],
  "maxSteps": 10
}
```

Sources: [SubAgentDefinitionUpsertReqVO.java](Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/vo/SubAgentDefinitionUpsertReqVO.java#L1-L35)

## 工具/子智能体管理与扩展

### 工具目录与 MCP 注册

`SubAgentDefinitionAdminController` 提供 `/tool-catalog` 接口暴露工具名列表。MCP 工具通过 `McpRegistry` 动态发现并注册到 `ToolCollection`。

Sources: [SubAgentDefinitionAdminController.java](Reactor-agent-trigger/src/main/java/org/wwz/ai/trigger/http/admin/SubAgentDefinitionAdminController.java#L1-L194)
Sources: [McpRegistry.java](Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/mcp/runtime/McpRegistry.java)

### 前端接入

UI 页面 `SubAgentAdmin` 与 `FeaturedConversations` 提供子智能体管理入口，调用 Admin 服务实现创建/更新/删除/重载。工具调用通过 `AgentDispatchTool` 触发 SSE 事件。

Sources: [SubAgentAdmin/index.tsx](ui/src/pages/SubAgentAdmin/index.tsx)
Sources: [SubAgentDefinitionAdminApplicationService.java](Reactor-agent-case/src/main/java/org/wwz/ai/application/agent/subagent/SubAgentDefinitionAdminApplicationService.java#L1-L130)

## 最佳实践与注意事项

- **子 Agent 隔离**：始终使用 `SubAgentRunner` 派发，避免直接调用 ReactImplAgent。
- **工具过滤**：Plan Mode 下 Explore 子 Agent 会自动剥离写工具。
- **Schema 稳定**：使用 `ToolSchemaNormalizer` 确保 schema 序列化一致性。
- **上下文共享**：工具与子 Agent 可共享 `ToolArtifactRegistry` 与执行账本。

Sources: [SubAgentDefinitionRepositoryTest.java](Reactor-agent-app/src/test/java/org/wwz/ai/test/domain/SubAgentDefinitionRepositoryTest.java#L1-L137)
Sources: [AgentToolCollectionFactoryTest.java](Reactor-agent-app/src/test/java/org/wwz/ai/test/spring/ai/AgentToolCollectionFactoryTest.java#L1-L482)

**Next Section**: [MCP 注册与协议适配](20-mcp-zhu-ce-yu-xie-yi-gua-pei)
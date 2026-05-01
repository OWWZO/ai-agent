# 工具展示层收口与流式工具渐进重构设计

## 1. 背景与问题

### 1.1 当前事实

当前 Reactor 主链路里，工具执行与前端展示是耦合在一起的：

1. `BaseAgent.executeTools()` 会并行执行多个 `toolCall`
2. 多个特殊工具会在工具内部直接调用 `agentContext.getPrinter().send(...)`
3. `ExecutorAgent`、`ReactImplAgent` 无法判断某个工具是否已经自行推送过展示消息，因此只能维护“不要再补发 `tool_result`”的硬编码黑名单
4. 前端对工具卡片的归并并不只依赖“这是哪个工具”，而是依赖 `messageId`、`messageType` 以及少量特定阶段语义

这说明当前真正混乱的不是“工具是否流式”，而是“谁负责展示、如何生成展示身份、何时补发默认工具结果”。

### 1.2 当前问题

1. **展示职责分散**
   - 工具一边执行业务，一边直接面向前端发消息
   - 外层 Agent 又要决定是否再补发一次通用 `tool_result`
   - 同一类职责分散在工具内部和 Agent 外层，边界不清晰

2. **黑名单不可维护**
   - `ExecutorAgent` / `ReactImplAgent` 里维护了工具名黑名单
   - 新增一个“自己负责展示”的工具时，除了写工具本身，还必须同步修改多个 Agent
   - 这不是可扩展设计，而是补丁式维护

3. **展示身份缺少统一规则**
   - `deep_search` 的 `search` / `report` 需要拆成不同卡片
   - `multimodalagent_tool` 会在同一次调用下发 `knowledge` 和 `markdown`
   - `image_generation_tool` 需要保留 `file` 产物展示，同时外层还能补一条 `tool_result`
   - 这类规则本质上是“展示编排”，不应该散落在各个工具里各自维护

4. **重复的流式解析逻辑**
   - 多个工具都在重复处理 `data:`
   - 都在过滤 `[DONE]` / `heartbeat`
   - 都在自己处理异常、超时、最终结果拼接
   - 这些重复确实需要后续收敛，但它们属于“传输层辅助抽象”，不等于必须先上新的执行框架

5. **现有运行时约束不能被绕开**
   - `ToolArtifactSource` 已经保证了工具产物按 `toolCallId` 归属
   - 执行账本已经围绕 `preRegisterToolInvocations()`、`finishToolInvocation()`、`recordToolArtifacts()` 稳定工作
   - 本次重构应优先复用现有事实模型，而不是另起一套执行主干

### 1.3 根因判断

当前问题的根因是：

- 缺少统一的**工具展示发射器**
- 缺少声明式的**工具展示策略**
- 缺少服务端统一维护的**展示身份分配规则**

因此，本次重构应先统一“展示层”，而不是先统一“执行层”。

## 2. 目标与非目标

### 2.1 目标

1. 统一工具向前端发送展示消息的入口
2. 用声明式策略替代 Agent 中的黑名单
3. 为每个 `toolCall` 下的不同展示槽位分配稳定 `messageId`
4. 保持现有 `BaseAgent.executeTools()`、执行账本、工具产物登记模型不变
5. 支持逐个工具低风险迁移，不要求一次性重写全部特殊工具
6. 为后续抽取公共 SSE / 流式 HTTP 支持类打基础

### 2.2 非目标

1. 本期**不**引入 `Flux<ToolEvent>` 作为强制执行契约
2. 本期**不**重写 `BaseAgent.executeTools()` 的主执行模型
3. 本期**不**修改数据库表、执行账本契约、工具产物持久化模型
4. 本期**不**要求前端大改渲染协议
5. 本期**不**把所有工具都归类为“流式工具”

## 3. 核心设计结论

### 3.1 先统一展示层，再渐进统一传输层

本次主方案不采用“先定义 `StreamingTool`、再引入 `ToolStreamExecutor` 统一接管所有流式工具”的路线。

改为：

1. 工具继续沿用当前 `BaseTool.execute(Object)` 执行契约
2. 引入 `ToolDisplayEmitter` 统一负责向前端发送展示消息
3. 引入 `ToolDisplayPolicy` 明确每个工具的展示责任
4. 对现有特殊工具做渐进迁移
5. 等展示职责收敛后，再考虑是否进一步抽象公共流式传输支持

### 3.2 用策略替代黑名单

“某个工具执行后，Agent 是否还要补发默认 `tool_result`”不应由黑名单决定，而应由工具自身声明。

本次引入 `ToolDisplayPolicy`：

- `STANDARD_ONLY`
  - 工具自己不做前端展示
  - Agent 执行完后补发一条标准 `tool_result`
- `SELF_RENDERED`
  - 工具自己通过 `ToolDisplayEmitter` 发展示消息
  - Agent 不再补发默认 `tool_result`
- `HYBRID`
  - 工具自己发主要展示消息
  - Agent 仍允许补发标准 `tool_result`

### 3.3 展示身份以“toolCall + 槽位”建模

仅靠“一个 `toolCall` 对应一个 `messageId`”是不够的。

服务端需要显式区分同一次工具调用下的多个展示槽位：

- `deep_search`
  - `search`
  - `report`
- `multimodalagent_tool`
  - `knowledge`
  - `markdown`
- `image_generation_tool`
  - `artifact`

因此稳定展示身份的生成规则应为：

`toolCallId + slotKey + messageType -> stable messageId`

其中：

- `toolCallId`：同一次工具调用唯一标识
- `slotKey`：同一次工具调用内部的展示槽位
- `messageType`：前端已识别的消息类型，如 `deep_search`、`file`、`markdown`

### 3.4 现有执行账本与产物绑定模型保持不变

本方案不改动以下运行时主干：

1. `BaseAgent.preRegisterToolInvocations()`
2. `BaseAgent.finishToolInvocation()`
3. `BaseAgent.recordToolArtifacts()`
4. `ToolArtifactSource` 与 `toolCallId` 的绑定方式

工具仍然需要显式捕获并透传 `ToolArtifactSource` 到异步回调线程。

### 3.5 统一发射入口不等于强制引入新执行框架

本方案承认当前多个工具确实存在重复的流式解析逻辑，但这些逻辑优先收敛为：

- 公共流式 HTTP / SSE 解析支持类
- 公共异常与最终结果拼接规则

而不是先把所有工具抬升为新的 Reactor 流执行模型。

## 4. 详细设计

### 4.1 ToolDisplayPolicy

```java
package org.wwz.ai.domain.agent.reactor.agent.tool.display;

/**
 * 工具展示策略。
 * 用于声明 Agent 在工具执行结束后是否需要补发默认 tool_result。
 */
public enum ToolDisplayPolicy {

    /**
     * 工具自身不做前端展示，Agent 统一补发标准 tool_result。
     */
    STANDARD_ONLY,

    /**
     * 工具自身负责展示，Agent 不再补发默认 tool_result。
     */
    SELF_RENDERED,

    /**
     * 工具自身负责主要展示，同时允许 Agent 补发标准 tool_result。
     */
    HYBRID
}
```

### 4.2 ToolDisplayAware

为了保持 `BaseTool` 的执行契约简洁，本次不把展示策略直接塞进 `BaseTool`，而是新增一个可选接口：

```java
package org.wwz.ai.domain.agent.reactor.agent.tool.display;

/**
 * 工具展示感知接口。
 * 只有需要声明特殊展示行为的工具才实现该接口。
 */
public interface ToolDisplayAware {

    /**
     * 返回当前工具的展示策略。
     */
    default ToolDisplayPolicy displayPolicy() {
        return ToolDisplayPolicy.STANDARD_ONLY;
    }
}
```

默认情况下：

- 不实现该接口的工具，视为 `STANDARD_ONLY`
- 只有特殊展示工具才需要显式声明

### 4.3 ToolDisplayEmitter

`ToolDisplayEmitter` 是本次重构的核心。

职责：

1. 统一封装 `printer.send(...)`
2. 统一分配稳定 `messageId`
3. 为同一个 `toolCall` 的多个展示槽位维持独立展示身份
4. 统一解析数字员工
5. 为未来扩展“串行发送队列 / 节流 / 观测埋点”预留唯一入口

建议接口：

```java
package org.wwz.ai.domain.agent.reactor.agent.tool.display;

public interface ToolDisplayEmitter {

    /**
     * 发送结构化展示消息。
     *
     * @param slotKey     展示槽位，如 main/search/report/knowledge
     * @param messageType 前端识别的消息类型，如 deep_search/file/markdown
     * @param payload     原始展示载荷
     * @param isFinal     当前槽位是否已完成
     */
    void emit(String slotKey, String messageType, Object payload, boolean isFinal);

    /**
     * 获取某个槽位+消息类型的稳定 messageId。
     * 主要用于少量需要提前拿到 messageId 的兼容场景。
     */
    String resolveMessageId(String slotKey, String messageType);
}
```

建议实现要点：

1. 内部维护 `ConcurrentHashMap<String, String>`
   - Key：`slotKey + "#" + messageType`
   - Value：稳定 `messageId`
2. `emit(...)` 内部统一调用现有 `Printer`
3. `digitalEmployee` 由 `toolName -> ToolCollection.getDigitalEmployee(toolName)` 自动解析
4. 本期仍直接复用现有 `SSEPrinter` 协议，不改消息结构

### 4.4 ToolDisplayRegistry

`ToolDisplayEmitter` 需要按 `toolCallId` 获取，因此应在 `AgentContext` 内维护一个请求级注册表。

建议新增：

```java
package org.wwz.ai.domain.agent.reactor.agent.tool.display;

public class ToolDisplayRegistry {

    /**
     * 获取或创建指定 toolCall 的展示发射器。
     */
    public ToolDisplayEmitter getOrCreate(String toolCallId, String toolName, AgentContext context) {
        // ...
    }
}
```

`AgentContext` 中新增：

1. `ToolDisplayRegistry toolDisplayRegistry`
2. `ThreadLocal<ToolDisplayEmitter> currentToolDisplayEmitterHolder`
3. `bindCurrentToolDisplayEmitter(...)`
4. `clearCurrentToolDisplayEmitter()`
5. `requireCurrentToolDisplayEmitter(String toolName)`

这样同步工具可以直接从线程上下文拿 emitter，异步工具则在 `execute()` 里先取出 emitter 并显式透传到回调线程。

### 4.5 BaseAgent.executeToolInternal() 改造

`BaseAgent.executeToolInternal()` 仍然负责：

1. 参数解析
2. 构造 `ToolArtifactSource`
3. 绑定当前线程的工具运行时上下文
4. 调用 `availableTools.execute(...)`
5. 收口执行结果

新增动作：

1. 为当前 `toolCallId` 预创建 `ToolDisplayEmitter`
2. 在执行前将 emitter 绑定到 `AgentContext`
3. 执行后清理 emitter 线程上下文

伪代码：

```java
ToolArtifactSource artifactSource = buildArtifactSource(command, toolName);
ToolDisplayEmitter displayEmitter =
        context.getToolDisplayRegistry().getOrCreate(command.getId(), toolName, context);

context.bindCurrentToolArtifactSource(artifactSource);
context.bindCurrentToolDisplayEmitter(displayEmitter);
try {
    result = availableTools.execute(toolName, args);
} finally {
    context.clearCurrentToolDisplayEmitter();
    context.clearCurrentToolArtifactSource();
}
```

注意：

- 本次不改 `executeTools()` 的并行模型
- 不引入新的全局流执行器
- 执行账本登记仍沿用现有主干

### 4.6 Agent 外层默认结果发送逻辑改造

`ExecutorAgent` 与 `ReactImplAgent` 的黑名单应删除，改为统一根据策略判断。

建议新增辅助方法：

```java
private boolean shouldEmitDefaultToolResult(BaseTool tool) {
    if (tool instanceof ToolDisplayAware aware) {
        return aware.displayPolicy() != ToolDisplayPolicy.SELF_RENDERED;
    }
    return true;
}
```

然后将当前逻辑改为：

```java
BaseTool tool = availableTools.getTool(command.getFunction().getName());
if (shouldEmitDefaultToolResult(tool)) {
    printer.send("tool_result", AgentResponse.ToolResult.builder()
            .toolName(toolName)
            .toolParam(parseToolParam(command))
            .toolResult(result)
            .build(), null);
}
```

语义解释：

- `STANDARD_ONLY`：发送默认 `tool_result`
- `SELF_RENDERED`：不发送默认 `tool_result`
- `HYBRID`：发送默认 `tool_result`

### 4.7 特殊工具的迁移规则

| 工具 | 展示策略 | 槽位设计 | 说明 |
|------|---------|---------|------|
| `deep_search` | `SELF_RENDERED` | `search` / `report` | 保留两张卡片语义 |
| `data_analysis` | `SELF_RENDERED` | `main` | 工具自己发增量与最终内容 |
| `code_interpreter` | `SELF_RENDERED` | `main` | 工具自己发 `code` |
| `report_tool` | `SELF_RENDERED` | `main` | 工具自己发 `html/markdown/ppt/file` 等最终产物 |
| `file_tool` | `SELF_RENDERED` | `main` | 工具自己发 `file` |
| `multimodalagent_tool` | `SELF_RENDERED` | `knowledge` / `markdown` | 同一次调用拆分两类展示 |
| `image_generation_tool` | `HYBRID` | `artifact` | 保留 `file` 卡片，同时允许补 `tool_result` |
| 其他普通工具 | `STANDARD_ONLY` | `main` | 维持现状 |

### 4.8 DeepSearchTool 改造示例

改造前：

- 工具内部自己维护 `messageIdRef`
- 工具内部自己决定何时向 `printer` 发 `deep_search`

改造后：

```java
public class DeepSearchTool implements BaseTool, ToolDisplayAware {

    @Override
    public ToolDisplayPolicy displayPolicy() {
        return ToolDisplayPolicy.SELF_RENDERED;
    }

    private void emitSearchFrame(DeepSearchrResponse response, boolean isFinal) {
        ToolDisplayEmitter emitter = agentContext.requireCurrentToolDisplayEmitter(getName());
        emitter.emit("search", "deep_search", response, isFinal);
    }

    private void emitReportFrame(DeepSearchrResponse response, boolean isFinal) {
        ToolDisplayEmitter emitter = agentContext.requireCurrentToolDisplayEmitter(getName());
        emitter.emit("report", "deep_search", response, isFinal);
    }
}
```

收益：

1. 工具不再自己生成 `messageId`
2. `search` / `report` 卡片拆分规则从“隐式状态”变成“显式槽位”
3. Agent 外层不再关心它是不是黑名单

### 4.9 公共流式支持类放到第二阶段

当展示职责收敛后，再引入公共支持类，例如：

`StreamingHttpToolSupport`

可收敛的能力包括：

1. 统一过滤 `[DONE]`
2. 统一过滤 `heartbeat`
3. 统一 `BufferedReader` 逐行消费
4. 统一异常包装
5. 统一最终结果拼接
6. 统一取消句柄封装

注意：

- 这是**传输层辅助抽象**
- 不是本次方案的主轴
- 本期不强制升级到 Reactor `Flux`

## 5. 数据流

### 5.1 STANDARD_ONLY 工具

```text
LLM 返回 toolCall
    ↓
BaseAgent.executeToolInternal()
    ↓
普通工具 execute()
    ↓
返回字符串结果
    ↓
ExecutorAgent / ReactImplAgent 根据策略补发 tool_result
    ↓
结果写入记忆与执行账本
```

### 5.2 SELF_RENDERED 工具

```text
LLM 返回 toolCall
    ↓
BaseAgent.executeToolInternal()
    ↓
绑定 ToolArtifactSource + ToolDisplayEmitter
    ↓
特殊工具 execute()
    ↓
异步回调中调用 emitter.emit(...)
    ↓
工具返回最终文本结果给 Agent
    ↓
Agent 不再补发默认 tool_result
    ↓
结果写入记忆与执行账本
```

### 5.3 HYBRID 工具

```text
LLM 返回 toolCall
    ↓
BaseAgent.executeToolInternal()
    ↓
工具通过 emitter.emit(...) 发送主要展示（如 file）
    ↓
工具返回最终文本结果
    ↓
Agent 继续补发默认 tool_result
    ↓
前端沿用现有折叠逻辑进行整合
```

## 6. 为什么本期不采用 StreamingTool + ToolStreamExecutor

### 6.1 抽象层级偏高

当前特殊工具的差异主要在“展示语义”，不是在“是否都能抽象成同一种流执行接口”。

如果现在强行统一为：

- `StreamingTool`
- `ToolEvent.Stage`
- `ToolStreamExecutor`

会把本来是展示层的问题，过早上提为执行框架问题。

### 6.2 `toolCall -> 单个 messageId` 的假设不成立

现有前端已经证明：

1. `deep_search` 同一调用下需要区分 `search` / `report`
2. `multimodalagent_tool` 同一调用下需要区分 `knowledge` / `markdown`
3. `image_generation_tool` 存在 `file + tool_result` 组合展示

因此“一个 `toolCall` 预分配一个 `messageId`”无法表达真实展示语义。

### 6.3 不应为了统一而引入新的主执行框架

当前：

- 执行账本已经围绕 `BaseAgent.executeTools()` 稳定工作
- 工具产物已围绕 `ToolArtifactSource` 稳定绑定

如果本期直接引入新的统一流执行器，会同步触碰：

1. 工具执行结果收口
2. 工具终态判断
3. 账本回写时机
4. 产物归属时机

改动面过大，不符合本期“先解决真实痛点、再收敛传输层”的原则。

## 7. 改造范围

### 7.1 新增文件

| 文件 | 说明 |
|------|------|
| `agent/tool/display/ToolDisplayPolicy.java` | 工具展示策略枚举 |
| `agent/tool/display/ToolDisplayAware.java` | 可选展示策略声明接口 |
| `agent/tool/display/ToolDisplayEmitter.java` | 统一展示发射器接口 |
| `agent/tool/display/DefaultToolDisplayEmitter.java` | 默认展示发射器实现 |
| `agent/tool/display/ToolDisplayRegistry.java` | 请求级 emitter 注册表 |
| `agent/tool/support/StreamingHttpToolSupport.java` | 第二阶段可选的公共流式支持类 |

### 7.2 修改文件

| 文件 | 改动内容 |
|------|---------|
| `agent/agent/AgentContext.java` | 新增 `ToolDisplayRegistry` 与 emitter 线程上下文支持 |
| `agent/agent/BaseAgent.java` | 在工具执行期绑定 / 清理当前 emitter |
| `agent/agent/ExecutorAgent.java` | 用策略替代黑名单 |
| `agent/agent/ReactImplAgent.java` | 用策略替代黑名单 |
| `agent/tool/common/DeepSearchTool.java` | 改为通过 emitter 发送 `deep_search` |
| `agent/tool/common/DataAnalysisTool.java` | 改为通过 emitter 发送 `data_analysis` |
| `agent/tool/common/CodeInterpreterTool.java` | 改为通过 emitter 发送 `code` |
| `agent/tool/common/ReportTool.java` | 改为通过 emitter 发送产物展示 |
| `agent/tool/common/FileTool.java` | 改为通过 emitter 发送 `file` |
| `agent/tool/common/MultiModalAgent.java` | 改为通过 emitter 发送 `knowledge` / `markdown` |
| `agent/tool/common/ImageGenerationTool.java` | 改为通过 emitter 发送 `file`，保留混合策略 |

### 7.3 前端改动

本期目标是：

- 尽量不改前端协议
- 继续沿用现有 `messageType` 语义
- 继续沿用现有 `messageId` 驱动的渲染逻辑

前端只需验证：

1. `deep_search` 的 `search` / `report` 卡片行为不变
2. `multimodalagent_tool` 的 `knowledge` / `markdown` 行为不变
3. `image_generation_tool` 的 `file + tool_result` 折叠行为不变

## 8. 测试与验收标准

### 8.1 核心测试点

1. **展示身份测试**
   - 同一个 `toolCallId + slotKey + messageType` 多次发送时复用同一个 `messageId`
   - 不同 `slotKey` 能得到不同 `messageId`

2. **策略测试**
   - `STANDARD_ONLY` 工具执行后会补发默认 `tool_result`
   - `SELF_RENDERED` 工具执行后不会补发默认 `tool_result`
   - `HYBRID` 工具执行后仍会补发默认 `tool_result`

3. **运行时兼容测试**
   - `ToolArtifactSource` 在线程内仍能正确绑定
   - 异步工具回调里仍能正确登记产物
   - 执行账本的开始、结束、产物登记时机不变

4. **前端回归测试**
   - `deep_search` 两阶段卡片不串位
   - `multimodalagent_tool` 的 `knowledge` / `markdown` 不串位
   - `image_generation_tool` 仍能正确折叠

### 8.2 功能验收

1. `ExecutorAgent`、`ReactImplAgent` 中已无特殊工具黑名单
2. 已迁移的特殊工具内部不再直接调用 `agentContext.getPrinter().send(...)`
3. 已迁移工具全部通过 `ToolDisplayEmitter` 发前端展示消息
4. 非特殊工具行为保持不变
5. 执行账本与工具产物持久化无回归

### 8.3 范围控制验收

1. 本期未引入 `Flux<ToolEvent>` 强制契约
2. 本期未重写 `BaseAgent.executeTools()` 主执行模型
3. 本期未引入新的数据库表或消息持久化模型
4. 本期完成后，若仍需进一步减少流式工具重复代码，再单独推进公共传输支持层重构

## 9. 结论

本次重构的正确切入点不是“先统一流式执行层”，而是“先统一工具展示层”。

只有先把下面三件事收口：

1. 谁负责向前端发消息
2. 一个工具调用下有哪些展示槽位
3. Agent 是否需要补发默认 `tool_result`

后续的流式解析、HTTP/SSE 支持类抽象、甚至更进一步的 `StreamingTool` 契约，才会有稳定边界。

因此，本方案采用：

- **展示层优先收口**
- **执行层保持稳定**
- **传输层渐进抽象**

这比一次性引入新的统一流执行框架更符合当前代码库的真实问题、现有约束与演进节奏。

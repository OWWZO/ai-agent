# StreamingTool 统一流式执行层设计

## 1. 背景与问题

当前项目中有 4 个流式工具（DeepSearchTool、DataAnalysisTool、CodeInterpreterTool、ReportTool），它们各自在工具内部直接调用 `agentContext.getPrinter().send()` 向前端推送流式消息。这种设计导致以下问题：

1. **代码重复**：每个流式工具都独立实现了同一套逻辑（OkHttp SSE 连接 → 解析 → 过滤 `[DONE]`/`heartbeat` → 调用 printer.send）
2. **并行流冲突**：`BaseAgent.executeTools()` 通过线程池并行执行多个 toolCall。当 LLM 一次返回多个流式工具调用时，多个线程同时往同一个 `SseEmitter` 写消息，消息会交错混叠
3. **前端无法区分并行流**：各工具自行生成 `messageId`，没有统一协调，前端无法将增量消息正确关联到对应工具的任务卡片
4. **ExecutorAgent 黑名单**：`ExecutorAgent.act()` 中维护了一个硬编码黑名单 `Arrays.asList("code_interpreter", "report_tool", ...)` 来跳过已内部推送的工具，新增流式工具必须同步修改黑名单
5. **缺乏统一收口**：取消信号、超时处理、错误处理、执行账本记录分散在各个工具内部，没有统一管理层

## 2. 目标

- 抽象 `StreamingTool` 接口，统一流式工具的契约
- 标准化 `ToolEvent`，让执行层能理解工具产出的每一帧数据的语义
- 引入 `ToolStreamExecutor` 统一执行层，负责并行流隔离、消息推送、生命周期管理
- 移除 `ExecutorAgent` 的黑名单机制
- 保证现有功能零回归

## 3. 方案设计

### 3.1 核心架构

```
改造前：
┌─────────────┐  printer.send()  ┌─────────┐
│ DeepSearch  │ ───────────────► │ 前端    │
│ DataAnalysis│ ───────────────► │ (混乱)  │
│ CodeInterp  │ ───────────────► │        │
│ Report      │ ───────────────► │        │
└─────────────┘                  └─────────┘

改造后：
┌─────────────┐  ToolEvent 流    ┌─────────────────┐  统一推送   ┌─────────┐
│ DeepSearch  │ ───────────────► │                 │ ────────► │ 前端    │
│ DataAnalysis│ ───────────────► │ ToolStreamExecutor│          │ (有序)  │
│ CodeInterp  │ ───────────────► │                 │          │        │
│ Report      │ ───────────────► │ - 分配 messageId │          │        │
└─────────────┘                  │ - 合并多个 Flux   │          │        │
                                 │ - 控制推送节奏    │          │        │
                                 │ - 统一收口      │          │        │
                                 └─────────────────┘          └─────────┘
```

### 3.2 关键原则

- **工具只产不管**：工具只负责产出 `ToolEvent` 流，不直接调用 printer
- **执行层统管统推**：`ToolStreamExecutor` 负责所有与前端交互的推送逻辑
- **并行隔离**：每个 toolCall 分配独立的 `messageId`，前端按 `messageId` 分组渲染
- **向后兼容**：非流式工具（`BaseTool`）保持原有执行路径不变

## 4. 组件详细设计

### 4.1 StreamingTool 接口

```java
package org.wwz.ai.domain.agent.reactor.agent.tool;

import reactor.core.publisher.Flux;

/**
 * 流式工具接口。实现类只负责产出事件流，不直接操作 Printer。
 */
public interface StreamingTool extends BaseTool {

    /**
     * 执行流式工具，返回事件流。
     *
     * @param input      工具参数（由 execute(Object input) 的参数序列化而来）
     * @param toolCallId 本次调用的唯一标识（执行层分配，用于并行隔离）
     * @return 工具事件流
     */
    Flux<ToolEvent> executeStreaming(Object input, String toolCallId);
}
```

**设计决策：**
- 继承 `BaseTool`，保证非流式调用方仍可使用 `execute(Object)` 方法（可提供一个默认适配实现）
- `executeStreaming` 返回 `Flux<ToolEvent>`，利用 Project Reactor 的流式抽象
- `toolCallId` 由执行层传入，确保并行时各流可区分

### 4.2 ToolEvent 标准化事件

```java
package org.wwz.ai.domain.agent.reactor.agent.tool;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流式工具产出的标准化事件。
 * 所有 StreamingTool 的实现都必须将内部数据转换为 ToolEvent。
 */
@Data
@Builder
public class ToolEvent {

    /** 本次 toolCall 的唯一标识（用于并行隔离） */
    private String toolCallId;

    /** 事件阶段 */
    private Stage stage;

    /** 展示给用户的文本描述 */
    private String message;

    /** 工具特有的结构化数据（如 DeepSearchResponse 等） */
    private Object payload;

    /** 是否是该工具的最后一条事件 */
    private boolean isFinal;

    /** 事件时间戳 */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public enum Stage {
        /** 工具开始执行 */
        STARTED,
        /** 中间进度 */
        PROGRESS,
        /** 产出了文件/产物 */
        ARTIFACT,
        /** 执行成功完成 */
        COMPLETED,
        /** 执行出错 */
        ERROR
    }
}
```

**为什么必须标准化：**
- `ToolStreamExecutor` 需要理解事件的语义（是否是最终结果、是否出错），才能统一决策（记账本、推前端、触发取消）
- `payload` 字段保留工具的原始数据结构，前端仍可按原有逻辑解析
- `Stage.ERROR` 让执行层能统一捕获异常并推送给前端，工具内部不再需要 try-catch 后调用 printer

### 4.3 ToolStreamExecutor 统一执行层

```java
package org.wwz.ai.domain.agent.reactor.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.reactor.agent.enums.RoleType;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式工具统一执行器。
 * 负责管理多个 StreamingTool 的并行执行、事件合并、统一推送和生命周期收口。
 */
@Slf4j
@Component
public class ToolStreamExecutor {

    /**
     * 执行一组流式工具调用。
     *
     * @param commands       toolCall 列表
     * @param availableTools 可用工具集合
     * @param context        Agent 上下文（包含 printer、requestId 等）
     * @return 各 toolCallId → ToolExecutionOutcome 的映射
     */
    public Map<String, BaseAgent.ToolExecutionOutcome> executeStreamingTools(
            List<ToolCall> commands,
            ToolCollection availableTools,
            AgentContext context) {

        Map<String, BaseAgent.ToolExecutionOutcome> results = new ConcurrentHashMap<>();
        Map<String, String> messageIdMap = new HashMap<>();
        Map<String, List<ToolEvent>> eventBuffers = new ConcurrentHashMap<>();
        Map<String, AtomicReference<String>> finalResultMap = new ConcurrentHashMap<>();

        // 1. 为每个 toolCall 预分配 messageId
        for (ToolCall cmd : commands) {
            messageIdMap.put(cmd.getId(), UUID.randomUUID().toString());
            eventBuffers.put(cmd.getId(), Collections.synchronizedList(new ArrayList<>()));
            finalResultMap.put(cmd.getId(), new AtomicReference<>(""));
        }

        // 2. 为每个 toolCall 建立 toolCallId → toolName 映射（供 handleEvent 使用）
        Map<String, String> toolCallIdToNameMap = new HashMap<>();
        for (ToolCall cmd : commands) {
            toolCallIdToNameMap.put(cmd.getId(), cmd.getFunction().getName());
        }

        // 3. 为每个 StreamingTool 单独启动 Flux，独立追踪终止状态
        CountDownLatch latch = new CountDownLatch(commands.size());
        List<Disposable> disposables = new ArrayList<>();

        for (ToolCall cmd : commands) {
            BaseTool tool = availableTools.getTool(cmd.getFunction().getName());
            if (!(tool instanceof StreamingTool streamingTool)) {
                latch.countDown();
                continue;
            }

            Object input = parseToolInput(cmd);
            String toolCallId = cmd.getId();
            String toolName = cmd.getFunction().getName();

            Flux<ToolEvent> flux = streamingTool.executeStreaming(input, toolCallId)
                    .doOnTerminate(() -> {
                        log.info("{} StreamingTool {} 流终止", context.getRequestId(), toolName);
                        latch.countDown();
                    });

            Disposable disposable = flux.subscribe(
                    event -> handleEvent(event, messageIdMap, eventBuffers, finalResultMap,
                            toolCallIdToNameMap, context),
                    error -> log.error("{} StreamingTool {} 流报错", context.getRequestId(), toolName, error)
            );
            disposables.add(disposable);
        }

        // 4. 等待所有流结束（保留超时机制）
        try {
            boolean allDone = latch.await(20, TimeUnit.MINUTES);
            if (!allDone) {
                log.warn("{} StreamingTool 执行超时，强制取消", context.getRequestId());
                disposables.forEach(Disposable::dispose);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            disposables.forEach(Disposable::dispose);
            log.warn("{} StreamingTool 执行被中断", context.getRequestId());
        }

        // 5. 组装最终结果
        for (ToolCall cmd : commands) {
            String finalResult = finalResultMap.get(cmd.getId()).get();
            results.put(cmd.getId(), BaseAgent.ToolExecutionOutcome.success(finalResult, finalResult, null));
        }

        return results;
    }

    private void handleEvent(ToolEvent event,
                             Map<String, String> messageIdMap,
                             Map<String, List<ToolEvent>> eventBuffers,
                             Map<String, AtomicReference<String>> finalResultMap,
                             Map<String, String> toolCallIdToNameMap,
                             AgentContext context) {
        String toolCallId = event.getToolCallId();
        String messageId = messageIdMap.get(toolCallId);

        // 缓冲事件（用于最终组装结果）
        eventBuffers.get(toolCallId).add(event);

        // 记录最终文本（用于返回给 LLM）
        if (event.getMessage() != null) {
            finalResultMap.get(toolCallId).updateAndGet(old -> old + event.getMessage());
        }

        // 根据 stage 决定推送给前端的消息类型
        String toolName = toolCallIdToNameMap.get(toolCallId);
        String digitalEmployee = context.getToolCollection().getDigitalEmployee(toolName);

        switch (event.getStage()) {
            case PROGRESS, STARTED ->
                    context.getPrinter().send(messageId, toolName, event.getPayload(), digitalEmployee, false);
            case ARTIFACT -> {
                // 产物事件可推送，也可仅记录
                context.getPrinter().send(messageId, toolName, event.getPayload(), digitalEmployee, false);
            }
            case COMPLETED -> {
                context.getPrinter().send(messageId, toolName, event.getPayload(), digitalEmployee, true);
                // 标记该 toolCall 已完成
            }
            case ERROR -> {
                context.getPrinter().send(messageId, "tool_result",
                        AgentResponse.ToolResult.builder()
                                .toolName(toolName)
                                .toolResult(event.getMessage())
                                .build(),
                        digitalEmployee, true);
            }
        }
    }

    /**
     * 将 ToolCall 的参数 JSON 字符串解析为 Object。
     * 复用现有 ObjectMapper 逻辑。
     */
    private Object parseToolInput(ToolCall cmd) {
        String arguments = cmd.getFunction().getArguments();
        if (StringUtils.isBlank(arguments)) {
            return new HashMap<>();
        }
        try {
            return new ObjectMapper().readValue(arguments, Object.class);
        } catch (Exception e) {
            log.warn("解析 tool arguments 失败: {}", arguments);
            return new HashMap<>();
        }
    }
}
```

**关键设计点：**

| 能力 | 实现方式 |
|------|---------|
| 并行隔离 | 每个 toolCall 预分配独立 `messageId`，前端按 `messageId` 分组渲染 |
| 流合并 | 每个流单独订阅，通过 `doOnTerminate()` 独立追踪终止状态，避免一个流报错影响其他流 |
| 线程安全 | 只有一个订阅者线程调用 `printer.send()`，避免并发写 `SseEmitter` |
| 取消收口 | 持有 `Disposable` 引用，Agent 中断时调用 `dispose()` 取消所有流 |
| 超时控制 | `CountDownLatch.await(20, MINUTES)`，超时后强制 dispose |
| 结果收集 | `finalResultMap` 收集所有事件 message，组装后返回给 LLM |

### 4.4 BaseAgent.executeTools() 改造

```java
public Map<String, ToolExecutionOutcome> executeTools(List<ToolCall> commands) {
    Map<String, ToolExecutionOutcome> result = new ConcurrentHashMap<>();
    if (commands == null || commands.isEmpty()) {
        return result;
    }

    // 判断是否有流式工具
    boolean hasStreaming = commands.stream()
            .anyMatch(cmd -> availableTools.getTool(cmd.getFunction().getName())
                    instanceof StreamingTool);

    if (hasStreaming) {
        // 统一走 ToolStreamExecutor
        return toolStreamExecutor.executeStreamingTools(commands, availableTools, context);
    }

    // 非流式工具，保持原有并行逻辑不变
    Map<String, Long> toolInvocationIds = preRegisterToolInvocations(commands);
    // ... 原有代码不变
}
```

### 4.5 ExecutorAgent.act() 改造

移除黑名单：

```java
@Override
public String act() {
    if (toolCalls.isEmpty()) {
        // ... 原有逻辑
    }

    Map<String, String> toolResults = executeTools(toolCalls);

    List<String> results = new ArrayList<>();
    for (ToolCall command : toolCalls) {
        String result = toolResults.get(command.getId());

        // 删除以下黑名单逻辑：
        // if (!Arrays.asList("code_interpreter", "report_tool", "file_tool",
        //         "deep_search", "multimodalagent_tool", "data_analysis")
        //         .contains(command.getFunction().getName())) {
        //     printer.send("tool_result", ...);
        // }

        // StreamingTool 的推送已由 ToolStreamExecutor 统一处理
        // ExecutorAgent 只负责收集结果塞给 LLM

        if (maxObserve != null) {
            result = result.substring(0, Math.min(result.length(), maxObserve));
        }
        result = attachToolArtifactSummary(result, command.getId());

        Message toolMsg = Message.toolMessage(result, command.getId(), null);
        getMemory().addMessage(toolMsg);
        results.add(result);
    }
    return String.join("\n\n", results);
}
```

### 4.6 DeepSearchTool 改造示例

```java
public class DeepSearchTool implements StreamingTool {

    @Override
    public Flux<ToolEvent> executeStreaming(Object input, String toolCallId) {
        return Flux.create(sink -> {
            // 解析参数，构建请求
            Map<String, Object> params = (Map<String, Object>) input;
            String query = (String) params.get("query");
            DeepSearchRequest request = buildRequest(query);

            // OkHttp EventSource 监听 SSE 流
            EventSourceListener listener = new EventSourceListener() {
                @Override
                public void onEvent(EventSource source, String id, String type, String data) {
                    if ("[DONE]".equals(data) || data.startsWith("heartbeat")) {
                        return;
                    }
                    DeepSearchrResponse resp = JSONObject.parseObject(data, DeepSearchrResponse.class);

                    // 将内部响应转换为 ToolEvent
                    ToolEvent.Stage stage = mapMessageTypeToStage(resp.getMessageType());
                    sink.next(ToolEvent.builder()
                            .toolCallId(toolCallId)
                            .stage(stage)
                            .message(resp.getAnswer())
                            .payload(resp)
                            .isFinal(resp.getIsFinal())
                            .build());
                }

                @Override
                public void onClosed(EventSource source) {
                    sink.complete();
                }

                @Override
                public void onFailure(EventSource source, Throwable t, Response response) {
                    sink.error(t);
                }
            };

            // 启动连接
            EventSource.Factory factory = EventSources.createFactory(client);
            factory.newEventSource(buildHttpRequest(request), listener);
        });
    }

    private ToolEvent.Stage mapMessageTypeToStage(String messageType) {
        return switch (messageType) {
            case "extend" -> ToolEvent.Stage.STARTED;
            case "search" -> ToolEvent.Stage.PROGRESS;
            case "report" -> ToolEvent.Stage.PROGRESS;
            default -> ToolEvent.Stage.PROGRESS;
        };
    }

    // ... 原有辅助方法（buildRequest、超时配置等）保留
}
```

**改造要点：**
- 删除所有 `agentContext.getPrinter().send()` 调用
- 删除 `CompletableFuture` 管理逻辑（由 ToolStreamExecutor 管理）
- 删除 `messageIdRef` 管理（由 ToolStreamExecutor 分配）
- 内部响应通过 `mapMessageTypeToStage` 映射为标准化 `ToolEvent.Stage`

## 5. 改造范围

### 5.1 新增文件

| 文件 | 说明 |
|------|------|
| `agent/tool/StreamingTool.java` | 流式工具接口 |
| `agent/tool/ToolEvent.java` | 标准化事件 |
| `agent/tool/ToolStreamExecutor.java` | 统一执行层 |

### 5.2 修改文件

| 文件 | 改动内容 |
|------|---------|
| `agent/agent/BaseAgent.java` | `executeTools()` 识别 StreamingTool 并分流 |
| `agent/agent/ExecutorAgent.java` | 删除黑名单逻辑 |
| `agent/tool/common/DeepSearchTool.java` | 实现 `StreamingTool` |
| `agent/tool/common/DataAnalysisTool.java` | 实现 `StreamingTool` |
| `agent/tool/common/CodeInterpreterTool.java` | 实现 `StreamingTool` |
| `agent/tool/common/ReportTool.java` | 实现 `StreamingTool` |
| `agent/tool/ToolCollection.java` | 可能需增加 `getTool(String name)` 方法 |

### 5.3 前端改动

前端现有逻辑已按 `messageId` 分组渲染消息，改造后只需确认：
- 同 `messageId` 的消息增量更新同一卡片
- 不同 `messageId` 的消息创建独立卡片

预计前端无需改动，或仅需微调。

## 6. 数据流

```
用户提问
    ↓
ExecutorAgent.think() → LLM 返回 2 个 toolCalls
    ↓
BaseAgent.executeTools([call_1, call_2])
    ↓
识别到 StreamingTool → 调用 ToolStreamExecutor.executeStreamingTools()
    ↓
┌──────────────────────────────────────────────────────────────┐
│  ToolStreamExecutor                                          │
│  1. 分配 messageId_A → call_1, messageId_B → call_2         │
│  2. 启动 DeepSearchTool.executeStreaming(call_1)            │
│     启动 DataAnalysisTool.executeStreaming(call_2)          │
│  3. Flux.merge(流A, 流B).subscribe(...)                     │
│  4. 收到 ToolEvent(toolCallId=call_1) →                     │
│        printer.send(messageId_A, "deep_search", payload, ...)│
│     收到 ToolEvent(toolCallId=call_2) →                     │
│        printer.send(messageId_B, "data_analysis", payload, ...)│
│  5. 所有流 complete → 组装结果返回                           │
└──────────────────────────────────────────────────────────────┘
    ↓
ExecutorAgent.act() → 结果塞入 LLM 记忆
    ↓
下一轮对话
```

## 7. 错误处理

| 场景 | 处理策略 |
|------|---------|
| 单个工具流报错 | `sink.error(t)` → `ToolStreamExecutor` subscribe 的 error 回调记录日志，该流标记为失败，不影响其他并行流 |
| 超时 | `CountDownLatch.await(20, MINUTES)` 超时后 `disposable.dispose()`，取消所有活跃流 |
| Agent 被中断 | `BaseAgent` 在 catch 块中通知 `ToolStreamExecutor` 取消所有流 |
| SSE 连接断开 | 工具内部 `onFailure` 中调用 `sink.error(t)`，由执行层统一处理 |
| 前端断开连接 | `SseEmitter` 异常由 `SSEPrinter` 现有逻辑捕获，不影响后端执行 |

## 8. 兼容性

- **`ToolExecutionOutcome` 访问权限**：当前为 `BaseAgent` 的 `private static class`，需提升为 `public static class`，供 `ToolStreamExecutor` 引用
- **非流式工具**：不受影响，继续走原有 `executeTools()` 并行逻辑
- **Spring AI ToolCallback 适配**：`BaseToolCallbackAdapter` 无需改动，它适配的是 `BaseTool.execute()` 方法
- **执行账本**：`preRegisterToolInvocations()` 和 `finishToolInvocation()` 在流式路径中仍被调用（在 `ToolStreamExecutor` 中维护调用点）
- **产物注册**：工具内部仍可调用 `agentContext.registerGeneratedArtifact()`（产物注册与推送是独立操作）

## 9. 验收标准

1. **功能验收**
   - [ ] DeepSearchTool、DataAnalysisTool、CodeInterpreterTool、ReportTool 均实现 `StreamingTool` 接口
   - [ ] 各工具内部不再出现 `agentContext.getPrinter().send()` 调用
   - [ ] `ExecutorAgent` 黑名单已删除
   - [ ] LLM 返回单个流式工具调用时，前端正常显示流式进度

2. **并行隔离验收**
   - [ ] 构造测试场景：LLM 同时返回 deep_search + data_analysis 两个 toolCall
   - [ ] 前端两个任务卡片独立渲染，消息不混叠
   - [ ] 两个工具的结果分别正确返回给 LLM

3. **回归验收**
   - [ ] 非流式工具（如 FileTool）执行不受影响
   - [ ] 原有单步执行场景（只有一个 toolCall）行为不变
   - [ ] SSE 连接异常时，工具能正确报错，不会阻塞 Agent

4. **代码验收**
   - [ ] 新增代码单元测试覆盖率 ≥ 60%
   - [ ] 无 `TODO` 遗留（本设计中的 TODO 需在实现阶段完成）

# LLM.java Spring AI 重构设计文档

## 背景

`LLM.java` 当前是一个 1789 行的庞大类，直接使用 OkHttp 手动调用 OpenAI 兼容的 HTTP API，内部包含大量手动格式转换逻辑（GPT/Claude 消息格式、工具格式、SSE 流解析等）。项目已通过 Armory 装配流水线统一管理 `ChatClient` 的创建和生命周期，但 `LLM.java` 独立于该体系之外，自行管理配置缓存和 HTTP 调用。

## 目标

用 Spring AI API 重构 `LLM.java`，充分利用 Spring AI 的抽象能力，复用项目已有的 Armory 装配流水线，**不改变对外调用逻辑**。

## 非目标

- 不改变 `Agent` 等上层调用方的代码
- 不删除 `struct_parse` 自定义工具调用模式（Spring AI 无对应抽象）
- 不改动 Armory 装配流水线的现有节点链

---

## 第一章：架构总览

### 核心转变

让 `LLM` 从"**自管理配置 + 手动 HTTP 调用**"转变为"**消费 Armory 流水线已装配好的 Spring AI OpenAiChatModel**"。

### 重构前后对比

```
【重构前】                    【重构后】
Agent (Planning/React/...)   Agent (Planning/React/...)
    │                              │
    ▼ call ask/askTool             ▼ call ask/askTool
┌─────────────┐             ┌─────────────────────────┐
│    LLM      │             │     LLM (精简后)        │
│  1789 行    │             │    ~300 行门面          │
│  - Config   │             │  - 消息转换             │
│  - OkHttp   │             │  - 参数覆盖             │
│  - Map格式  │             │  - 调用编排             │
│  - 手动解析 │             │  - struct_parse逻辑     │
│  - 缓存池   │             │  - 流式推送逻辑         │
└──────┬──────┘             └───────────┬─────────────┘
       │ HTTP                             │ use ChatModel
       ▼                                  ▼
┌─────────────┐             ┌─────────────────────────┐
│ OpenAI API  │             │   OpenAiChatModel       │
│ (外部服务)   │             │   (Armory 流水线创建)    │
└─────────────┘             │   - 已注册在 Spring 容器 │
                            │   - 复用统一配置        │
                            └─────────────────────────┘
```

### 关键设计原则

1. **不复建缓存**：删除 `LLM` 类内的 `instances` 缓存池，ChatClient/ChatModel 生命周期由 Armory 流水线和 Spring 容器统一管理
2. **不复建配置**：模型配置统一走 Armory 的 `DynamicContext` 数据加载
3. **不复建 HTTP 调用**：删除全部 OkHttp 方法，使用 `chatModel.call()` / `chatModel.stream()`
4. **不复建格式转换**：GPT/Claude 消息格式差异由 Spring AI 内部处理，不再需要 `formatMessages` 中的模型判断和 `gptToClaudeTool`

---

## 第二章：组件设计

### 2.1 `LlmChatClientResolver` — ChatModel 解析器

**职责**：根据 `modelName` 从 Armory 流水线已注册的 Bean 中解析出对应的 `OpenAiChatModel`，替代原 `instances` 缓存池。

**核心设计**：Armory 的 `AiClientModelNode` 已经将 `OpenAiChatModel` 注册为 `ai_client_model_{modelId}` Bean，`LlmChatClientResolver` 负责建立 `modelName → Bean` 的映射。

```java
@Service
public class LlmChatClientResolver {

    @Resource
    private ApplicationContext applicationContext;

    /**
     * 根据模型名称解析 OpenAiChatModel。
     * 优先从 Armory 已注册的 ai_client_model_* Bean 中查找，
     * 找不到时兜底创建（保持兼容性）。
     */
    public OpenAiChatModel resolveModel(String modelName) {
        // 遍历 Spring 容器中所有 OpenAiChatModel 类型的 bean
        // 找到 modelName 匹配的（通过检查 bean 的 defaultOptions.model）
        // 或者通过已知的映射关系查找
    }
}
```

**为什么返回 `OpenAiChatModel` 而非 `ChatClient`**：
- Armory 的 `AiClientNode` 创建的 `ChatClient` 带有 `defaultSystem`、`defaultAdvisors`、`defaultToolCallbacks`，是对话场景的配置
- LLM 是底层引擎，需要**干净无默认配置**的模型实例
- `OpenAiChatModel` 是纯模型层，每次调用时通过 `OpenAiChatOptions` 动态指定参数，更灵活

### 2.2 `DomainMessageConverter` — 消息类型转换器

**职责**：将项目的 `Message` DTO 转换为 Spring AI 的 `Message` 类型体系。替代原 `formatMessages` 中 200+ 行的手动 Map 组装。

**核心设计**：利用 Spring AI 的类型体系（`UserMessage`、`SystemMessage`、`AssistantMessage`、`ToolResponseMessage`），不再需要 `isClaude` 分支判断——Spring AI 内部处理 GPT/Claude 格式差异。

```java
@Component
public class DomainMessageConverter {

    public List<org.springframework.ai.chat.messages.Message> convert(List<Message> messages) {
        return messages.stream().map(this::convertSingle).toList();
    }

    private org.springframework.ai.chat.messages.Message convertSingle(Message msg) {
        return switch (msg.getRole()) {
            case USER -> toUserMessage(msg);
            case ASSISTANT -> toAssistantMessage(msg);
            case SYSTEM -> new SystemMessage(msg.getContent());
            case TOOL -> toToolResponseMessage(msg);
        };
    }

    private UserMessage toUserMessage(Message msg) {
        if (msg.getBase64Image() != null && !msg.getBase64Image().isEmpty()) {
            Media media = new Media(MimeTypeUtils.IMAGE_JPEG,
                new URL("data:image/jpeg;base64," + msg.getBase64Image()));
            return new UserMessage(msg.getContent(), media);
        }
        return new UserMessage(msg.getContent());
    }

    private AssistantMessage toAssistantMessage(Message msg) {
        if (msg.getToolCalls() == null || msg.getToolCalls().isEmpty()) {
            return new AssistantMessage(msg.getContent());
        }
        List<AssistantMessage.ToolCall> toolCalls = msg.getToolCalls().stream()
            .map(tc -> new AssistantMessage.ToolCall(
                tc.getId(), tc.getType(),
                tc.getFunction().getName(), tc.getFunction().getArguments()))
            .toList();
        return new AssistantMessage(msg.getContent(), Map.of(), toolCalls);
    }

    private ToolResponseMessage toToolResponseMessage(Message msg) {
        String content = StringUtil.textDesensitization(msg.getContent(), ...);
        return new ToolResponseMessage(
            List.of(msg.getToolCallId()), List.of(content), List.of(""));
    }
}
```

**关键收益**：`formatMessages` 中 GPT/Claude 双分支（约 90 行）完全删除，多模态、工具调用、工具结果都由 Spring AI 统一处理。

### 2.3 `LLM`（精简门面）

**职责**：保留原有 API 契约（`ask`、`askTool` 的方法签名不变），内部从"手动 HTTP 调用"变为"编排 Spring AI API"。

**状态变化**：
- 删除：`apiKey`、`baseUrl`、`interfaceUrl`（由 Armory 管理）
- 删除：`instances` 缓存池（由 Spring 容器管理）
- 删除：`callOpenAI`、`callOpenAIStream`、`callOpenAIFunctionCallStream`、`callClaudeFunctionCallStream`（4 个 OkHttp 方法，约 800 行）
- 删除：`formatMessages`（提取到 `DomainMessageConverter`）
- 删除：`gptToClaudeTool`（Spring AI 内部处理）
- 保留：`model`、`maxTokens`、`temperature`、`functionCallType`、`extParams`（调用参数）
- 保留：`totalInputTokens`、`maxInputTokens`（状态）
- 保留：`truncateMessage`（消息截断）
- 保留：`struct_parse` 的提示词拼接 + JSON 代码块解析
- 保留：流式推送的 `Printer.send()` 调用

```java
@Slf4j
@Data
public class LLM {

    private final String model;
    private final String llmErp;
    private final int maxTokens;
    private final double temperature;
    private final String functionCallType;
    private final Map<String, Object> extParams;

    private int totalInputTokens;
    private Integer maxInputTokens;

    // 依赖（通过 SpringContextHolder 获取，保持 LLM 的实例化方式不变）
    private transient LlmChatClientResolver resolver;
    private transient DomainMessageConverter converter;
    private transient StreamResponseHandler streamHandler;

    public LLM(String modelName, String llmErp) {
        this.llmErp = llmErp;
        LLMSettings config = Config.getLLMConfig(modelName);
        this.model = config.getModel();
        this.maxTokens = config.getMaxTokens();
        this.temperature = config.getTemperature();
        this.functionCallType = config.getFunctionCallType();
        this.extParams = config.getExtParams();
        this.maxInputTokens = config.getMaxInputTokens();
        this.totalInputTokens = 0;

        this.resolver = SpringContextHolder.getBean(LlmChatClientResolver.class);
        this.converter = SpringContextHolder.getBean(DomainMessageConverter.class);
        this.streamHandler = SpringContextHolder.getBean(StreamResponseHandler.class);
    }
}
```

### 2.4 `StreamResponseHandler` — 流式响应处理

**职责**：处理 Spring AI `Flux<ChatResponse>` 流，保留原有的 interval 控制 + `Printer.send()` 推送逻辑。

```java
@Service
public class StreamResponseHandler {

    @Resource
    private ReactorConfig reactorConfig;

    public String handleStringStream(AgentContext context, Flux<ChatResponse> flux) {
        // 1. 读取 interval 配置
        // 2. 订阅 Flux，收集 content
        // 3. 按 interval 规则调用 context.getPrinter().send()
        // 4. 返回聚合后的完整字符串
    }

    public ToolCallResponse handleToolCallStream(
            AgentContext context, Flux<ChatResponse> flux, String functionCallType) {
        // 1. 订阅 Flux，区分 content delta 和 tool_calls delta
        // 2. 按 interval 推送 content
        // 3. 聚合 tool_calls
        // 4. 返回 ToolCallResponse
    }
}
```

---

## 第三章：数据流

### 3.1 `ask` 非流式

```
Agent
  │
  ▼
LLM.ask(context, messages, systemMsgs, false, temp)
  │
  ├── 1. resolver.resolveModel(model)
  │      从 Armory 获取 OpenAiChatModel (ai_client_model_{modelId})
  │
  ├── 2. converter.convert(messages)
  │      Message DTO → Spring AI Message
  │
  ├── 3. new Prompt(messages, options)
  │      封装消息 + OpenAiChatOptions
  │
  ├── 4. chatModel.call(prompt)
  │      Spring AI 同步调用
  │      - 消息序列化 (GPT/Claude 格式由 Spring AI 内部处理)
  │      - HTTP 请求发送
  │      - 响应解析
  │
  ├── 5. extractContent(response)
  │      提取 response.result.output.text
  │
  ▼
String
```

### 3.2 `ask` 流式

```
Agent
  │
  ▼
LLM.ask(context, messages, systemMsgs, true, temp)
  │
  ├── 1-3. 同非流式（获取 Model、转换消息、构建 Prompt）
  │
  ├── 4. chatModel.stream(prompt)
  │      返回 Flux<ChatResponse>
  │
  ▼
StreamResponseHandler.handleStringStream(context, flux)
  │
  ├── 5. 读取 ReactorConfig messageInterval
  │
  ├── 6. 订阅 Flux<ChatResponse>
  │      提取 delta.content，累加到 StringBuilder
  │
  ├── 7. 按 interval 推送
  │      context.getPrinter().send(...)
  │
  ├── 8. Flux 完成
  │      推送剩余内容，printer.send(..., true)
  │
  ▼
String
```

**关键变化**：原 `callOpenAIStream` 中约 150 行的 SSE 解析逻辑（逐行读取 `data: {...}`、JSON 解析、delta 提取）完全由 Spring AI 的 `stream()` 替代。`StreamResponseHandler` 只负责订阅 Flux 和按 interval 推送。

### 3.3 `askTool` function_call 非流式

```
Agent
  │
  ▼
LLM.askTool(..., "function_call", ..., false, ...)
  │
  ├── 1. resolver.resolveModel(model)
  │
  ├── 2. converter.convert(messages)
  │
  ├── 3. 工具转换：BaseTool/McpTool → ToolCallback
  │      (Spring AI 统一处理格式差异)
  │
  ├── 4. new Prompt(messages, options)
  │      options 包含 toolCallbacks 和 toolChoice
  │
  ├── 5. chatModel.call(prompt)
  │      Spring AI 自动处理工具定义序列化和响应解析
  │
  ├── 6. 解析响应
  │      response.getResult().getOutput()
  │      ├─ getText() → content
  │      └─ getToolCalls() → List<ToolCall>
  │
  ▼
ToolCallResponse
```

**关键收益**：原 `gptToClaudeTool`（约 50 行）和手动工具 Map 组装（约 40 行）全部删除。

### 3.4 `askTool` function_call 流式

```
Agent
  │
  ▼
LLM.askTool(..., "function_call", ..., true, ...)
  │
  ├── 1-3. 同非流式
  │
  ├── 4. chatModel.stream(prompt)
  │      返回 Flux<ChatResponse>
  │
  ▼
StreamResponseHandler.handleToolCallStream(context, flux, "function_call")
  │
  ├── 5. 订阅 Flux<ChatResponse>
  │      每个 ChatResponse 包含:
  │      ├─ delta.content → 文本增量
  │      └─ delta.toolCalls → 工具调用增量
  │
  ├── 6. 文本推送（同 ask 流式的 interval 逻辑）
  │
  ├── 7. 工具调用聚合
  │      原逻辑: 手动维护 Map<index, OpenAIToolCall>，逐个字段判断 null，拼接 arguments
  │      新逻辑: Spring AI 在 Flux 中已返回完整的 ToolCall 对象，只需收集
  │
  ├── 8. Flux 完成
  │      返回 ToolCallResponse (content + toolCalls + finishReason)
  │
  ▼
ToolCallResponse
```

**关键收益**：原 `callOpenAIFunctionCallStream` / `callClaudeFunctionCallStream` 中约 **500 行**的 SSE 流解析、delta 处理、tool call 分片聚合逻辑，替换为 Spring AI 的 `Flux<ChatResponse>` 订阅 + 简单收集。

### 3.5 `askTool` struct_parse（非流式 / 流式）

**保留原有逻辑**，仅将底层 HTTP 调用替换为 `chatModel.call()` / `chatModel.stream()`：
- 提示词拼接（工具描述拼接到 systemMsgs）
- JSON 代码块正则匹配解析
- 流式 interval 推送

---

## 第四章：文件变更与注意事项

### 4.1 文件变更清单

| 操作 | 文件 | 说明 |
|------|------|------|
| **大幅修改** | `LLM.java` | 从 1789 行精简到 ~300 行，删除所有 OkHttp 方法 |
| **新增** | `LlmChatClientResolver.java` | 从 Armory 解析 ChatModel |
| **新增** | `DomainMessageConverter.java` | Message DTO ↔ Spring AI Message 转换 |
| **新增** | `StreamResponseHandler.java` | 流式响应 interval 推送 |
| **新增** | `BaseToolCallbackAdapter.java` | BaseTool → Spring AI ToolCallback 适配 |
| **新增** | `McpToolCallbackAdapter.java` | McpToolInfo → Spring AI ToolCallback 适配 |
| **删除逻辑** | `LLM.java` 内 | `instances` 缓存、`callOpenAI`、`callOpenAIStream`、`callOpenAIFunctionCallStream`、`callClaudeFunctionCallStream`、`formatMessages`、`gptToClaudeTool` |
| **保留逻辑** | `LLM.java` 内 | `truncateMessage`、`struct_parse` 提示词拼接与 JSON 解析、流式 interval 推送 |

### 4.2 注意事项

1. **ToolCallback 适配**：需要将 `BaseTool` 和 `McpToolInfo` 适配为 Spring AI 的 `ToolCallback`。`ToolCallback` 需要实现 `call(String functionInput)` 方法，内部调用原工具的 `run()` 逻辑。

2. **模型映射**：`resolveModel("qwen-vl-max")` 需在 Armory 已注册的 `ai_client_model_*` Bean 中查找 modelName 匹配。若无对应配置，需兜底处理。

3. **extParams 透传**：原 `extParams`（如 top_p、frequency_penalty）需映射到 `OpenAiChatOptions` 的对应字段。

4. **TokenCounter**：`truncateMessage` 仍依赖 `TokenCounter`，Spring AI 的 `Message` 类型同样可被 TokenCounter 计算。

5. **测试覆盖**：重构后需覆盖：
   - ask 非流式 / 流式
   - askTool function_call 非流式 / 流式
   - askTool struct_parse 非流式 / 流式
   - GPT / Claude 模型兼容性
   - 多模态消息

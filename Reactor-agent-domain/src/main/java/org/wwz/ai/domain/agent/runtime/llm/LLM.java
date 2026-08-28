package org.wwz.ai.domain.agent.runtime.llm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.util.CollectionUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolChoice;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeToolPolicy;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.runtime.util.ToolSchemaNormalizer;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.runtime.ReactorLlmDependencies;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 领域门面。
 * <p>
 * 统一处理消息转换、模型调用、工具调用、流式增量和 LLM invocation 账本。
 * 主路径全部走 Spring AI；struct_parse 仅作为无原生 tools[] 时的兼容协议。
 */
@Slf4j
@Data
public class LLM {

    private static final String STRUCT_PARSE = "struct_parse";
    private static final String FUNCTION = "function";
    private static final String STRUCT_PARSE_JSON_MARKER = "```json";
    private static final Pattern STRUCT_PARSE_JSON_PATTERN = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");

    /** 模型标识。 */
    private final String model;
    /** 用户请求使用的模型引用；与解析后的上游模型名区分。 */
    private final String modelReference;
    /** LLM ERP 标识。 */
    private final String llmErp;
    /** 最大输出 token。 */
    private final int maxTokens;
    /** 默认温度。 */
    private final double temperature;
    /** API Key。 */
    private final String apiKey;
    /** Base URL。 */
    private final String baseUrl;
    /** 接口路径。 */
    private final String interfaceUrl;
    /** 工具调用模式。 */
    private final String functionCallType;
    /** JSON 工具。 */
    private final ObjectMapper objectMapper;
    /** 原始模型配置，供 Spring AI 解析器复用。 */
    private final LLMSettings llmSettings;

    /** 显式注入的运行时依赖。 */
    private final transient ReactorRuntimeDependencies runtimeDependencies;
    private final transient LlmChatModelResolver chatModelResolver;
    private final transient OpenAiChatOptionsFactory chatOptionsFactory;
    private final transient DomainMessageConverter messageConverter;
    private final transient LlmChatResponseMapper responseMapper;
    private final transient StreamResponseHandler streamResponseHandler;
    /** false 表示本实例已是备援路径，禁止再嵌套 fallback。 */
    private final boolean allowModelFallback;

    public LLM(String modelName, String llmErp, ReactorRuntimeDependencies runtimeDependencies) {
        this(modelName, llmErp, runtimeDependencies, true);
    }

    private LLM(String modelName,
                String llmErp,
                ReactorRuntimeDependencies runtimeDependencies,
                boolean allowModelFallback) {
        this.llmErp = llmErp;
        this.allowModelFallback = allowModelFallback;
        this.modelReference = modelName;
        this.runtimeDependencies = requireRuntimeDependencies(runtimeDependencies);
        ReactorLlmDependencies llmDependencies = this.runtimeDependencies.requireLlmDependencies();
        this.chatModelResolver = llmDependencies.getChatModelResolver();
        this.chatOptionsFactory = llmDependencies.getChatOptionsFactory();
        this.messageConverter = llmDependencies.getMessageConverter();
        this.responseMapper = llmDependencies.getResponseMapper();
        this.streamResponseHandler = llmDependencies.getStreamResponseHandler();

        LLMSettings config = this.runtimeDependencies.resolveLlmSettings(modelName);
        this.llmSettings = config;
        this.model = config.getModel();
        this.maxTokens = config.getMaxTokens();
        this.temperature = config.getTemperature();
        this.apiKey = config.getApiKey();

        String baseUrlFromConfig = config.getBaseUrl();
        if (StringUtils.isBlank(baseUrlFromConfig)) {
            throw new IllegalArgumentException(
                    "Base URL is not configured or empty. Please set llm.default.base_url in application.yml, or configure llm.settings for model: "
                            + modelName);
        }
        this.baseUrl = baseUrlFromConfig;
        this.interfaceUrl = StringUtils.isNotBlank(config.getInterfaceUrl())
                ? config.getInterfaceUrl()
                : "/v1/chat/completions";
        this.functionCallType = config.getFunctionCallType();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 纯文本问答统一走 Spring AI。
     */
    public CompletableFuture<String> ask(
            AgentContext context,
            List<Message> messages,
            List<Message> systemMsgs,
            boolean stream,
            Double temperature
    ) {
        return ask(
                context,
                messages,
                systemMsgs,
                stream,
                true,
                temperature,
                ExecutionLedgerConstants.CALL_KIND_ASK
        );
    }

    /**
     * 纯文本问答统一走 Spring AI，并允许显式控制是否向前端分发流式增量。
     */
    public CompletableFuture<String> ask(
            AgentContext context,
            List<Message> messages,
            List<Message> systemMsgs,
            boolean stream,
            boolean pushToClient,
            Double temperature
    ) {
        return ask(
                context,
                messages,
                systemMsgs,
                stream,
                pushToClient,
                temperature,
                ExecutionLedgerConstants.CALL_KIND_ASK
        );
    }

    /**
     * 纯文本问答统一走 Spring AI，并允许调用方覆盖账本语义。
     * 用于内部 ask 与面向用户的 ask 共享执行链，但在回放时做语义隔离。
     */
    public CompletableFuture<String> ask(
            AgentContext context,
            List<Message> messages,
            List<Message> systemMsgs,
            boolean stream,
            boolean pushToClient,
            Double temperature,
            String callKind
    ) {
        CompletableFuture<String> primary = askOnCurrentModel(
                context, messages, systemMsgs, stream, pushToClient, temperature, callKind);
        return withFallbackModel(context, "ask", primary, fallbackName -> new LLM(fallbackName, llmErp, runtimeDependencies, false)
                .askOnCurrentModel(context, messages, systemMsgs, stream, pushToClient, temperature, callKind));
    }

    private CompletableFuture<String> askOnCurrentModel(
            AgentContext context,
            List<Message> messages,
            List<Message> systemMsgs,
            boolean stream,
            boolean pushToClient,
            Double temperature,
            String callKind
    ) {
        try {
            // 纯文本请求的顺序必须保持为：记录实际 prompt 快照 → 创建 LLM 账本事实 → 调用模型 → 完成账本事实。
            // 这样观测数据、账本状态和真正发送给模型的内容才能一一对应。
            // 先记录完整 prompt 观测，再创建 invocation，保证请求快照与实际发送内容一致。
            LlmPromptObservability.logRequest(
                    context,
                    model,
                    callKind,
                    LlmPromptRequestSnapshotSupport.collapseSystemMessages(systemMsgs),
                    messages,
                    null
            );
            LlmInvocationHandle invocationHandle = startLlmInvocation(
                    context,
                    callKind,
                    stream
            );
            Prompt prompt = buildPrompt(
                    mergeMessages(systemMsgs, messages),
                    chatOptionsFactory.buildTextOptions(llmSettings, temperature)
            );
            OpenAiChatModel chatModel = resolveChatModel();

            log.info("{} call llm ask via Spring AI, model={}, stream={}",
                    context.getRequestId(), model, stream);

            String retryLabel = "llm-ask:" + model;
            if (!stream) {
                // 非流式调用在受控 LLM 执行器中完成，避免阻塞请求线程或公共 ForkJoinPool。
                return AgentExecutorSupport.supplyAsync(runtimeDependencies.requireLlmExecutor(), "llmAsk", context, () -> {
                    try {
                        ChatResponse response = LlmRequestRetry.call(
                                retryLabel, () -> chatModel.call(prompt), retryNotifier(context));
                        ReasoningContentExtractor.SplitResult split =
                                ReasoningContentExtractor.splitFromChatResponse(response);
                        String content = split.hasContent()
                                ? split.content()
                                : responseMapper.toText(response);
                        LlmUsageSnapshot usage = LlmUsageSnapshot.resolve(response.getMetadata());
                        finishLlmInvocation(
                                context,
                                invocationHandle,
                                ExecutionLedgerConstants.STATUS_SUCCESS,
                                content,
                                split.reasoningContent(),
                                0,
                                usage,
                                resolveFinishReason(response),
                                null
                        );
                        return content;
                    } catch (Exception e) {
                        finishLlmInvocation(
                                context,
                                invocationHandle,
                                ExecutionLedgerConstants.resolveFailureStatus(e),
                                null,
                                0,
                                null,
                                null,
                                e.getMessage()
                        );
                        throw new CompletionException(e);
                    }
                });
            }

            // 流式调用的完成与失败都由 whenComplete 收口，避免网络异常时留下 RUNNING 的孤立 invocation。
            return streamResponseHandler.handleStringStreamWithUsage(
                    context,
                    LlmRequestRetry.stream(retryLabel, () -> chatModel.stream(prompt), retryNotifier(context)),
                    null,
                    false,
                    pushToClient
            )
                    .whenComplete((result, throwable) -> {
                        if (throwable == null) {
                            finishLlmInvocation(
                                    context,
                                    invocationHandle,
                                    ExecutionLedgerConstants.STATUS_SUCCESS,
                                    result == null ? null : result.getContent(),
                                    0,
                                    result == null ? null : result.getUsage(),
                                    null,
                                    null
                            );
                            return;
                        }
                        Throwable cause = unwrapCompletionThrowable(throwable);
                        finishLlmInvocation(
                                context,
                                invocationHandle,
                                ExecutionLedgerConstants.resolveFailureStatus(cause),
                                null,
                                0,
                                null,
                                null,
                                cause.getMessage()
                        );
                    })
                    .thenApply(result -> result == null ? null : result.getContent());
        } catch (Exception e) {
            log.error("{} Unexpected error in ask: {}", context.getRequestId(), e.getMessage(), e);
            return failedFuture(e);
        }
    }

    /**
     * 工具调用统一门面：function_call 走 Spring AI tools[]；struct_parse 走文本 JSON 协议。
     */
    public CompletableFuture<ToolCallResponse> askTool(
            AgentContext context,
            List<Message> messages,
            Message systemMsgs,
            ToolCollection tools,
            ToolChoice toolChoice,
            Double temperature,
            boolean stream,
            int timeout
    ) {
        return askTool(context, messages, systemMsgs, tools, toolChoice, temperature, stream, true, timeout);
    }

    /**
     * 工具调用统一门面，并允许调用方控制是否向前端透传流式增量。
     */
    public CompletableFuture<ToolCallResponse> askTool(
            AgentContext context,
            List<Message> messages,
            Message systemMsgs,
            ToolCollection tools,
            ToolChoice toolChoice,
            Double temperature,
            boolean stream,
            boolean pushToClient,
            int timeout
    ) {
        CompletableFuture<ToolCallResponse> primary = askToolOnCurrentModel(
                context, messages, systemMsgs, tools, toolChoice, temperature, stream, pushToClient, timeout);
        return withFallbackModel(context, "askTool", primary, fallbackName -> new LLM(fallbackName, llmErp, runtimeDependencies, false)
                .askToolOnCurrentModel(context, messages, systemMsgs, tools, toolChoice, temperature, stream, pushToClient, timeout));
    }

    private CompletableFuture<ToolCallResponse> askToolOnCurrentModel(
            AgentContext context,
            List<Message> messages,
            Message systemMsgs,
            ToolCollection tools,
            ToolChoice toolChoice,
            Double temperature,
            boolean stream,
            boolean pushToClient,
            int timeout
    ) {
        try {
            tools = PlanModeToolPolicy.filterTools(context, tools);
            // 工具调用有两条协议分支：原生 function_call 由 Spring AI 组装 tools[]，
            // struct_parse 则把 schema 放入 system 文本后自行解析 JSON；两者共用观测和账本入口。
            if (!ToolChoice.isValid(toolChoice)) {
                throw new IllegalArgumentException("Invalid tool_choice: " + toolChoice);
            }

            LlmAskToolProtocol protocol = isStructParseMode()
                    ? LlmAskToolProtocol.STRUCT_PARSE
                    : LlmAskToolProtocol.FUNCTION_CALL;
            Message effectiveSystem = systemMsgs;
            if (protocol == LlmAskToolProtocol.STRUCT_PARSE) {
                // struct_parse 没有原生 tools[]，先把工具 schema 编入 system，再沿用文本响应解析。
                effectiveSystem = buildStructParseSystemMessage(systemMsgs, tools);
            }
            // 与正式发送一致的 system+messages+tools 快照（先观测再落账本 start）
            LlmPromptObservability.logRequest(
                    context, model, ExecutionLedgerConstants.CALL_KIND_ASK_TOOL, effectiveSystem, messages, tools);
            LlmInvocationHandle invocationHandle = startLlmInvocation(
                    context,
                    ExecutionLedgerConstants.CALL_KIND_ASK_TOOL,
                    stream
            );
            long startTime = System.currentTimeMillis();
            if (protocol == LlmAskToolProtocol.STRUCT_PARSE) {
                return askToolWithStructParse(
                        context, messages, systemMsgs, tools, temperature, stream, timeout, startTime, invocationHandle);
            }
            // function_call 主路径：Spring AI 负责 tools[] 与 tool_choice。
            Prompt prompt = buildPrompt(
                    mergeMessages(systemMsgs, messages),
                    chatOptionsFactory.buildToolOptions(llmSettings, temperature, tools, toolChoice)
            );
            OpenAiChatModel chatModel = resolveChatModel();

            log.info("{} call llm askTool via Spring AI, model={}, stream={}, mode=function_call",
                    context.getRequestId(), model, stream);

            String retryLabel = "llm-askTool:" + model;
            if (!stream) {
                return AgentExecutorSupport.withTimeout(
                        AgentExecutorSupport.supplyAsync(
                                runtimeDependencies.requireLlmExecutor(),
                                "llmAskToolFunctionCall",
                                context,
                                () -> {
                                    try {
                                        ChatResponse response = LlmRequestRetry.call(
                                                retryLabel, () -> chatModel.call(prompt), retryNotifier(context));
                                        return responseMapper.toToolCallResponse(response, startTime);
                                    } catch (Exception e) {
                                        throw new CompletionException(e);
                                    }
                                }),
                        timeout,
                        TimeUnit.SECONDS
                ).whenComplete((response, throwable) -> {
                    if (throwable == null) {
                        finishLlmInvocation(context, invocationHandle, response, null);
                        return;
                    }
                    finishLlmInvocation(context, invocationHandle, null, unwrapCompletionThrowable(throwable));
                });
            }

            CompletableFuture<ToolCallResponse> streamFuture = streamResponseHandler.handleToolCallStream(
                    context,
                    LlmRequestRetry.stream(retryLabel, () -> chatModel.stream(prompt), retryNotifier(context)),
                    startTime,
                    pushToClient
            ).orTimeout(timeout, TimeUnit.SECONDS);

            // 空流通常是兼容网关的瞬态响应：只对“明确为空流”的情况重试一次，避免普通错误被掩盖。
            return streamFuture.handle((response, throwable) -> {
                        if (throwable == null) {
                            return CompletableFuture.completedFuture(response);
                        }
                        Throwable root = unwrapCompletionThrowable(throwable);
                        if (!isEmptyStreamingResponse(root)) {
                            return CompletableFuture.<ToolCallResponse>failedFuture(root);
                        }
                        log.warn("{} empty streaming askTool, retry once non-stream, model={}",
                                context.getRequestId(), model);
                        return AgentExecutorSupport.withTimeout(
                                AgentExecutorSupport.supplyAsync(
                                        runtimeDependencies.requireLlmExecutor(),
                                        "llmAskToolFunctionCallEmptyStreamRetry",
                                        context,
                                        () -> {
                                            try {
                                                ChatResponse callResponse = LlmRequestRetry.call(
                                                        retryLabel + ":empty-stream-retry",
                                                        () -> chatModel.call(prompt),
                                                        retryNotifier(context));
                                                return responseMapper.toToolCallResponse(callResponse, startTime);
                                            } catch (Exception e) {
                                                throw new CompletionException(e);
                                            }
                                        }),
                                timeout,
                                TimeUnit.SECONDS);
                    })
                    .thenCompose(f -> f)
                    .whenComplete((response, throwable) -> {
                        if (throwable == null) {
                            finishLlmInvocation(context, invocationHandle, response, null);
                            return;
                        }
                        finishLlmInvocation(context, invocationHandle, null, unwrapCompletionThrowable(throwable));
                    });
        } catch (Exception e) {
            log.error("{} Unexpected error in askTool: {}", context.getRequestId(), e.getMessage(), e);
            return failedFuture(e);
        }
    }

    /**
     * struct_parse 兼容路径。
     * 仍然让模型输出文本中的 JSON 代码块，但底层同样走 Spring AI 的文本 call/stream。
     */
    private CompletableFuture<ToolCallResponse> askToolWithStructParse(
            AgentContext context,
            List<Message> messages,
            Message systemMsg,
            ToolCollection tools,
            Double temperature,
            boolean stream,
            int timeout,
            long startTime,
            LlmInvocationHandle invocationHandle
    ) {
        // struct_parse 不支持原生 tools[]，因此 system 中的工具 schema 和模型返回的 JSON 共同构成协议。
        Message mergedSystemMessage = buildStructParseSystemMessage(systemMsg, tools);
        Prompt prompt = buildPrompt(
                mergeMessages(mergedSystemMessage, messages),
                chatOptionsFactory.buildTextOptions(llmSettings, temperature)
        );
        OpenAiChatModel chatModel = resolveChatModel();

        log.info("{} call llm askTool via Spring AI, model={}, stream={}, mode=struct_parse",
                context.getRequestId(), model, stream);

        String retryLabel = "llm-askTool-struct:" + model;
        if (!stream) {
            // 非流式路径可以一次性解析完整 JSON；流式路径则由响应处理器隐藏代码块标记后再解析。
            return AgentExecutorSupport.withTimeout(
                    AgentExecutorSupport.supplyAsync(
                            runtimeDependencies.requireLlmExecutor(),
                            "llmAskToolStructParse",
                            context,
                            () -> {
                                try {
                                    ChatResponse response = LlmRequestRetry.call(
                                            retryLabel, () -> chatModel.call(prompt), retryNotifier(context));
                                    LlmUsageSnapshot usage = LlmUsageSnapshot.resolve(response.getMetadata());
                                    ToolCallResponse toolCallResponse = buildStructParseToolCallResponse(
                                            context,
                                            responseMapper.toText(response),
                                            resolveFinishReason(response),
                                            usage.getTotalTokens(),
                                            startTime
                                    );
                                    responseMapper.applyUsage(toolCallResponse, usage);
                                    finishLlmInvocation(context, invocationHandle, toolCallResponse, null);
                                    return toolCallResponse;
                                } catch (Exception e) {
                                    finishLlmInvocation(context, invocationHandle, null, e);
                                    throw new CompletionException(e);
                                }
                            }),
                    timeout,
                    TimeUnit.SECONDS);
        }

        return streamResponseHandler.handleStringStreamWithUsage(
                        context,
                        LlmRequestRetry.stream(retryLabel, () -> chatModel.stream(prompt), retryNotifier(context)),
                        STRUCT_PARSE_JSON_MARKER,
                        true,
                        true
                )
                .thenApply(result -> {
                    ToolCallResponse toolCallResponse = buildStructParseToolCallResponse(
                            context,
                            result == null ? null : result.getContent(),
                            null,
                            result == null || result.getUsage() == null ? null : result.getUsage().getTotalTokens(),
                            startTime
                    );
                    return responseMapper.applyUsage(toolCallResponse,
                            result == null ? LlmUsageSnapshot.empty() : result.getUsage());
                })
                .whenComplete((response, throwable) -> {
                    if (throwable == null) {
                        finishLlmInvocation(context, invocationHandle, response, null);
                        return;
                    }
                    finishLlmInvocation(context, invocationHandle, null, unwrapCompletionThrowable(throwable));
                })
                .orTimeout(timeout, TimeUnit.SECONDS);
    }

    /**
     * 将 struct_parse 的文本响应映射回既有 ToolCallResponse。
     */
    private ToolCallResponse buildStructParseToolCallResponse(
            AgentContext context,
            String fullContent,
            String finishReason,
            Integer totalTokens,
            long startTime
    ) {
        List<ToolCall> toolCalls = new ArrayList<>();
        for (String match : findMatches(fullContent, STRUCT_PARSE_JSON_PATTERN)) {
            ToolCall toolCall = parseToolCall(context, match);
            if (toolCall != null) {
                toolCalls.add(toolCall);
            }
        }

        String visibleContent = extractVisibleContent(fullContent);
        visibleContent = StringUtils.trimToNull(visibleContent);
        if (visibleContent == null && toolCalls.isEmpty()) {
            throw new IllegalArgumentException("Empty or invalid response from LLM");
        }

        return ToolCallResponse.builder()
                .content(visibleContent)
                .toolCalls(toolCalls)
                .finishReason(finishReason)
                .totalTokens(totalTokens)
                .duration(System.currentTimeMillis() - startTime)
                .build();
    }

    private String extractVisibleContent(String fullContent) {
        if (fullContent == null) {
            return null;
        }
        int stopPos = fullContent.indexOf(STRUCT_PARSE_JSON_MARKER);
        return stopPos >= 0 ? fullContent.substring(0, stopPos) : fullContent;
    }

    private Message buildStructParseSystemMessage(Message systemMsg, ToolCollection tools) {
        String toolPrompt = buildStructParseToolPrompt(tools);
        String originalSystemPrompt = systemMsg != null ? StringUtils.defaultString(systemMsg.getContent()) : "";
        String mergedContent = StringUtils.isBlank(originalSystemPrompt)
                ? toolPrompt
                : originalSystemPrompt + "\n" + toolPrompt;
        return Message.systemMessage(mergedContent, null);
    }

    /**
     * struct_parse 模式仍复用原来的工具描述文本，但不再关心 GPT/Claude 的手工分支。
     */
    private String buildStructParseToolPrompt(ToolCollection tools) {
        ReactorConfig reactorConfig = runtimeDependencies.requireReactorConfig();
        StringBuilder prompt = new StringBuilder(StringUtils.defaultString(reactorConfig.getStructParseToolSystemPrompt()));
        if (prompt.length() > 0) {
            prompt.append('\n');
        }

        if (tools == null) {
            return prompt.toString();
        }

        for (BaseTool tool : tools.getToolMap().values()) {
            Map<String, Object> functionMap = new LinkedHashMap<>();
            functionMap.put("name", tool.getName());
            functionMap.put("description", tool.getDescription());
            functionMap.put("parameters",
                    addFunctionNameParam(normalizeToolParameters(tool.toParams(), tool.getName()), tool.getName()));
            prompt.append(String.format("- `%s`%n```json %s ```%n", tool.getName(), JSON.toJSONString(functionMap)));
        }

        for (McpToolInfo tool : tools.getMcpToolMap().values()) {
            Map<String, Object> functionMap = new LinkedHashMap<>();
            functionMap.put("name", tool.getName());
            functionMap.put("description", tool.getDesc());
            functionMap.put("parameters",
                    addFunctionNameParam(parseAndNormalizeToolParameters(tool.getParameters(), tool.getName()), tool.getName()));
            prompt.append(String.format("- `%s`%n```json %s ```%n", tool.getName(), JSON.toJSONString(functionMap)));
        }
        return prompt.toString();
    }

    private Prompt buildPrompt(List<Message> domainMessages, OpenAiChatOptions options) {
        return new Prompt(messageConverter.convert(domainMessages), options);
    }

    private List<Message> mergeMessages(List<Message> systemMsgs, List<Message> messages) {
        List<Message> mergedMessages = new ArrayList<>();
        if (systemMsgs != null) {
            for (Message systemMsg : systemMsgs) {
                if (systemMsg != null) {
                    mergedMessages.add(systemMsg);
                }
            }
        }
        if (messages != null) {
            for (Message message : messages) {
                if (message != null) {
                    mergedMessages.add(message);
                }
            }
        }
        return mergedMessages;
    }

    private List<Message> mergeMessages(Message systemMsg, List<Message> messages) {
        List<Message> mergedMessages = new ArrayList<>();
        if (systemMsg != null) {
            mergedMessages.add(systemMsg);
        }
        if (messages != null) {
            for (Message message : messages) {
                if (message != null) {
                    mergedMessages.add(message);
                }
            }
        }
        return mergedMessages;
    }

    private OpenAiChatModel resolveChatModel() {
        return chatModelResolver.resolve(llmSettings);
    }

    private boolean isStructParseMode() {
        return STRUCT_PARSE.equals(functionCallType);
    }

    private List<String> resolveFallbackModelNames() {
        return LlmModelFallback.resolveFallbackModelNames(runtimeDependencies, modelReference);
    }

    private <T> CompletableFuture<T> withFallbackModel(AgentContext context,
                                                       String op,
                                                       CompletableFuture<T> primary,
                                                       Function<String, CompletableFuture<T>> fallbackCall) {
        if (!allowModelFallback || primary == null) {
            return primary;
        }
        return primary.handle((result, error) -> {
            if (error == null) {
                return CompletableFuture.completedFuture(result);
            }
            Throwable root = unwrapCompletionThrowable(error);
            if (!LlmModelFallback.isEligible(root)) {
                return CompletableFuture.<T>failedFuture(root);
            }
            List<String> fallbackNames = resolveFallbackModelNames();
            if (fallbackNames.isEmpty()) {
                return CompletableFuture.<T>failedFuture(root);
            }
            return LlmModelFallback.executeFallbackChain(
                    model,
                    root,
                    fallbackNames,
                    fallbackCall,
                    (fromModel, toModel, cause) -> {
                        String requestId = context == null ? "-" : context.getRequestId();
                        log.warn("{} {} model={} failed after retries ({}), switching to fallback model={}",
                                requestId, op, fromModel, cause.getMessage(), toModel);
                        notifyFallback(context, fromModel, toModel, cause);
                    }
            );
        }).thenCompose(f -> f);
    }

    private void notifyFallback(AgentContext context, String fromModel, String fallbackName, Throwable cause) {
        if (context == null || context.getPrinter() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("fromModel", fromModel);
            payload.put("toModel", fallbackName);
            payload.put("reason", cause == null ? null : cause.getMessage());
            context.getPrinter().send("llm_fallback", payload);
        } catch (Exception e) {
            log.debug("llm_fallback event skipped: {}", e.getMessage());
        }
    }



    private String resolveFinishReason(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getMetadata() == null) {
            return null;
        }
        return response.getResult().getMetadata().getFinishReason();
    }

    private LlmInvocationHandle startLlmInvocation(AgentContext context, String callKind, boolean stream) {
        if (context == null || !context.hasActiveLedgerRun() || context.getAgentRunState() == null) {
            return LlmInvocationHandle.disabled();
        }
        // invocation 的 prompt 估算和观测快照来自当前线程上下文，必须在异步切换前捕获。
        // 若当前请求没有有效 run，则保持 fail-open，不让可选的持久化能力阻断模型调用。
        LocalDateTime startedAt = LocalDateTime.now();
        int invocationSeq = context.getAgentRunState().nextInvocationSeq();
        LlmPromptObservability.ObservationBundle obs = LlmPromptObservability.current();
        TokenCounter.PromptEstimate est = obs == null ? null : obs.getEstimate();
        Long invocationId = context.getExecutionRecorder().createLlmInvocation(LlmInvocationStartRecord.builder()
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .invocationSeq(invocationSeq)
                .agentName(context.getAgentRunState().getCurrentAgentName())
                .stepNo(context.getAgentRunState().getCurrentStepNo())
                .callKind(callKind)
                .streaming(stream)
                .modelName(model)
                .startedAt(startedAt)
                .systemFingerprint(obs == null ? null : obs.getSystemFingerprint())
                .estTotalTokens(est == null ? null : est.getEstimatedTotalTokens())
                .estSystemTokens(est == null ? null : est.getSystemTokens())
                .estMessageTokens(est == null ? null : est.getMessageTokens())
                .estToolTokens(est == null ? null : est.getToolTokens())
                .messageCount(est == null ? null : est.getMessageCount())
                .toolCount(est == null ? null : est.getToolCount())
                .cacheStatus(obs == null ? null : obs.getCacheStatus())
                .cacheRiskFlags(obs == null ? null : obs.getCacheRiskFlags())
                .build());
        context.getAgentRunState().bindCurrentLlmInvocationId(invocationId);
        return new LlmInvocationHandle(invocationId, obs);
    }

    private void finishLlmInvocation(AgentContext context,
                                     LlmInvocationHandle handle,
                                     ToolCallResponse response,
                                     Throwable throwable) {
        restoreObservationBundle(handle);
        if (throwable != null) {
            finishLlmInvocation(
                    context,
                    handle,
                    ExecutionLedgerConstants.resolveFailureStatus(throwable),
                    null,
                    null,
                    0,
                    null,
                    null,
                    throwable.getMessage()
            );
            return;
        }
        long durationMs = response == null ? 0L : response.getDuration();
        LlmUsageSnapshot usage = response == null ? LlmUsageSnapshot.empty() : response.toUsageSnapshot();
        LlmPromptObservability.logResponse(
                context,
                model,
                ExecutionLedgerConstants.CALL_KIND_ASK_TOOL,
                usage,
                durationMs
        );
        finishLlmInvocation(
                context,
                handle,
                ExecutionLedgerConstants.STATUS_SUCCESS,
                response == null ? null : response.getContent(),
                response == null ? null : response.getReasoningContent(),
                response == null || response.getToolCalls() == null ? 0 : response.getToolCalls().size(),
                usage,
                response == null ? null : response.getFinishReason(),
                null
        );
    }

    private void finishLlmInvocation(AgentContext context,
                                     LlmInvocationHandle handle,
                                     Integer status,
                                     String responseText,
                                     Integer toolCallCount,
                                     LlmUsageSnapshot usage,
                                     String finishReason,
                                     String errorMsg) {
        finishLlmInvocation(context, handle, status, responseText, null, toolCallCount, usage, finishReason, errorMsg);
    }

    private void finishLlmInvocation(AgentContext context,
                                     LlmInvocationHandle handle,
                                     Integer status,
                                     String responseText,
                                     String reasoningContent,
                                     Integer toolCallCount,
                                     LlmUsageSnapshot usage,
                                     String finishReason,
                                     String errorMsg) {
        if (context == null || handle == null || !handle.enabled() || handle.invocationId() == null) {
            return;
        }
        // 完成时重新取回 invocation 创建阶段的观测快照，合并最终 token/cache 信息后一次性落账本。
        // 清理当前线程观测是必要的，否则复用线程会把本次模型调用的数据带进下一请求。
        restoreObservationBundle(handle);
        LlmUsageSnapshot snapshot = usage == null ? LlmUsageSnapshot.empty() : usage;
        LlmPromptObservability.ObservationBundle obs = LlmPromptObservability.current();
        if (obs == null || obs.getUsage() == null || obs.getUsage().isEmpty()) {
            if (!snapshot.isEmpty()) {
                LlmPromptObservability.logResponse(context, model, "ask", snapshot, 0L);
                obs = LlmPromptObservability.current();
            }
        }
        context.getExecutionRecorder().finishLlmInvocation(LlmInvocationFinishRecord.builder()
                .llmInvocationId(handle.invocationId())
                .requestId(context.getRequestId())
                .status(status)
                .responseText(responseText)
                .reasoningContent(reasoningContent)
                .toolCallCount(toolCallCount)
                .promptTokens(snapshot.getPromptTokens())
                .completionTokens(snapshot.getCompletionTokens())
                .totalTokens(snapshot.getTotalTokens())
                .cachedPromptTokens(snapshot.getCachedPromptTokens())
                .promptTextTokens(snapshot.getPromptTextTokens())
                .promptAudioTokens(snapshot.getPromptAudioTokens())
                .promptImageTokens(snapshot.getPromptImageTokens())
                .completionTextTokens(snapshot.getCompletionTextTokens())
                .completionAudioTokens(snapshot.getCompletionAudioTokens())
                .reasoningTokens(snapshot.getReasoningTokens())
                .cacheStatus(obs == null ? null : obs.getCacheStatus())
                .cacheRiskFlags(obs == null ? null : obs.getCacheRiskFlags())
                .finishReason(finishReason)
                .errorMsg(errorMsg)
                .finishedAt(LocalDateTime.now())
                .build());
        LlmPromptObservability.clear();
    }

    private void restoreObservationBundle(LlmInvocationHandle handle) {
        if (handle == null || handle.observationBundle() == null) {
            return;
        }
        if (LlmPromptObservability.current() == null) {
            LlmPromptObservability.restore(handle.observationBundle());
        }
    }

    private Throwable unwrapCompletionThrowable(Throwable throwable) {
        if ((throwable instanceof CompletionException || throwable instanceof ExecutionException)
                && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private boolean isEmptyStreamingResponse(Throwable throwable) {
        if (!(throwable instanceof IllegalArgumentException)) {
            return false;
        }
        String message = throwable.getMessage();
        return message != null && message.startsWith("Empty response from streaming LLM");
    }

    private <T> CompletableFuture<T> failedFuture(Exception e) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(e);
        return future;
    }

    private Map<String, Object> addFunctionNameParam(Map<String, Object> parameters, String toolName) {
        Map<String, Object> newParameters = new LinkedHashMap<>(parameters == null ? Map.of() : parameters);
        ArrayList<String> newRequired = new ArrayList<>();
        newRequired.add("function_name");
        if (parameters != null && parameters.containsKey("required") && parameters.get("required") != null) {
            newRequired.addAll((List<String>) parameters.get("required"));
        }
        newParameters.put("required", newRequired);

        Map<String, Object> newProperties = new LinkedHashMap<>();
        Map<String, Object> functionNameMap = new HashMap<>();
        functionNameMap.put("description", "默认值为工具名: " + toolName);
        functionNameMap.put("type", "string");
        newProperties.put("function_name", functionNameMap);
        if (parameters != null && parameters.containsKey("properties") && parameters.get("properties") != null) {
            newProperties.putAll((Map<String, Object>) parameters.get("properties"));
        }
        newParameters.put("properties", newProperties);
        return newParameters;
    }

    private Map<String, Object> normalizeToolParameters(Map<String, Object> rawParameters, String toolName) {
        return ToolSchemaNormalizer.normalizeSchema(rawParameters, toolName);
    }

    private Map<String, Object> parseAndNormalizeToolParameters(String rawParameters, String toolName) {
        return ToolSchemaNormalizer.normalizeSchemaAsMap(rawParameters, toolName);
    }

    /**
     * 将 LLM 瞬态重试进度推到前端状态条；isFinal=false 避免写入任务聚合/历史回放。
     */
    private static LlmRequestRetry.RetryListener retryNotifier(AgentContext context) {
        return (label, attempt, maxAttempts, error, delayMs) -> {
            if (context == null || context.getPrinter() == null) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("attempt", attempt);
            payload.put("maxAttempts", maxAttempts);
            payload.put("delayMs", delayMs);
            payload.put("label", label == null ? "" : label);
            if (error != null && StringUtils.isNotBlank(error.getMessage())) {
                payload.put("error", error.getMessage());
            }
            payload.put("message", String.format(
                    "模型请求失败，正在重试（第 %d/%d 次）…", attempt, maxAttempts));
            context.getPrinter().send(null, "llm_retry", payload, false);
        };
    }

    private List<String> findMatches(String text, Pattern pattern) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        Matcher matcher = pattern.matcher(text);
        List<String> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        return matches;
    }

    private ToolCall parseToolCall(AgentContext context, String jsonContent) {
        try {
            JSONObject jsonObj = JSON.parseObject(jsonContent);
            String toolName = jsonObj.getString("function_name");
            jsonObj.remove("function_name");
            return ToolCall.builder()
                    .id(StringUtil.getUUID())
                    .type(FUNCTION)
                    .function(ToolCall.Function.builder()
                            .name(toolName)
                            .arguments(JSON.toJSONString(jsonObj))
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("{} parse tool call error {}", context.getRequestId(), jsonContent, e);
            return null;
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ToolCallResponse {
        private String content;
        /** 模型原生 CoT，与 content 独立（DeepSeek / Qwen reasoning_content）。 */
        private String reasoningContent;
        private List<ToolCall> toolCalls;
        private String streamMessageId;
        private String finishReason;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private Integer cachedPromptTokens;
        private Integer promptTextTokens;
        private Integer promptAudioTokens;
        private Integer promptImageTokens;
        private Integer completionTextTokens;
        private Integer completionAudioTokens;
        private Integer reasoningTokens;
        private long duration;

        public LlmUsageSnapshot toUsageSnapshot() {
            return LlmUsageSnapshot.builder()
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .cachedPromptTokens(cachedPromptTokens)
                    .promptTextTokens(promptTextTokens)
                    .promptAudioTokens(promptAudioTokens)
                    .promptImageTokens(promptImageTokens)
                    .completionTextTokens(completionTextTokens)
                    .completionAudioTokens(completionAudioTokens)
                    .reasoningTokens(reasoningTokens)
                    .build();
        }
    }

    private record LlmInvocationHandle(Long invocationId, LlmPromptObservability.ObservationBundle observationBundle) {
        private static LlmInvocationHandle disabled() {
            return new LlmInvocationHandle(null, null);
        }

        private boolean enabled() {
            return invocationId != null;
        }
    }

    private ReactorRuntimeDependencies requireRuntimeDependencies(ReactorRuntimeDependencies dependencies) {
        if (dependencies == null) {
            throw new IllegalArgumentException("ReactorRuntimeDependencies must not be null");
        }
        return dependencies;
    }
}

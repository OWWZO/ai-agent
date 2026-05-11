package org.wwz.ai.domain.agent.runtime.llm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.util.CollectionUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolChoice;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM主体类。
 */
@Slf4j
@Data
public class LLM {

    private static final Map<String, LLM> instances = new ConcurrentHashMap<>();
    private static final String STRUCT_PARSE = "struct_parse";
    private static final String FUNCTION = "function";
    private static final String STRUCT_PARSE_JSON_MARKER = "```json";
    private static final Pattern STRUCT_PARSE_JSON_PATTERN = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");

    /** 模型标识。 */
    private final String model;
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
    /** token 计数器，保留给截断逻辑使用。 */
    private final TokenCounter tokenCounter;
    /** JSON 工具。 */
    private final ObjectMapper objectMapper;
    /** 扩展参数。 */
    private final Map<String, Object> extParams;
    /** 原始模型配置，供 Spring AI 解析器复用。 */
    private final LLMSettings llmSettings;

    /** 累计输入 token。 */
    private int totalInputTokens;
    /** 最大输入 token。 */
    private Integer maxInputTokens;

    /** 显式注入的运行时依赖。 */
    private final transient ReactorRuntimeDependencies runtimeDependencies;
    private final transient LlmChatModelResolver chatModelResolver;
    private final transient OpenAiChatOptionsFactory chatOptionsFactory;
    private final transient DomainMessageConverter messageConverter;
    private final transient LlmChatResponseMapper responseMapper;
    private final transient StreamResponseHandler streamResponseHandler;

    public LLM(String modelName, String llmErp, ReactorRuntimeDependencies runtimeDependencies) {
        this.llmErp = llmErp;
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
        this.totalInputTokens = 0;
        this.maxInputTokens = config.getMaxInputTokens();
        this.extParams = config.getExtParams();
        this.tokenCounter = new TokenCounter();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 兼容旧逻辑的消息格式转换，仅保留给 HTTP 回退链路与 token 截断使用。
     */
    public List<Map<String, Object>> formatMessages(List<Message> messages, boolean isClaude) {
        List<Map<String, Object>> formattedMessages = new ArrayList<>();
        if (messages == null || messages.isEmpty()) {
            return formattedMessages;
        }

        for (Message message : messages) {
            if (message == null || message.getRole() == null) {
                continue;
            }
            Map<String, Object> messageMap = new HashMap<>();
            if (StringUtils.isNotBlank(message.getBase64Image())) {
                List<Map<String, Object>> multimodalContent = new ArrayList<>();
                Map<String, String> imageUrlMap = new HashMap<>();
                imageUrlMap.put("url", "data:image/jpeg;base64," + message.getBase64Image());
                Map<String, Object> imageMap = new HashMap<>();
                imageMap.put("type", "image_url");
                imageMap.put("image_url", imageUrlMap);
                multimodalContent.add(imageMap);

                Map<String, Object> textMap = new HashMap<>();
                textMap.put("type", "text");
                textMap.put("text", message.getContent());
                multimodalContent.add(textMap);

                messageMap.put("role", message.getRole().getValue());
                messageMap.put("content", multimodalContent);
            } else if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                if (isClaude) {
                    messageMap.put("role", message.getRole().getValue());
                    List<Map<String, Object>> claudeToolCalls = new ArrayList<>();
                    for (ToolCall toolCall : message.getToolCalls()) {
                        if (toolCall == null || toolCall.getFunction() == null) {
                            continue;
                        }
                        Map<String, Object> claudeToolCall = new HashMap<>();
                        claudeToolCall.put("type", "tool_use");
                        claudeToolCall.put("id", toolCall.getId());
                        claudeToolCall.put("name", toolCall.getFunction().getName());
                        claudeToolCall.put("input", JSON.parseObject(toolCall.getFunction().getArguments()));
                        claudeToolCalls.add(claudeToolCall);
                    }
                    messageMap.put("content", claudeToolCalls);
                } else {
                    messageMap.put("role", message.getRole().getValue());
                    List<Map<String, Object>> toolCallsMap = JSON.parseObject(
                            JSON.toJSONString(message.getToolCalls()),
                            new TypeReference<List<Map<String, Object>>>() {
                            });
                    messageMap.put("tool_calls", toolCallsMap);
                }
            } else if (StringUtils.isNotBlank(message.getToolCallId())) {
                ReactorConfig reactorConfig = runtimeDependencies.requireReactorConfig();
                String content = StringUtil.textDesensitization(message.getContent(), reactorConfig.getSensitivePatterns());
                if (isClaude) {
                    messageMap.put("role", "user");
                    List<Map<String, Object>> claudeToolCalls = new ArrayList<>();
                    Map<String, Object> claudeToolCall = new HashMap<>();
                    claudeToolCall.put("type", "tool_result");
                    claudeToolCall.put("tool_use_id", message.getToolCallId());
                    claudeToolCall.put("content", content);
                    claudeToolCalls.add(claudeToolCall);
                    messageMap.put("content", claudeToolCalls);
                } else {
                    messageMap.put("role", message.getRole().getValue());
                    messageMap.put("content", content);
                    messageMap.put("tool_call_id", message.getToolCallId());
                }
            } else {
                messageMap.put("role", message.getRole().getValue());
                messageMap.put("content", message.getContent());
            }
            formattedMessages.add(messageMap);
        }
        return formattedMessages;
    }

    /**
     * 保留原截断逻辑，避免调用方行为变化。
     */
    public List<Map<String, Object>> truncateMessage(AgentContext context, List<Map<String, Object>> messages, int maxInputTokens) {
        if (messages == null || messages.isEmpty() || maxInputTokens < 0) {
            return messages;
        }

        log.info("{} before truncate {}", context.getRequestId(), JSON.toJSONString(messages));
        List<Map<String, Object>> truncatedMessages = new ArrayList<>();
        int remainingTokens = maxInputTokens;
        Map<String, Object> system = messages.get(0);

        if ("system".equals(system.getOrDefault("role", ""))) {
            remainingTokens -= tokenCounter.countMessageTokens(system);
        }

        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> message = messages.get(i);
            int messageToken = tokenCounter.countMessageTokens(message);
            if (remainingTokens >= messageToken) {
                truncatedMessages.add(0, message);
                remainingTokens -= messageToken;
            } else {
                break;
            }
        }

        Iterator<Map<String, Object>> iterator = truncatedMessages.iterator();
        while (iterator.hasNext()) {
            Map<String, Object> message = iterator.next();
            if (!"user".equals(message.getOrDefault("role", ""))) {
                iterator.remove();
            } else {
                break;
            }
        }

        if ("system".equals(system.getOrDefault("role", ""))) {
            truncatedMessages.add(0, system);
        }

        log.info("{} after truncate {}", context.getRequestId(), JSON.toJSONString(truncatedMessages));
        return truncatedMessages;
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
        try {
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

            if (!stream) {
                return AgentExecutorSupport.supplyAsync(runtimeDependencies.requireLlmExecutor(), "llmAsk", () -> {
                    try {
                        ChatResponse response = chatModel.call(prompt);
                        String content = responseMapper.toText(response);
                        finishLlmInvocation(
                                context,
                                invocationHandle,
                                ExecutionLedgerConstants.STATUS_SUCCESS,
                                content,
                                0,
                                resolvePromptTokens(response.getMetadata()),
                                resolveCompletionTokens(response.getMetadata()),
                                resolveTotalTokens(response.getMetadata()),
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
                                null,
                                null,
                                e.getMessage()
                        );
                        throw new CompletionException(e);
                    }
                });
            }

            return streamResponseHandler.handleStringStream(context, chatModel.stream(prompt), null, false, pushToClient)
                    .whenComplete((content, throwable) -> {
                        if (throwable == null) {
                            finishLlmInvocation(
                                    context,
                                    invocationHandle,
                                    ExecutionLedgerConstants.STATUS_SUCCESS,
                                    content,
                                    0,
                                    null,
                                    null,
                                    null,
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
                                null,
                                null,
                                cause.getMessage()
                        );
                    });
        } catch (Exception e) {
            log.error("{} Unexpected error in ask: {}", context.getRequestId(), e.getMessage(), e);
            return failedFuture(e);
        }
    }

    /**
     * 工具调用统一门面。
     * function_call 走 Spring AI 原生工具调用；struct_parse 保留兼容分支。
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
        try {
            if (!ToolChoice.isValid(toolChoice)) {
                throw new IllegalArgumentException("Invalid tool_choice: " + toolChoice);
            }

            LlmInvocationHandle invocationHandle = startLlmInvocation(
                    context,
                    ExecutionLedgerConstants.CALL_KIND_ASK_TOOL,
                    stream
            );
            long startTime = System.currentTimeMillis();
            if (isStructParseMode()) {
                return askToolWithStructParse(
                        context, messages, systemMsgs, tools, temperature, stream, timeout, startTime, invocationHandle);
            }

            Prompt prompt = buildPrompt(
                    mergeMessages(systemMsgs, messages),
                    chatOptionsFactory.buildToolOptions(llmSettings, temperature, tools, toolChoice)
            );
            OpenAiChatModel chatModel = resolveChatModel();

            log.info("{} call llm askTool via Spring AI, model={}, stream={}, mode=function_call",
                    context.getRequestId(), model, stream);

            if (!stream) {
                CompletableFuture<ToolCallResponse> springFuture = AgentExecutorSupport.supplyAsync(
                        runtimeDependencies.requireLlmExecutor(),
                        "llmAskToolFunctionCall",
                        () -> {
                    try {
                        ChatResponse response = chatModel.call(prompt);
                        return responseMapper.toToolCallResponse(response, startTime);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }).orTimeout(timeout, TimeUnit.SECONDS);

                return withFallback(
                        springFuture,
                        () -> legacyAskToolFunctionCallNonStream(context, messages, systemMsgs, tools, toolChoice, temperature, timeout, startTime),
                        context.getRequestId(),
                        "askTool(function_call, stream=false)"
                ).whenComplete((response, throwable) -> {
                    if (throwable == null) {
                        finishLlmInvocation(context, invocationHandle, response, null);
                        return;
                    }
                    finishLlmInvocation(context, invocationHandle, null, unwrapCompletionThrowable(throwable));
                });
            }

            return streamResponseHandler.handleToolCallStream(context, chatModel.stream(prompt), startTime, pushToClient)
                    .whenComplete((response, throwable) -> {
                        if (throwable == null) {
                            finishLlmInvocation(context, invocationHandle, response, null);
                            return;
                        }
                        finishLlmInvocation(context, invocationHandle, null, unwrapCompletionThrowable(throwable));
                    })
                    .orTimeout(timeout, TimeUnit.SECONDS);
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
        Message mergedSystemMessage = buildStructParseSystemMessage(systemMsg, tools);
        Prompt prompt = buildPrompt(
                mergeMessages(mergedSystemMessage, messages),
                chatOptionsFactory.buildTextOptions(llmSettings, temperature)
        );
        OpenAiChatModel chatModel = resolveChatModel();

        log.info("{} call llm askTool via Spring AI, model={}, stream={}, mode=struct_parse",
                context.getRequestId(), model, stream);

        if (!stream) {
            return AgentExecutorSupport.supplyAsync(runtimeDependencies.requireLlmExecutor(), "llmAskToolStructParse", () -> {
                try {
                    ChatResponse response = chatModel.call(prompt);
                    ToolCallResponse toolCallResponse = buildStructParseToolCallResponse(
                            context,
                            responseMapper.toText(response),
                            resolveFinishReason(response),
                            resolveTotalTokens(response.getMetadata()),
                            startTime
                    );
                    toolCallResponse.setPromptTokens(resolvePromptTokens(response.getMetadata()));
                    toolCallResponse.setCompletionTokens(resolveCompletionTokens(response.getMetadata()));
                    finishLlmInvocation(context, invocationHandle, toolCallResponse, null);
                    return toolCallResponse;
                } catch (Exception e) {
                    finishLlmInvocation(context, invocationHandle, null, e);
                    throw new CompletionException(e);
                }
            }).orTimeout(timeout, TimeUnit.SECONDS);
        }

        return streamResponseHandler.handleStringStream(
                        context,
                        chatModel.stream(prompt),
                        STRUCT_PARSE_JSON_MARKER,
                        true
                )
                .thenApply(content -> buildStructParseToolCallResponse(context, content, null, null, startTime))
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
     * function_call 非流式保留受控 HTTP 回退，便于灰度期间快速止损。
     */
    private CompletableFuture<ToolCallResponse> legacyAskToolFunctionCallNonStream(
            AgentContext context,
            List<Message> messages,
            Message systemMsg,
            ToolCollection tools,
            ToolChoice toolChoice,
            Double temperature,
            int timeout,
            long startTime
    ) {
        try {
            Map<String, Object> params = buildLegacyFunctionCallParams(messages, systemMsg, tools, toolChoice, temperature);
            log.warn("{} fallback askTool to legacy HTTP, model={}, toolCount={}",
                    context.getRequestId(), model, countTools(tools));

            return callOpenAI(params, timeout)
                    .thenApply(responseJson -> parseLegacyFunctionCallResponse(context, responseJson, startTime));
        } catch (Exception e) {
            return failedFuture(e);
        }
    }

    private ToolCallResponse parseLegacyFunctionCallResponse(AgentContext context, String responseJson, long startTime) {
        try {
            log.info("{} legacy llm response {}", context.getRequestId(), responseJson);
            JsonNode jsonResponse = objectMapper.readTree(responseJson);
            JsonNode choices = jsonResponse.get("choices");
            if (choices == null || choices.isEmpty() || choices.get(0).get("message") == null) {
                throw new IllegalArgumentException("Invalid or empty response from LLM");
            }

            JsonNode message = choices.get(0).get("message");
            String content = message.has("content") && !message.get("content").isNull()
                    && !"null".equals(message.get("content").asText())
                    ? message.get("content").asText()
                    : null;

            List<ToolCall> toolCalls = new ArrayList<>();
            if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
                for (JsonNode toolCall : message.get("tool_calls")) {
                    JsonNode functionNode = toolCall.get("function");
                    String name = functionNode != null && functionNode.has("name")
                            ? functionNode.get("name").asText()
                            : null;
                    if (StringUtils.isBlank(name)) {
                        log.warn("{} skip invalid tool call from legacy response: {}", context.getRequestId(), toolCall);
                        continue;
                    }
                    toolCalls.add(ToolCall.builder()
                            .id(toolCall.has("id") ? toolCall.get("id").asText() : StringUtil.getUUID())
                            .type(toolCall.has("type") ? toolCall.get("type").asText() : FUNCTION)
                            .function(ToolCall.Function.builder()
                                    .name(name)
                                    .arguments(normalizeToolArguments(extractToolArguments(functionNode)))
                                    .build())
                            .build());
                }
            }

            String finishReason = choices.get(0).has("finish_reason") && !choices.get(0).get("finish_reason").isNull()
                    ? choices.get(0).get("finish_reason").asText()
                    : null;
            Integer totalTokens = jsonResponse.has("usage") && jsonResponse.get("usage").has("total_tokens")
                    ? jsonResponse.get("usage").get("total_tokens").asInt()
                    : null;

            return ToolCallResponse.builder()
                    .content(content)
                    .toolCalls(toolCalls)
                    .finishReason(finishReason)
                    .totalTokens(totalTokens)
                    .duration(System.currentTimeMillis() - startTime)
                    .build();
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    /**
     * 兼容旧 HTTP 回退链路的参数组装。
     */
    private Map<String, Object> buildLegacyFunctionCallParams(
            List<Message> messages,
            Message systemMsg,
            ToolCollection tools,
            ToolChoice toolChoice,
            Double temperature
    ) {
        Map<String, Object> params = new HashMap<>();
        List<Map<String, Object>> formattedMessages = new ArrayList<>();

        if (systemMsg != null) {
            if (isClaudeModel()) {
                params.put("system", systemMsg.getContent());
            } else {
                formattedMessages.addAll(formatMessages(List.of(systemMsg), false));
            }
        }
        formattedMessages.addAll(formatMessages(messages, isClaudeModel()));

        List<Map<String, Object>> formattedTools = new ArrayList<>();
        if (tools != null) {
            for (BaseTool tool : tools.getToolMap().values()) {
                Map<String, Object> functionMap = new HashMap<>();
                functionMap.put("name", tool.getName());
                functionMap.put("description", tool.getDescription());
                functionMap.put("parameters", normalizeToolParameters(tool.toParams(), tool.getName()));

                Map<String, Object> toolMap = new HashMap<>();
                toolMap.put("type", FUNCTION);
                toolMap.put("function", functionMap);
                formattedTools.add(toolMap);
            }
            for (McpToolInfo tool : tools.getMcpToolMap().values()) {
                Map<String, Object> functionMap = new HashMap<>();
                functionMap.put("name", tool.getName());
                functionMap.put("description", tool.getDesc());
                functionMap.put("parameters", parseAndNormalizeToolParameters(tool.getParameters(), tool.getName()));

                Map<String, Object> toolMap = new HashMap<>();
                toolMap.put("type", FUNCTION);
                toolMap.put("function", functionMap);
                formattedTools.add(toolMap);
            }
        }

        if (isClaudeModel()) {
            formattedTools = gptToClaudeTool(formattedTools);
        }

        params.put("model", model);
        params.put("messages", formattedMessages);
        params.put("tools", formattedTools);
        params.put("tool_choice", toolChoice.getValue());
        params.put("max_tokens", maxTokens);
        params.put("temperature", temperature != null ? temperature : this.temperature);
        params.put("stream", false);
        if (extParams != null) {
            params.putAll(extParams);
        }

        log.info("legacy fallback request {}", JSONObject.toJSONString(params));
        return params;
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

    private boolean isClaudeModel() {
        return model != null && model.contains("claude");
    }

    private int countTools(ToolCollection tools) {
        if (tools == null) {
            return 0;
        }
        return tools.getToolMap().size() + tools.getMcpToolMap().size();
    }

    private String resolveFinishReason(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getMetadata() == null) {
            return null;
        }
        return response.getResult().getMetadata().getFinishReason();
    }

    private Integer resolveTotalTokens(ChatResponseMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        Usage usage = metadata.getUsage();
        return usage != null ? usage.getTotalTokens() : null;
    }

    private Integer resolvePromptTokens(ChatResponseMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        Usage usage = metadata.getUsage();
        return usage != null ? usage.getPromptTokens() : null;
    }

    private Integer resolveCompletionTokens(ChatResponseMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        Usage usage = metadata.getUsage();
        return usage != null ? usage.getCompletionTokens() : null;
    }

    private LlmInvocationHandle startLlmInvocation(AgentContext context, String callKind, boolean stream) {
        if (context == null || !context.hasActiveLedgerRun() || context.getAgentRunState() == null) {
            return LlmInvocationHandle.disabled();
        }
        LocalDateTime startedAt = LocalDateTime.now();
        int invocationSeq = context.getAgentRunState().nextInvocationSeq();
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
                .build());
        context.getAgentRunState().bindCurrentLlmInvocationId(invocationId);
        return new LlmInvocationHandle(invocationId);
    }

    private void finishLlmInvocation(AgentContext context,
                                     LlmInvocationHandle handle,
                                     ToolCallResponse response,
                                     Throwable throwable) {
        if (throwable != null) {
            finishLlmInvocation(
                    context,
                    handle,
                    ExecutionLedgerConstants.resolveFailureStatus(throwable),
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    throwable.getMessage()
            );
            return;
        }
        finishLlmInvocation(
                context,
                handle,
                ExecutionLedgerConstants.STATUS_SUCCESS,
                response == null ? null : response.getContent(),
                response == null || response.getToolCalls() == null ? 0 : response.getToolCalls().size(),
                response == null ? null : response.getPromptTokens(),
                response == null ? null : response.getCompletionTokens(),
                response == null ? null : response.getTotalTokens(),
                response == null ? null : response.getFinishReason(),
                null
        );
    }

    private void finishLlmInvocation(AgentContext context,
                                     LlmInvocationHandle handle,
                                     Integer status,
                                     String responseText,
                                     Integer toolCallCount,
                                     Integer promptTokens,
                                     Integer completionTokens,
                                     Integer totalTokens,
                                     String finishReason,
                                     String errorMsg) {
        if (context == null || handle == null || !handle.enabled() || handle.invocationId() == null) {
            return;
        }
        context.getExecutionRecorder().finishLlmInvocation(LlmInvocationFinishRecord.builder()
                .llmInvocationId(handle.invocationId())
                .requestId(context.getRequestId())
                .status(status)
                .responseText(responseText)
                .toolCallCount(toolCallCount)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .finishReason(finishReason)
                .errorMsg(errorMsg)
                .finishedAt(LocalDateTime.now())
                .build());
    }

    private <T> CompletableFuture<T> withFallback(CompletableFuture<T> primaryFuture,
                                                  Supplier<CompletableFuture<T>> fallbackSupplier,
                                                  String requestId,
                                                  String scene) {
        CompletableFuture<T> result = new CompletableFuture<>();
        primaryFuture.whenComplete((value, throwable) -> {
            if (throwable == null) {
                result.complete(value);
                return;
            }

            Throwable cause = unwrapCompletionThrowable(throwable);
            log.warn("{} {} failed on Spring AI path, fallback to legacy chain: {}",
                    requestId, scene, cause.getMessage(), cause);

            try {
                fallbackSupplier.get().whenComplete((fallbackValue, fallbackThrowable) -> {
                    if (fallbackThrowable == null) {
                        result.complete(fallbackValue);
                    } else {
                        result.completeExceptionally(unwrapCompletionThrowable(fallbackThrowable));
                    }
                });
            } catch (Exception fallbackError) {
                result.completeExceptionally(fallbackError);
            }
        });
        return result;
    }

    private Throwable unwrapCompletionThrowable(Throwable throwable) {
        if ((throwable instanceof CompletionException || throwable instanceof ExecutionException)
                && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private <T> CompletableFuture<T> failedFuture(Exception e) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(e);
        return future;
    }

    /**
     * 深拷贝，保留给 Claude 工具格式转换使用。
     */
    public <T> T deepCopy(T original) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(original);
            return objectMapper.readValue(
                    jsonBytes,
                    objectMapper.getTypeFactory().constructType(original.getClass())
            );
        } catch (Exception e) {
            throw new RuntimeException("深拷贝失败", e);
        }
    }

    /**
     * GPT 工具定义转换为 Claude 工具定义。
     * 仅保留给 legacy fallback 使用。
     */
    public List<Map<String, Object>> gptToClaudeTool(List<Map<String, Object>> gptTools) {
        List<Map<String, Object>> newGptTools = deepCopy(gptTools);
        List<Map<String, Object>> claudeTools = new ArrayList<>();
        for (Map<String, Object> gptToolWrapper : newGptTools) {
            Map<String, Object> gptTool = (Map<String, Object>) gptToolWrapper.get("function");
            Map<String, Object> claudeTool = new HashMap<>();
            claudeTool.put("name", gptTool.get("name"));
            claudeTool.put("description", gptTool.get("description"));

            Map<String, Object> parameters = (Map<String, Object>) gptTool.get("parameters");
            ArrayList<String> newRequired = new ArrayList<>();
            newRequired.add("function_name");
            if (parameters.containsKey("required") && parameters.get("required") != null) {
                newRequired.addAll((List<String>) parameters.get("required"));
            }
            parameters.put("required", newRequired);

            Map<String, Object> newProperties = new HashMap<>();
            Map<String, Object> functionNameMap = new HashMap<>();
            functionNameMap.put("description", "默认值为工具名: " + gptTool.get("name"));
            functionNameMap.put("type", "string");
            newProperties.put("function_name", functionNameMap);
            if (parameters.containsKey("properties") && parameters.get("properties") != null) {
                newProperties.putAll((Map<String, Object>) parameters.get("properties"));
            }
            parameters.put("properties", newProperties);
            claudeTool.put("input_schema", gptTool.get("parameters"));
            claudeTools.add(claudeTool);
        }
        return claudeTools;
    }

    private Map<String, Object> addFunctionNameParam(Map<String, Object> parameters, String toolName) {
        Map<String, Object> newParameters = deepCopy(parameters);
        ArrayList<String> newRequired = new ArrayList<>();
        newRequired.add("function_name");
        if (parameters.containsKey("required") && parameters.get("required") != null) {
            newRequired.addAll((List<String>) parameters.get("required"));
        }
        newParameters.put("required", newRequired);

        Map<String, Object> newProperties = new HashMap<>();
        Map<String, Object> functionNameMap = new HashMap<>();
        functionNameMap.put("description", "默认值为工具名: " + toolName);
        functionNameMap.put("type", "string");
        newProperties.put("function_name", functionNameMap);
        if (parameters.containsKey("properties") && parameters.get("properties") != null) {
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

    private String extractToolArguments(JsonNode functionNode) {
        if (functionNode == null || !functionNode.has("arguments") || functionNode.get("arguments").isNull()) {
            return "{}";
        }
        return getToolArgumentsChunk(functionNode.get("arguments"));
    }

    private String getToolArgumentsChunk(JsonNode argumentsNode) {
        if (argumentsNode == null || argumentsNode.isNull()) {
            return "";
        }
        return argumentsNode.isTextual() ? argumentsNode.asText() : argumentsNode.toString();
    }

    private String normalizeToolArguments(String arguments) {
        if (StringUtils.isBlank(arguments)) {
            return "{}";
        }
        String normalized = arguments.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            try {
                normalized = objectMapper.readValue(normalized, String.class);
            } catch (Exception ignore) {
            }
        }
        if (StringUtils.isBlank(normalized)) {
            return "{}";
        }
        normalized = normalized.trim();
        if ((normalized.startsWith("{") && normalized.endsWith("}"))
                || (normalized.startsWith("[") && normalized.endsWith("]"))) {
            return normalized;
        }
        try {
            JsonNode parsed = objectMapper.readTree(normalized);
            return parsed.toString();
        } catch (Exception ignore) {
            return "{}";
        }
    }

    /**
     * 最小化保留的旧 HTTP 调用能力，仅用于受控回退。
     */
    protected CompletableFuture<String> callOpenAI(Map<String, Object> params) {
        return callOpenAI(params, 300);
    }

    /**
     * 最小化保留的旧 HTTP 调用能力，仅用于受控回退。
     */
    protected CompletableFuture<String> callOpenAI(Map<String, Object> params, int timeout) {
        return AgentExecutorSupport.supplyAsync(runtimeDependencies.requireLlmExecutor(), "legacyHttpLlm", () -> {
            try {
                return runtimeDependencies.requireRemoteHttpPort().execute(RemoteHttpRequest.builder()
                        .method("POST")
                        .url(baseUrl + interfaceUrl)
                        .headers(Map.of(
                                "Authorization", "Bearer " + apiKey,
                                "Content-Type", "application/json",
                                "Accept", "application/json"
                        ))
                        .body(objectMapper.writeValueAsString(params))
                        .connectTimeoutSeconds((long) timeout)
                        .readTimeoutSeconds((long) timeout)
                        .writeTimeoutSeconds((long) timeout)
                        .callTimeoutSeconds((long) timeout)
                        .build());
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
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
        private List<ToolCall> toolCalls;
        private String streamMessageId;
        private String finishReason;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private long duration;
    }

    private record LlmInvocationHandle(Long invocationId) {
        private static LlmInvocationHandle disabled() {
            return new LlmInvocationHandle(null);
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

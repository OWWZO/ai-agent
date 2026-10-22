package org.wwz.ai.domain.agent.runtime.llm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolChoice;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayTiming;
import org.wwz.ai.domain.agent.runtime.CuteLlmDependencies;
import org.wwz.ai.domain.agent.runtime.CuteRuntimeDependencies;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * 出站调用全部经由 LlmCompletionPort；struct_parse 仅作为无原生 tools[] 时的兼容协议。
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
    /** 原始模型配置，供 Spring AI 解析器复用。 */
    private final LLMSettings llmSettings;
    /** 出站请求装配：domain Message / 工具集合 → LlmRequest（无状态，可共享）。 */
    private static final LlmRequestAssembler REQUEST_ASSEMBLER = new LlmRequestAssembler();

    /** 显式注入的运行时依赖。 */
    private final transient CuteRuntimeDependencies runtimeDependencies;
    private final transient LlmCompletionPort completionPort;
    private final transient StreamResponseHandler streamResponseHandler;
    /** false 表示本实例已是备援路径，禁止再嵌套 fallback。 */
    private final boolean allowModelFallback;

    public LLM(String modelName, String llmErp, CuteRuntimeDependencies runtimeDependencies) {
        this(modelName, llmErp, runtimeDependencies, true);
    }

    private LLM(String modelName,
                String llmErp,
                CuteRuntimeDependencies runtimeDependencies,
                boolean allowModelFallback) {
        this.llmErp = llmErp;
        this.allowModelFallback = allowModelFallback;
        this.modelReference = modelName;
        this.runtimeDependencies = requireRuntimeDependencies(runtimeDependencies);
        CuteLlmDependencies llmDependencies = this.runtimeDependencies.requireLlmDependencies();
        this.completionPort = llmDependencies.getCompletionPort();
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
        CompletableFuture<String> composed = withFallbackModel(context, "ask", primary, fallbackName -> new LLM(fallbackName, llmErp, runtimeDependencies, false)
                .askOnCurrentModel(context, messages, systemMsgs, stream, pushToClient, temperature, callKind));
        return LlmRequestRetry.propagateCancellation(composed, primary);
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
                    LlmPromptShapeFactory.forText(
                            LlmPromptRequestSnapshotSupport.collapseSystemMessages(systemMsgs),
                            messages)
            );
            LlmInvocationLedger ledger = new LlmInvocationLedger(context, model);
            LlmInvocationLedger.Handle invocationHandle = ledger.start(callKind, stream);
            if (context != null && context.isRunCancelled()) {
                ledger.finish(invocationHandle, ExecutionLedgerConstants.STATUS_STOPPED,
                        null, 0, null, null, "user_stop");
                return failedFuture(new LlmCancelledException(context.getRunCancelReason(), null));
            }
            LlmRequest request = REQUEST_ASSEMBLER.build(
                    context, model, llmSettings,
                    LlmRequestAssembler.merge(systemMsgs, messages),
                    List.of(),
                    null,
                    temperature);

            log.info("{} call llm ask via completion port, model={}, stream={}",
                    context.getRequestId(), model, stream);

            String retryLabel = "llm-ask:" + model;
            if (!stream) {
                // 非流式调用在受控 LLM 执行器中完成，避免阻塞请求线程或公共 ForkJoinPool。
                // stop 会 cancel 这个 Future，从而打断阻塞中的 call。
                return abortOnRunCancel(context, AgentExecutorSupport.supplyAsync(runtimeDependencies.requireLlmExecutor(), "llmAsk", context, () -> {
                    try {
                        LlmResponse response = LlmRequestRetry.call(
                                retryLabel, () -> completionPort.complete(request), retryNotifier(context));
                        ReasoningContentExtractor.SplitResult split =
                                ReasoningContentExtractor.splitFromResponse(response);
                        String content = split.hasContent()
                                ? split.content()
                                : resolveResponseText(response);
                        LlmUsageSnapshot usage = LlmUsageSnapshot.resolve(response == null ? null : response.getUsage());
                        if (context != null && context.isRunCancelled()) {
                            ledger.write(invocationHandle, ExecutionLedgerConstants.STATUS_STOPPED,
                                    content, split.reasoningContent(), 0, usage,
                                    resolveFinishReason(response), "user_stop");
                            throw new LlmCancelledException(context.getRunCancelReason(), content);
                        }
                        ledger.write(invocationHandle, ExecutionLedgerConstants.STATUS_SUCCESS,
                                content, split.reasoningContent(), 0, usage,
                                resolveFinishReason(response), null);
                        return content;
                    } catch (LlmCancelledException e) {
                        throw new CompletionException(e);
                    } catch (Exception e) {
                        ledger.finish(invocationHandle, e);
                        throw new CompletionException(e);
                    }
                }));
            }

            // 流式调用的完成与失败都由 whenComplete 收口，避免网络异常时留下 RUNNING 的孤立 invocation。
            // callAsync：整次 handler 失败后丢弃半截累积并新开流，覆盖首 chunk 前与中途断流。
            CompletableFuture<StreamResponseHandler.StringStreamResult> streamFuture = LlmRequestRetry.callAsync(
                    retryLabel,
                             () -> streamResponseHandler.handleStringStreamWithUsage(
                                     context,
                             completionPort.stream(request),
                            null,
                            false,
                            pushToClient,
                            0,
                            invocationHandle.runtimeTiming()
                    ),
                    retryNotifier(context)
            );
            streamFuture.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    ledger.finish(invocationHandle,
                            ExecutionLedgerConstants.STATUS_SUCCESS,
                            result == null ? null : result.getContent(),
                            0,
                            result == null ? null : result.getUsage(),
                            null,
                            null);
                    return;
                }
                ledger.finish(invocationHandle, unwrapCompletionThrowable(throwable));
            });
            return LlmRequestRetry.mapCancellable(streamFuture, result -> result == null ? null : result.getContent());
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
        CompletableFuture<ToolCallResponse> composed = withFallbackModel(context, "askTool", primary, fallbackName -> new LLM(fallbackName, llmErp, runtimeDependencies, false)
                .askToolOnCurrentModel(context, messages, systemMsgs, tools, toolChoice, temperature, stream, pushToClient, timeout));
        return LlmRequestRetry.propagateCancellation(composed, primary);
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
            // 工具调用有两条协议分支：原生 function_call 由出站 Adapter 组装 tools[]，
            // struct_parse 则把 schema 放入 system 文本后自行解析 JSON；两者共用观测和账本入口。
            if (!ToolChoice.isValid(toolChoice)) {
                throw new IllegalArgumentException("Invalid tool_choice: " + toolChoice);
            }

            LlmAskToolProtocol protocol = resolveAskToolProtocol();
            PromptShape promptShape = LlmPromptShapeFactory.forAskTool(
                    systemMsgs, messages, tools, protocol);
            // 与正式发送一致的 system+messages+tools 快照（先观测再落账本 start）
            LlmPromptObservability.logRequest(
                    context, model, ExecutionLedgerConstants.CALL_KIND_ASK_TOOL, promptShape);
            LlmInvocationLedger ledger = new LlmInvocationLedger(context, model);
            LlmInvocationLedger.Handle invocationHandle = ledger.start(
                    ExecutionLedgerConstants.CALL_KIND_ASK_TOOL, stream);
            if (context != null && context.isRunCancelled()) {
                ledger.finish(invocationHandle, ExecutionLedgerConstants.STATUS_STOPPED,
                        null, 0, null, null, "user_stop");
                return failedFuture(new LlmCancelledException(context.getRunCancelReason(), null));
            }
            long startTime = System.currentTimeMillis();
            if (protocol == LlmAskToolProtocol.STRUCT_PARSE) {
                return askToolWithStructParse(
                        context, messages, systemMsgs, tools, temperature, stream, timeout,
                        startTime, ledger, invocationHandle);
            }
            // function_call 主路径：Adapter 负责供应商 tools[] 与 tool_choice 映射。
            LlmRequest request = REQUEST_ASSEMBLER.build(
                    context, model, llmSettings,
                    LlmRequestAssembler.merge(systemMsgs, messages),
                    REQUEST_ASSEMBLER.buildToolDefinitions(tools),
                    toolChoice.getValue(),
                    temperature);

            log.info("{} call llm askTool via completion port, model={}, stream={}, mode=function_call, invocationId={}, thread={}",
                    context.getRequestId(), model, stream, invocationHandle.invocationId(), Thread.currentThread().getName());

            String retryLabel = "llm-askTool:" + model;
            if (!stream) {
                CompletableFuture<ToolCallResponse> call = AgentExecutorSupport.withTimeout(
                        AgentExecutorSupport.supplyAsync(
                                runtimeDependencies.requireLlmExecutor(),
                                "llmAskToolFunctionCall",
                                context,
                                () -> {
                                    try {
                                         LlmResponse response = LlmRequestRetry.call(
                                                 retryLabel, () -> completionPort.complete(request), retryNotifier(context));
                                         return toToolCallResponse(response, startTime);
                                    } catch (Exception e) {
                                        throw new CompletionException(e);
                                    }
                                }),
                        timeout,
                        TimeUnit.SECONDS
                );
                abortOnRunCancel(context, call);
                return call.whenComplete((response, throwable) -> {
                    if (throwable == null) {
                        ledger.finish(invocationHandle, response);
                        return;
                    }
                    ledger.finish(invocationHandle, unwrapCompletionThrowable(throwable));
                });
            }

            CompletableFuture<ToolCallResponse> streamFuture = LlmRequestRetry.callAsync(
                    retryLabel,
                    () -> streamResponseHandler.handleToolCallStream(
                             context,
                             completionPort.stream(request),
                            startTime,
                            pushToClient,
                            timeout,
                            invocationHandle.runtimeTiming()
                    ),
                    retryNotifier(context)
            );

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
                                                 LlmResponse callResponse = LlmRequestRetry.call(
                                                         retryLabel + ":empty-stream-retry",
                                                         () -> completionPort.complete(request),
                                                         retryNotifier(context));
                                                 return toToolCallResponse(callResponse, startTime);
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
                            ledger.finish(invocationHandle, response);
                            return;
                        }
                        ledger.finish(invocationHandle, unwrapCompletionThrowable(throwable));
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
            LlmInvocationLedger ledger,
            LlmInvocationLedger.Handle invocationHandle
    ) {
        // struct_parse 不支持原生 tools[]，因此 system 中的工具 schema 和模型返回的 JSON 共同构成协议。
        Message mergedSystemMessage = buildStructParseSystemMessage(systemMsg, tools);
        LlmRequest request = REQUEST_ASSEMBLER.build(
                context, model, llmSettings,
                LlmRequestAssembler.merge(mergedSystemMessage, messages),
                List.of(),
                null,
                temperature);

        log.info("{} call llm askTool via completion port, model={}, stream={}, mode=struct_parse, invocationId={}, thread={}",
                context.getRequestId(), model, stream, invocationHandle.invocationId(), Thread.currentThread().getName());

        String retryLabel = "llm-askTool-struct:" + model;
        if (!stream) {
            // 非流式路径可以一次性解析完整 JSON；流式路径则由响应处理器隐藏代码块标记后再解析。
            CompletableFuture<ToolCallResponse> call = AgentExecutorSupport.withTimeout(
                    AgentExecutorSupport.supplyAsync(
                            runtimeDependencies.requireLlmExecutor(),
                            "llmAskToolStructParse",
                            context,
                            () -> {
                                try {
                                     LlmResponse response = LlmRequestRetry.call(
                                             retryLabel, () -> completionPort.complete(request), retryNotifier(context));
                                     LlmUsageSnapshot usage = LlmUsageSnapshot.resolve(
                                             response == null ? null : response.getUsage());
                                     ToolCallResponse toolCallResponse = buildStructParseToolCallResponse(
                                             context,
                                             resolveResponseText(response),
                                            resolveFinishReason(response),
                                            usage.getTotalTokens(),
                                            startTime
                                    );
                                     applyUsage(toolCallResponse, usage);
                                    if (context != null && context.isRunCancelled()) {
                                        LlmCancelledException cancelled = new LlmCancelledException(
                                                context.getRunCancelReason(), toolCallResponse.getContent());
                                        ledger.finish(invocationHandle, cancelled);
                                        throw cancelled;
                                    }
                                    ledger.finish(invocationHandle, toolCallResponse);
                                    return toolCallResponse;
                                } catch (LlmCancelledException e) {
                                    throw new CompletionException(e);
                                } catch (Exception e) {
                                    ledger.finish(invocationHandle, e);
                                    throw new CompletionException(e);
                                }
                            }),
                    timeout,
                    TimeUnit.SECONDS);
            return abortOnRunCancel(context, call);
        }

        return LlmRequestRetry.callAsync(
                        retryLabel,
                        () -> streamResponseHandler.handleStringStreamWithUsage(
                                 context,
                                 completionPort.stream(request),
                                STRUCT_PARSE_JSON_MARKER,
                                true,
                                true,
                                timeout,
                                invocationHandle.runtimeTiming()
                        ),
                        retryNotifier(context)
                )
                .thenApply(result -> {
                    ToolCallResponse toolCallResponse = buildStructParseToolCallResponse(
                            context,
                            result == null ? null : result.getContent(),
                            null,
                            result == null || result.getUsage() == null ? null : result.getUsage().getTotalTokens(),
                            startTime
                    );
                     applyUsage(toolCallResponse,
                             result == null ? LlmUsageSnapshot.empty() : result.getUsage());
                     return toolCallResponse;
                })
                .whenComplete((response, throwable) -> {
                    if (throwable == null) {
                        ledger.finish(invocationHandle, response);
                        return;
                    }
                    ledger.finish(invocationHandle, unwrapCompletionThrowable(throwable));
                });
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

    public LlmAskToolProtocol resolveAskToolProtocol() {
        return isStructParseMode() ? LlmAskToolProtocol.STRUCT_PARSE : LlmAskToolProtocol.FUNCTION_CALL;
    }

    private Message buildStructParseSystemMessage(Message systemMsg, ToolCollection tools) {
        return LlmPromptShapeFactory.buildStructParseSystemMessage(systemMsg, tools);
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
        LlmExecutionPosition position = LlmExecutionPosition.capture(context);
        return LlmModelFallback.afterPrimary(
                position,
                context == null ? null : context.getAgentRunState(),
                model,
                primary,
                resolveFallbackModelNames(),
                fallbackCall,
                (fromModel, toModel, cause) -> {
                    String requestId = context == null ? "-" : context.getRequestId();
                    log.warn("{} {} model={} failed after retries ({}) thread={} agentName={} stepNo={}, switching to fallback model={}",
                            requestId,
                            op,
                            fromModel,
                            cause.getMessage(),
                            Thread.currentThread().getName(),
                            position.agentName(),
                            position.stepNo(),
                            toModel);
                    notifyFallback(context, fromModel, toModel, cause);
                }
        );
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



    private String resolveFinishReason(LlmResponse response) {
        return response == null ? null : response.getFinishReason();
    }

    private String resolveResponseText(LlmResponse response) {
        if (response == null || StringUtils.isBlank(response.getContent())
                || "null".equals(response.getContent())) {
            throw new IllegalArgumentException("Empty or invalid response from LLM");
        }
        return response.getContent();
    }

    private ToolCallResponse toToolCallResponse(LlmResponse response, long startTimeMs) {
        ReasoningContentExtractor.SplitResult split = ReasoningContentExtractor.splitFromResponse(response);
        List<ToolCall> toolCalls = new ArrayList<>();
        if (response != null && response.getToolCalls() != null) {
            for (LlmToolCall toolCall : response.getToolCalls()) {
                if (toolCall == null || StringUtils.isBlank(toolCall.getName())) {
                    continue;
                }
                toolCalls.add(ToolCall.builder()
                        .id(StringUtils.defaultIfBlank(toolCall.getId(), StringUtil.getUUID()))
                        .type(StringUtils.defaultIfBlank(toolCall.getType(), FUNCTION))
                        .function(ToolCall.Function.builder()
                                .name(toolCall.getName())
                                .arguments(StringUtils.defaultIfBlank(toolCall.getArguments(), "{}"))
                                .build())
                        .build());
            }
        }
        ToolCallResponse result = ToolCallResponse.builder()
                .content(split.content())
                .reasoningContent(split.reasoningContent())
                .toolCalls(toolCalls)
                .finishReason(resolveFinishReason(response))
                .duration(System.currentTimeMillis() - startTimeMs)
                .build();
        applyUsage(result, LlmUsageSnapshot.resolve(response == null ? null : response.getUsage()));
        return result;
    }

    private void applyUsage(ToolCallResponse response, LlmUsageSnapshot usage) {
        LlmUsageSnapshot.applyTo(response, usage);
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

    /**
     * 非流式阻塞调用没有 Reactor 订阅可 dispose，stop 时直接取消执行 Future 以打断等待。
     */
    private <T> CompletableFuture<T> abortOnRunCancel(AgentContext context, CompletableFuture<T> future) {
        if (context == null || future == null) {
            return future;
        }
        Runnable unregister = context.registerRunAbort(() -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        future.whenComplete((ignored, error) -> unregister.run());
        return future;
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
        private ReplayTiming timing;

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

    private CuteRuntimeDependencies requireRuntimeDependencies(CuteRuntimeDependencies dependencies) {
        if (dependencies == null) {
            throw new IllegalArgumentException("CuteRuntimeDependencies must not be null");
        }
        return dependencies;
    }
}

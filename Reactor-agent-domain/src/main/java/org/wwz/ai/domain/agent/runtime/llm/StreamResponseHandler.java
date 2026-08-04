package org.wwz.ai.domain.agent.runtime.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 基于 Flux<ChatResponse> 的统一流式响应处理器。
 */
@Slf4j
@Component
public class StreamResponseHandler {

    @Resource
    private ReactorConfig reactorConfig;
    @Resource
    private LlmChatResponseMapper chatResponseMapper;

    /**
     * 处理纯文本流式响应。
     */
    public CompletableFuture<String> handleStringStream(AgentContext context, Flux<ChatResponse> flux) {
        return handleStringStream(context, flux, null, false, true);
    }

    /**
     * 处理纯文本流式响应，并支持在遇到指定标记后停止向前端继续透传。
     */
    public CompletableFuture<String> handleStringStream(AgentContext context,
                                                        Flux<ChatResponse> flux,
                                                        String hiddenStartMarker,
                                                        boolean emitFinalSnapshot) {
        return handleStringStream(context, flux, hiddenStartMarker, emitFinalSnapshot, true);
    }

    /**
     * 处理纯文本流式响应，并显式控制是否向前端分发增量内容。
     */
    public CompletableFuture<String> handleStringStream(AgentContext context,
                                                        Flux<ChatResponse> flux,
                                                        String hiddenStartMarker,
                                                        boolean emitFinalSnapshot,
                                                        boolean pushToClient) {
        return handleStringStreamWithUsage(context, flux, hiddenStartMarker, emitFinalSnapshot, pushToClient)
                .thenApply(result -> result == null ? null : result.getContent());
    }

    /**
     * 处理纯文本流式响应，同时返回接口 usage。
     */
    public CompletableFuture<StringStreamResult> handleStringStreamWithUsage(AgentContext context,
                                                                             Flux<ChatResponse> flux,
                                                                             String hiddenStartMarker,
                                                                             boolean emitFinalSnapshot,
                                                                             boolean pushToClient) {
        CompletableFuture<StringStreamResult> future = new CompletableFuture<>();
        StringBuilder allContent = new StringBuilder();
        StringBuilder streamBuffer = new StringBuilder();
        String messageId = canAllocateStreamMessageId(context) ? StringUtil.getUUID() : null;
        int[] intervals = resolveIntervals();
        int[] tokenIndex = new int[]{1};
        int[] emittedLength = new int[]{0};
        LlmUsageSnapshot[] usageHolder = new LlmUsageSnapshot[]{LlmUsageSnapshot.empty()};

        flux.subscribe(response -> {
            try {
                usageHolder[0] = usageHolder[0].mergeLatest(
                        LlmUsageSnapshot.resolve(response == null ? null : response.getMetadata()));
                String chunkContent = extractText(response);
                if (StringUtils.isBlank(chunkContent)) {
                    return;
                }
                allContent.append(chunkContent);
                if (pushToClient && messageId != null) {
                    // 先在完整内容上处理隐藏标记，再按 emittedLength 只发送新增区间，避免重复或泄露内部前缀。
                    String visibleContent = extractVisibleContent(allContent.toString(), hiddenStartMarker);
                    if (visibleContent.length() > emittedLength[0]) {
                        streamBuffer.append(visibleContent, emittedLength[0], visibleContent.length());
                        emittedLength[0] = visibleContent.length();
                        if (shouldFlush(tokenIndex[0], intervals[0], intervals[1])) {
                            context.getPrinter().send(messageId, context.getStreamMessageType(), streamBuffer.toString(), false);
                            streamBuffer.setLength(0);
                        }
                        tokenIndex[0]++;
                    }
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, future::completeExceptionally, () -> {
            try {
                // onComplete 负责冲刷最后不足一个 interval 的增量，并发送可选的最终快照。
                if (pushToClient && messageId != null && streamBuffer.length() > 0) {
                    context.getPrinter().send(messageId, context.getStreamMessageType(), streamBuffer.toString(), false);
                }
                if (pushToClient && messageId != null && emitFinalSnapshot) {
                    String visibleFinalContent = extractVisibleContent(allContent.toString(), hiddenStartMarker).trim();
                    if (StringUtils.isNotBlank(visibleFinalContent)) {
                        context.getPrinter().send(messageId, context.getStreamMessageType(), visibleFinalContent, true);
                    }
                }
                String finalContent = allContent.toString().trim();
                if (finalContent.isEmpty()) {
                    future.completeExceptionally(new IllegalArgumentException("Empty response from streaming LLM"));
                } else {
                    future.complete(new StringStreamResult(finalContent, usageHolder[0]));
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * 处理工具调用流式响应
     */
    public CompletableFuture<LLM.ToolCallResponse> handleToolCallStream(AgentContext context,
                                                                        Flux<ChatResponse> flux,
                                                                        long startTimeMs) {
        return handleToolCallStream(context, flux, startTimeMs, true);
    }

    /**
     * 处理工具调用流式响应，并允许调用方决定是否向前端分发流式增量。
     */
    public CompletableFuture<LLM.ToolCallResponse> handleToolCallStream(AgentContext context,
                                                                        Flux<ChatResponse> flux,
                                                                        long startTimeMs,
                                                                        boolean pushToClient) {
        // 异步结果容器
        CompletableFuture<LLM.ToolCallResponse> future = new CompletableFuture<>();

        // 双路收集：content（正式/过程文）与 reasoning（原生 CoT）
        StringBuilder allContent = new StringBuilder();
        StringBuilder allReasoning = new StringBuilder();
        StringBuilder streamBuffer = new StringBuilder();
        StringBuilder reasoningBuffer = new StringBuilder();

        // 流式推送配置
        String messageId = canAllocateStreamMessageId(context) ? StringUtil.getUUID() : null;
        String reasoningMessageId = canAllocateStreamMessageId(context) ? StringUtil.getUUID() : null;
        int[] intervals = resolveIntervals();
        int[] tokenIndex = new int[]{1};
        // content 过程文：边生成边推（助手过程回复先于 tool_call 展示）。
        // 无 tool 的终答仍会走 result；前端会对与 conclusion 同文案的过程回复去重。

        Map<String, ToolCallAccumulator> toolCallAccumulators = new LinkedHashMap<>();

        String[] finishReason = new String[1];
        LlmUsageSnapshot[] usageHolder = new LlmUsageSnapshot[]{LlmUsageSnapshot.empty()};
        int[] chunkCount = new int[]{0};
        int[] toolDeltaCount = new int[]{0};

        flux.subscribe(
            response -> {
                try {
                    // 每个 chunk 同时可能包含正文、reasoning 和 tool_call delta，三类内容必须独立累积。
                    chunkCount[0]++;
                    Generation generation = response != null ? response.getResult() : null;
                    AssistantMessage output = generation != null ? generation.getOutput() : null;

                    // 收集 tool_call 片段（仅聚合，不阻塞 content 推送）
                    if (output != null && output.getToolCalls() != null) {
                        toolDeltaCount[0] += output.getToolCalls().size();
                        mergeToolCalls(output.getToolCalls(), toolCallAccumulators);
                    }

                    // 流式 delta：禁止 trim（token 常带 leading space）
                    String chunkReasoning = ReasoningContentExtractor.extractDeltaReasoning(response);
                    String chunkContent = ReasoningContentExtractor.extractDeltaContent(response);
                    // 兼容 content 里嵌 <think> 的整段再拆
                    if (StringUtils.isNotEmpty(chunkContent)
                            && StringUtils.containsIgnoreCase(chunkContent, "<think>")) {
                        ReasoningContentExtractor.SplitResult tagged =
                                ReasoningContentExtractor.split(chunkContent, chunkReasoning);
                        chunkContent = tagged.content();
                        if (StringUtils.isNotEmpty(tagged.reasoningContent())) {
                            chunkReasoning = tagged.reasoningContent();
                        }
                    }

                    // reasoning：支持增量 / 累计两种网关；有就推
                    if (StringUtils.isNotEmpty(chunkReasoning)) {
                        String reasoningDelta = appendReasoningChunk(allReasoning, chunkReasoning);
                        if (StringUtils.isNotEmpty(reasoningDelta)
                                && pushToClient && reasoningMessageId != null && context.getPrinter() != null) {
                            reasoningBuffer.append(reasoningDelta);
                            if (shouldFlush(tokenIndex[0], intervals[0], intervals[1])
                                    || reasoningBuffer.length() >= 24) {
                                context.getPrinter().send(reasoningMessageId,
                                        ReasoningContentExtractor.EVENT_TYPE,
                                        reasoningBuffer.toString(), false);
                                reasoningBuffer.setLength(0);
                            }
                        }
                    }

                    // content：立即按间隔推送，保证「助手过程文 → 工具调用」时序
                    if (StringUtils.isNotEmpty(chunkContent)) {
                        allContent.append(chunkContent);
                        if (pushToClient && messageId != null && context.getPrinter() != null) {
                            streamBuffer.append(chunkContent);
                            if (shouldFlush(tokenIndex[0], intervals[0], intervals[1])) {
                                context.getPrinter().send(messageId, context.getStreamMessageType(),
                                    streamBuffer.toString(), false);
                                streamBuffer.setLength(0);
                            }
                            tokenIndex[0]++;
                        }
                    }

                    if (generation != null && generation.getMetadata() != null
                        && StringUtils.isNotBlank(generation.getMetadata().getFinishReason())) {
                        finishReason[0] = generation.getMetadata().getFinishReason();
                    }

                    usageHolder[0] = usageHolder[0].mergeLatest(
                            LlmUsageSnapshot.resolve(response == null ? null : response.getMetadata()));

                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            },

            future::completeExceptionally,

            () -> {
                try {
                    List<ToolCall> toolCalls = buildToolCalls(toolCallAccumulators);
                    // 整轮再 split 一次，兜底 <think> 跨 chunk 或仅 final metadata
                    ReasoningContentExtractor.SplitResult finalSplit =
                            ReasoningContentExtractor.split(allContent.toString(), allReasoning.toString());
                    String content = finalSplit.content();
                    String reasoningContent = finalSplit.reasoningContent();
                    boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
                    boolean hasReasoning = StringUtils.isNotBlank(reasoningContent);
                    boolean hasContent = StringUtils.isNotBlank(content);

                    // 只有整轮结束后才能确定工具参数是否完整；因此中间 chunk 只做聚合，不提前执行工具。
                    // reasoning 收尾：有就推 final（有/无 tool_call 均推）
                    if (pushToClient && reasoningMessageId != null && context.getPrinter() != null && hasReasoning) {
                        if (reasoningBuffer.length() > 0) {
                            context.getPrinter().send(reasoningMessageId,
                                    ReasoningContentExtractor.EVENT_TYPE,
                                    reasoningBuffer.toString(), false);
                            reasoningBuffer.setLength(0);
                        }
                        context.getPrinter().send(reasoningMessageId,
                                ReasoningContentExtractor.EVENT_TYPE,
                                reasoningContent, true);
                    }

                    // content 收尾：有正文就 final（有/无 tool 均推）。
                    // 无 tool 时后续 result 终答与过程文同文案，前端会去重隐藏过程块。
                    if (pushToClient && messageId != null && hasContent && context.getPrinter() != null) {
                        if (streamBuffer.length() > 0) {
                            context.getPrinter().send(messageId, context.getStreamMessageType(),
                                    streamBuffer.toString(), false);
                            streamBuffer.setLength(0);
                        }
                        context.getPrinter().send(messageId, context.getStreamMessageType(),
                                content, true);
                    }

                    if (!hasContent && !hasToolCalls && !hasReasoning) {
                        String requestId = context == null ? "-" : context.getRequestId();
                        log.warn("{} empty streaming tool-call response: chunks={}, toolDeltas={}, " +
                                        "accumulators={}, finishReason={}, usage={}",
                                requestId,
                                chunkCount[0],
                                toolDeltaCount[0],
                                toolCallAccumulators.size(),
                                finishReason[0],
                                usageHolder[0] == null ? null : usageHolder[0].getTotalTokens());
                        String detail = String.format(
                                "Empty response from streaming LLM (chunks=%d, toolDeltas=%d, accumulators=%d, finishReason=%s). " +
                                        "Check model endpoint, tools schema size, and whether tool_call deltas were dropped.",
                                chunkCount[0],
                                toolDeltaCount[0],
                                toolCallAccumulators.size(),
                                finishReason[0]);
                        future.completeExceptionally(new IllegalArgumentException(detail));
                        return;
                    }

                    future.complete(chatResponseMapper.applyUsage(LLM.ToolCallResponse.builder()
                        .content(hasContent ? content : null)
                        .reasoningContent(hasReasoning ? reasoningContent : null)
                        .toolCalls(toolCalls)
                        .streamMessageId(messageId)
                        .finishReason(finishReason[0])
                        .duration(System.currentTimeMillis() - startTimeMs)
                        .build(), usageHolder[0]));

                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        );

        return future;
    }

    private boolean canAllocateStreamMessageId(AgentContext context) {
        return context != null
                && Boolean.TRUE.equals(context.getIsStream())
                && StringUtils.isNotBlank(context.getStreamMessageType());
    }

    private int[] resolveIntervals() {
        int firstInterval = 1;
        int sendInterval = 3;
        try {
            String rawConfig = reactorConfig.getMessageInterval().getOrDefault("llm", "1,3");
            String[] intervalConfig = rawConfig.split(",");
            firstInterval = Math.max(1, Integer.parseInt(intervalConfig[0]));
            sendInterval = Math.max(1, Integer.parseInt(intervalConfig[1]));
        } catch (Exception ignore) {
        }
        return new int[]{firstInterval, sendInterval};
    }

    private boolean shouldFlush(int tokenIndex, int firstInterval, int sendInterval) {
        return tokenIndex == firstInterval || tokenIndex % sendInterval == 0;
    }

    /**
     * 追加 reasoning chunk：兼容增量 delta 与「每帧全量累计」两种网关。
     *
     * @return 真正需要推给前端的增量文本（可能为空）
     */
    private static String appendReasoningChunk(StringBuilder all, String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        String soFar = all.toString();
        if (soFar.isEmpty()) {
            all.append(chunk);
            return chunk;
        }
        // 累计全文：新帧以旧全文为前缀
        if (chunk.startsWith(soFar) && chunk.length() >= soFar.length()) {
            String delta = chunk.substring(soFar.length());
            all.setLength(0);
            all.append(chunk);
            return delta;
        }
        // 重复旧帧
        if (soFar.startsWith(chunk)) {
            return "";
        }
        // 真增量
        all.append(chunk);
        return chunk;
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private String extractVisibleContent(String allContent, String hiddenStartMarker) {
        if (StringUtils.isBlank(hiddenStartMarker)) {
            return allContent;
        }
        int markerIndex = allContent.indexOf(hiddenStartMarker);
        return markerIndex >= 0 ? allContent.substring(0, markerIndex) : allContent;
    }

    private void mergeToolCalls(List<AssistantMessage.ToolCall> toolCalls,
                                Map<String, ToolCallAccumulator> toolCallAccumulators) {
        int index = 0;
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            // Prefer stable id. When id is blank (some OpenAI-compatible deltas), pin by stream order
            // so name/arguments fragments still merge onto the same accumulator.
            String key = StringUtils.isNotBlank(toolCall.id())
                    ? toolCall.id()
                    : ("idx#" + index);
            // If this fragment carries an id that already exists, use it; also migrate idx key when id appears.
            if (StringUtils.isNotBlank(toolCall.id()) && toolCallAccumulators.containsKey(toolCall.id())) {
                key = toolCall.id();
            } else if (StringUtils.isNotBlank(toolCall.id()) && toolCallAccumulators.containsKey("idx#" + index)) {
                ToolCallAccumulator existing = toolCallAccumulators.remove("idx#" + index);
                toolCallAccumulators.put(toolCall.id(), existing);
                key = toolCall.id();
            }
            ToolCallAccumulator accumulator = toolCallAccumulators.computeIfAbsent(key, ignored -> new ToolCallAccumulator());
            accumulator.merge(toolCall, chatResponseMapper);
            index++;
        }
    }

    private List<ToolCall> buildToolCalls(Map<String, ToolCallAccumulator> toolCallAccumulators) {
        List<ToolCall> toolCalls = new ArrayList<>();
        for (ToolCallAccumulator accumulator : toolCallAccumulators.values()) {
            ToolCall toolCall = accumulator.toToolCall();
            if (toolCall != null) {
                toolCalls.add(toolCall);
            }
        }
        return toolCalls;
    }

    /**
     * 聚合流式 tool call 片段，兼容累计返回和增量返回两种模式。
     */
    private static class ToolCallAccumulator {
        private String id;
        private String type;
        private String name;
        private String arguments = "";

        void merge(AssistantMessage.ToolCall toolCall, LlmChatResponseMapper responseMapper) {
            if (StringUtils.isNotBlank(toolCall.id())) {
                this.id = toolCall.id();
            }
            if (StringUtils.isNotBlank(toolCall.type())) {
                this.type = toolCall.type();
            }
            if (StringUtils.isNotBlank(toolCall.name())) {
                this.name = toolCall.name();
            }
            String incomingArguments = StringUtils.defaultString(toolCall.arguments());
            if (StringUtils.isBlank(incomingArguments)) {
                return;
            }
            if (StringUtils.isBlank(this.arguments)) {
                this.arguments = incomingArguments;
                return;
            }
            if (incomingArguments.equals(this.arguments) || this.arguments.startsWith(incomingArguments)) {
                return;
            }
            if (incomingArguments.startsWith(this.arguments)) {
                this.arguments = incomingArguments;
                return;
            }
            this.arguments = this.arguments + incomingArguments;
            this.arguments = responseMapper.normalizeToolArguments(this.arguments);
        }

        ToolCall toToolCall() {
            if (StringUtils.isBlank(name)) {
                return null;
            }
            return ToolCall.builder()
                    .id(id)
                    .type(StringUtils.defaultIfBlank(type, "function"))
                    .function(ToolCall.Function.builder()
                            .name(name)
                            .arguments(StringUtils.defaultIfBlank(arguments, "{}"))
                            .build())
                    .build();
        }
    }

    @Data
    @AllArgsConstructor
    public static class StringStreamResult {
        private String content;
        private LlmUsageSnapshot usage;
    }
}

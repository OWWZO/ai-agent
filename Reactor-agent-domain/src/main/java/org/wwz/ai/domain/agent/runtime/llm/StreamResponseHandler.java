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

import reactor.core.Disposable;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

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
        // 文本流按“累积完整响应 -> 过滤隐藏标记 -> 计算新增片段 -> 按间隔推送”处理；
        // future 只在 complete/error 收口，避免每个 chunk 都改变上游调用契约。
        CompletableFuture<StringStreamResult> future = new CompletableFuture<>();
        StringBuilder allContent = new StringBuilder();
        StringBuilder streamBuffer = new StringBuilder();
        String messageId = canAllocateStreamMessageId(context) ? StringUtil.getUUID() : null;
        int[] intervals = resolveIntervals();
        int[] tokenIndex = new int[]{1};
        int[] emittedLength = new int[]{0};
        LlmUsageSnapshot[] usageHolder = new LlmUsageSnapshot[]{LlmUsageSnapshot.empty()};
        AtomicReference<Disposable> subscription = new AtomicReference<>();

        Disposable disposable = flux.subscribe(response -> {
            try {
                if (abortIfCancelled(context, future, subscription.get())) {
                    return;
                }
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
                if (abortIfCancelled(context, future, subscription.get())) {
                    return;
                }
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
        subscription.set(disposable);
        wireCancelDispose(context, future, disposable);

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
        // tool-call 流同时维护 content、reasoning 和 tool-call delta 三条累积线；
        // 中间帧只聚合，只有 onComplete 才能确认参数完整并交给执行层。
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
        AtomicReference<Disposable> subscription = new AtomicReference<>();

        log.info("{} [tool-stream-diag] handleToolCallStream subscribe start, pushToClient={}, printerNull={}",
                context == null ? "-" : context.getRequestId(),
                pushToClient,
                context == null || context.getPrinter() == null);
        int[] emitCount = new int[]{0};
        Disposable disposable = flux.subscribe(
            response -> {
                try {
                    if (abortIfCancelled(context, future, subscription.get())) {
                        return;
                    }
                    // 一个 ChatResponse 可能同时携带正文、reasoning 和多个 tool-call
                    // 片段，逐类合并后再按节流策略向前端发事件。
                    // 每个 chunk 同时可能包含正文、reasoning 和 tool_call delta，三类内容必须独立累积。
                    chunkCount[0]++;
                    Generation generation = response != null ? response.getResult() : null;
                    AssistantMessage output = generation != null ? generation.getOutput() : null;

                    // 收集 tool_call 片段（仅聚合，不阻塞 content 推送）
                    if (output != null && output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                        toolDeltaCount[0] += output.getToolCalls().size();
                        if (chunkCount[0] <= 12 || toolDeltaCount[0] <= 8 || chunkCount[0] % 20 == 0) {
                            AssistantMessage.ToolCall sample = output.getToolCalls().get(0);
                            log.info("{} [tool-stream-diag] handler chunk#{} toolDeltasInChunk={} sampleId='{}' "
                                            + "sampleName='{}' sampleArgLen={} accumulatorsBefore={}",
                                    context == null ? "-" : context.getRequestId(),
                                    chunkCount[0],
                                    output.getToolCalls().size(),
                                    sample.id(),
                                    sample.name(),
                                    sample.arguments() == null ? 0 : sample.arguments().length(),
                                    toolCallAccumulators.size());
                        }
                        mergeToolCalls(output.getToolCalls(), toolCallAccumulators);
                        int beforeEmit = emitCount[0];
                        emitToolCallDeltaEvents(context, toolCallAccumulators, pushToClient, emitCount);
                        if (emitCount[0] > beforeEmit) {
                            ToolCallAccumulator acc = toolCallAccumulators.values().stream().findFirst().orElse(null);
                            log.info("{} [tool-stream-diag] emitted tool_call_delta#{} name='{}' argsLen={} streamKey={}",
                                    context == null ? "-" : context.getRequestId(),
                                    emitCount[0],
                                    acc == null ? null : acc.name,
                                    acc == null || acc.arguments == null ? 0 : acc.arguments.length(),
                                    acc == null ? null : acc.streamKey);
                        }
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
                    if (abortIfCancelled(context, future, subscription.get())) {
                        return;
                    }
                    // 完成时再次拆分隐藏思考并冲刷尾部增量，再构造完整 toolCalls；
                    // 这是唯一允许把聚合参数交给后续工具调度的边界。
                    // 定稿前再冲一帧：带上真实 toolCallId + 完整 argumentsText，方便前端与 running 对齐。
                    flushToolCallDeltaEvents(context, toolCallAccumulators, pushToClient);
                    List<ToolCall> toolCalls = buildToolCalls(toolCallAccumulators);
                    log.info("{} [tool-stream-diag] handleToolCallStream complete: chunks={}, toolDeltas={}, "
                                    + "accumulators={}, builtToolCalls={}, emitCount={}, pushToClient={}, finishReason={}",
                            context == null ? "-" : context.getRequestId(),
                            chunkCount[0],
                            toolDeltaCount[0],
                            toolCallAccumulators.size(),
                            toolCalls == null ? 0 : toolCalls.size(),
                            emitCount[0],
                            pushToClient,
                            finishReason[0]);
                    for (ToolCallAccumulator acc : toolCallAccumulators.values()) {
                        log.info("{} [tool-stream-diag] final accumulator: id='{}' name='{}' argsLen={} streamKey={}",
                                context == null ? "-" : context.getRequestId(),
                                acc.id,
                                acc.name,
                                acc.arguments == null ? 0 : acc.arguments.length(),
                                acc.streamKey);
                    }
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
        subscription.set(disposable);
        wireCancelDispose(context, future, disposable);

        return future;
    }

    /**
     * 用户停止 / 流断开时尽快 dispose 上游 LLM 流，避免继续烧 token 与假死等待。
     */
    private static void wireCancelDispose(AgentContext context,
                                          CompletableFuture<?> future,
                                          Disposable disposable) {
        if (context == null || context.getRunCancellation() == null || disposable == null) {
            return;
        }
        // 协作式取消没有统一 listener 总线：future 完成时若已取消则 dispose；
        // 主循环每 chunk 也会检查 isRunCancelled 并主动 abort。
        future.whenComplete((ignored, error) -> {
            if (context.isRunCancelled() && !disposable.isDisposed()) {
                disposable.dispose();
            }
        });
    }

    private static boolean abortIfCancelled(AgentContext context,
                                            CompletableFuture<?> future,
                                            Disposable disposable) {
        if (context == null || !context.isRunCancelled() || future.isDone()) {
            return false;
        }
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
        future.completeExceptionally(new CancellationException(
                "LLM stream aborted: " + StringUtils.defaultIfBlank(
                        context.getRunCancelReason(), "user_stop")));
        return true;
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
        // 兼容网关既可能返回“累计全文”也可能返回“纯增量”的两种格式：当 chunk
        // 以已有全文为前缀时只取尾部，否则把它当作新的增量，重复帧直接忽略。
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
            // Prefer stable id. When id is blank (OpenAI 后续 delta 常只带 index)，
            // 按 streamIndex / idx# 回填到同一 accumulator，避免 arguments 碎片拆卡。
            final int streamIndex = index;
            String key;
            if (StringUtils.isNotBlank(toolCall.id())) {
                key = toolCall.id();
                if (toolCallAccumulators.containsKey("idx#" + streamIndex)
                        && !toolCallAccumulators.containsKey(toolCall.id())) {
                    ToolCallAccumulator existing = toolCallAccumulators.remove("idx#" + streamIndex);
                    toolCallAccumulators.put(toolCall.id(), existing);
                }
            } else {
                String byIndex = findAccumulatorKeyByStreamIndex(toolCallAccumulators, streamIndex);
                key = StringUtils.isNotBlank(byIndex) ? byIndex : ("idx#" + streamIndex);
            }
            ToolCallAccumulator accumulator = toolCallAccumulators.computeIfAbsent(key, ignored -> {
                ToolCallAccumulator created = new ToolCallAccumulator();
                // 展示用稳定 key：无真实 id 时也能立刻推前端，且 messageId 全程不变。
                created.streamKey = "stream-tool-" + StringUtil.getUUID();
                created.streamIndex = streamIndex;
                return created;
            });
            if (StringUtils.isBlank(accumulator.streamKey)) {
                accumulator.streamKey = "stream-tool-" + StringUtil.getUUID();
            }
            accumulator.streamIndex = streamIndex;
            accumulator.merge(toolCall);
            index++;
        }
    }

    private static String findAccumulatorKeyByStreamIndex(Map<String, ToolCallAccumulator> toolCallAccumulators,
                                                          int streamIndex) {
        if (toolCallAccumulators == null || toolCallAccumulators.isEmpty()) {
            return null;
        }
        String idxKey = "idx#" + streamIndex;
        if (toolCallAccumulators.containsKey(idxKey)) {
            return idxKey;
        }
        for (Map.Entry<String, ToolCallAccumulator> entry : toolCallAccumulators.entrySet()) {
            ToolCallAccumulator value = entry.getValue();
            if (value != null && value.streamIndex == streamIndex) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 将已经聚合的 tool-call 状态实时推给前端；这里只做展示，不把参数交给执行层。
     * messageId 固定为 streamKey，避免真实 toolCallId 晚到时前端拆成两张卡。
     */
    private void emitToolCallDeltaEvents(AgentContext context,
                                         Map<String, ToolCallAccumulator> toolCallAccumulators,
                                         boolean pushToClient) {
        emitToolCallDeltaEvents(context, toolCallAccumulators, pushToClient, null);
    }

    private void emitToolCallDeltaEvents(AgentContext context,
                                         Map<String, ToolCallAccumulator> toolCallAccumulators,
                                         boolean pushToClient,
                                         int[] emitCount) {
        if (!pushToClient || context == null || context.getPrinter() == null) {
            if (log.isDebugEnabled()) {
                log.debug("[tool-stream-diag] skip emit: pushToClient={} printerNull={}",
                        pushToClient, context == null || context.getPrinter() == null);
            }
            return;
        }
        for (ToolCallAccumulator accumulator : toolCallAccumulators.values()) {
            if (!accumulator.shouldEmitDelta()) {
                continue;
            }
            sendToolCallDelta(context, accumulator);
            if (emitCount != null) {
                emitCount[0]++;
            }
        }
    }

    /** 忽略节流，把当前聚合态全部推出去（流结束前使用）。 */
    private void flushToolCallDeltaEvents(AgentContext context,
                                          Map<String, ToolCallAccumulator> toolCallAccumulators,
                                          boolean pushToClient) {
        if (!pushToClient || context == null || context.getPrinter() == null) {
            return;
        }
        for (ToolCallAccumulator accumulator : toolCallAccumulators.values()) {
            if (StringUtils.isBlank(accumulator.name) && StringUtils.isEmpty(accumulator.arguments)) {
                continue;
            }
            accumulator.markEmitted();
            sendToolCallDelta(context, accumulator);
        }
    }

    private void sendToolCallDelta(AgentContext context, ToolCallAccumulator accumulator) {
        String streamKey = StringUtils.defaultIfBlank(accumulator.streamKey, accumulator.id);
        if (StringUtils.isBlank(streamKey)) {
            return;
        }
        String displayToolCallId = StringUtils.isNotBlank(accumulator.id)
                ? accumulator.id
                : streamKey;
        // 对齐 LeAgent：专用 tool_call_delta 事件 + 累计 argumentsRaw/argumentsText。
        // messageId 固定 streamKey，前端与后续 running tool_call 用 streamToolKey/toolCallId 并卡。
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageType", "tool_call_delta");
        payload.put("status", "streaming");
        if (StringUtils.isNotBlank(accumulator.name)) {
            payload.put("toolName", accumulator.name);
        }
        payload.put("toolCallId", displayToolCallId);
        payload.put("streamToolKey", streamKey);
        if (accumulator.streamIndex >= 0) {
            payload.put("streamToolIndex", accumulator.streamIndex);
        }
        if (StringUtils.isNotEmpty(accumulator.arguments)) {
            // argumentsRaw：与 LeAgent 同名字段；argumentsText：兼容既有 FE 合并逻辑
            payload.put("argumentsRaw", accumulator.arguments);
            payload.put("argumentsText", accumulator.arguments);
        }
        payload.put("summary", StringUtils.isNotBlank(accumulator.name)
                ? ("正在生成 " + accumulator.name + " 参数…")
                : "正在生成工具参数…");
        payload.put("isFinal", false);
        if (log.isInfoEnabled()) {
            log.info("{} [tool-stream-diag] printer.send tool_call_delta streamKey={} toolCallId={} "
                            + "toolName={} argsLen={}",
                    context.getRequestId(),
                    streamKey,
                    displayToolCallId,
                    accumulator.name,
                    accumulator.arguments == null ? 0 : accumulator.arguments.length());
        }
        context.getPrinter().send(streamKey, "tool_call_delta", payload, false);
    }

    private List<ToolCall> buildToolCalls(Map<String, ToolCallAccumulator> toolCallAccumulators) {
        List<ToolCall> toolCalls = new ArrayList<>();
        for (ToolCallAccumulator accumulator : toolCallAccumulators.values()) {
            ToolCall toolCall = accumulator.toToolCall(chatResponseMapper);
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
        /** 前端卡片稳定主键（printer messageId），全程不变 */
        private String streamKey;
        private int streamIndex = -1;
        private String id;
        private String type;
        private String name;
        private String arguments = "";
        private String lastEmittedName;
        private String lastEmittedArguments;
        private int lastEmittedArgsLength;
        private long lastEmittedAtMs;

        void merge(AssistantMessage.ToolCall toolCall) {
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
        }

        /**
         * 有 name 或已有参数即可展示；无真实 id 也推。
         * 大参数按长度/时间节流，避免每个 token 刷爆 SSE，但仍保持可见增长。
         */
        boolean shouldEmitDelta() {
            boolean hasName = StringUtils.isNotBlank(name);
            boolean hasArgs = StringUtils.isNotEmpty(arguments);
            if (!hasName && !hasArgs) {
                return false;
            }
            if (Objects.equals(lastEmittedName, name)
                    && Objects.equals(lastEmittedArguments, arguments)) {
                return false;
            }
            long now = System.currentTimeMillis();
            int argsLen = arguments == null ? 0 : arguments.length();
            int grew = argsLen - lastEmittedArgsLength;
            boolean firstEmit = lastEmittedName == null && lastEmittedArguments == null;
            boolean nameChanged = !Objects.equals(lastEmittedName, name);
            // 贴近终答 token 节奏：有增长就尽快推，前端 useStreamingText 再做逐字追赶
            boolean enoughGrowth = grew >= 1;
            boolean enoughTime = lastEmittedAtMs > 0 && (now - lastEmittedAtMs) >= 16;
            // 首帧 / 改名 / JSON 顶层闭合立即发；其余按增长或时间节流
            boolean looksComplete = hasArgs
                    && (arguments.endsWith("}") || arguments.endsWith("]"));
            if (!firstEmit && !nameChanged && !enoughGrowth && !enoughTime && !looksComplete) {
                return false;
            }
            markEmitted();
            return true;
        }

        void markEmitted() {
            lastEmittedName = name;
            lastEmittedArguments = arguments;
            lastEmittedArgsLength = arguments == null ? 0 : arguments.length();
            lastEmittedAtMs = System.currentTimeMillis();
        }

        ToolCall toToolCall(LlmChatResponseMapper responseMapper) {
            if (StringUtils.isBlank(name)) {
                return null;
            }
            String resolvedId = StringUtils.isNotBlank(id)
                    ? id
                    : StringUtils.defaultIfBlank(streamKey, StringUtil.getUUID());
            String rawArgs = StringUtils.defaultIfBlank(arguments, "{}");
            String normalizedArgs = responseMapper != null
                    ? responseMapper.normalizeToolArguments(rawArgs)
                    : rawArgs;
            return ToolCall.builder()
                    .id(resolvedId)
                    .type(StringUtils.defaultIfBlank(type, "function"))
                    .function(ToolCall.Function.builder()
                            .name(name)
                            .arguments(normalizedArgs)
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

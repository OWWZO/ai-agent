package org.wwz.ai.domain.agent.runtime.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayTiming;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 处理 Domain LLM stream event 的流式响应聚合器。
 * <p>
 * 供应商流对象已经在 LlmCompletionPort 之后被转换为纯 Java 事件；本类只保留
 * 增量推送、工具调用聚合、超时和取消语义。
 */
@Slf4j
public class StreamResponseHandler {

    @Resource
    private ReactorConfig reactorConfig;

    public CompletableFuture<String> handleStringStream(AgentContext context,
                                                         LlmCompletionPort.StreamCall streamCall) {
        return handleStringStream(context, streamCall, null, false, true);
    }

    public CompletableFuture<String> handleStringStream(AgentContext context,
                                                         LlmCompletionPort.StreamCall streamCall,
                                                         String hiddenStartMarker,
                                                         boolean emitFinalSnapshot) {
        return handleStringStream(context, streamCall, hiddenStartMarker, emitFinalSnapshot, true);
    }

    public CompletableFuture<String> handleStringStream(AgentContext context,
                                                         LlmCompletionPort.StreamCall streamCall,
                                                         String hiddenStartMarker,
                                                         boolean emitFinalSnapshot,
                                                         boolean pushToClient) {
        return handleStringStreamWithUsage(context, streamCall, hiddenStartMarker, emitFinalSnapshot, pushToClient)
                .thenApply(result -> result == null ? null : result.getContent());
    }

    public CompletableFuture<StringStreamResult> handleStringStreamWithUsage(AgentContext context,
                                                                               LlmCompletionPort.StreamCall streamCall,
                                                                               String hiddenStartMarker,
                                                                               boolean emitFinalSnapshot,
                                                                               boolean pushToClient) {
        return handleStringStreamWithUsage(context, streamCall, hiddenStartMarker, emitFinalSnapshot,
                pushToClient, 0, null);
    }

    public CompletableFuture<StringStreamResult> handleStringStreamWithUsage(AgentContext context,
                                                                               LlmCompletionPort.StreamCall streamCall,
                                                                               String hiddenStartMarker,
                                                                               boolean emitFinalSnapshot,
                                                                               boolean pushToClient,
                                                                               int timeoutSeconds) {
        return handleStringStreamWithUsage(context, streamCall, hiddenStartMarker, emitFinalSnapshot,
                pushToClient, timeoutSeconds, null);
    }

    public CompletableFuture<StringStreamResult> handleStringStreamWithUsage(AgentContext context,
                                                                               LlmCompletionPort.StreamCall streamCall,
                                                                               String hiddenStartMarker,
                                                                               boolean emitFinalSnapshot,
                                                                               boolean pushToClient,
                                                                               int timeoutSeconds,
                                                                               ReplayTiming runtimeTiming) {
        if (context != null && context.isRunCancelled()) {
            return failedFuture(new LlmCancelledException(context.getRunCancelReason(), null));
        }
        if (streamCall == null) {
            return failedFuture(new IllegalArgumentException("LLM stream call must not be null"));
        }

        CompletableFuture<StringStreamResult> future = new CompletableFuture<>();
        StringBuilder allContent = new StringBuilder();
        StringBuilder streamBuffer = new StringBuilder();
        String messageId = canAllocateStreamMessageId(context) ? StringUtil.getUUID() : null;
        int[] intervals = resolveIntervals();
        int[] tokenIndex = new int[]{1};
        int[] emittedLength = new int[]{0};
        LlmUsageSnapshot[] usageHolder = new LlmUsageSnapshot[]{LlmUsageSnapshot.empty()};
        AtomicReference<String> partial = new AtomicReference<>();

        java.util.function.Supplier<String> partialContent = () -> partial.get();
        try {
            streamCall.subscribe(event -> {
                try {
                    if (abortIfInactive(context, future, streamCall, partialContent)) {
                        return;
                    }
                    usageHolder[0] = usageHolder[0].mergeLatest(
                            LlmUsageSnapshot.resolve(event == null ? null : event.getUsage()));
                    String chunkContent = ReasoningContentExtractor.extractDeltaContent(event);
                    if (StringUtils.isBlank(chunkContent)) {
                        return;
                    }
                    allContent.append(chunkContent);
                    String visible = extractVisibleContent(allContent.toString(), hiddenStartMarker);
                    partial.set(visible.trim().isEmpty() ? null : visible.trim());
                    if (pushToClient && messageId != null && visible.length() > emittedLength[0]) {
                        streamBuffer.append(visible, emittedLength[0], visible.length());
                        emittedLength[0] = visible.length();
                        if (shouldFlush(tokenIndex[0], intervals[0], intervals[1])) {
                            sendStreamEvent(context, messageId, context.getStreamMessageType(),
                                    streamBuffer.toString(), runtimeTiming, false);
                            streamBuffer.setLength(0);
                        }
                        tokenIndex[0]++;
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    streamCall.cancel();
                }
            }, error -> {
                if (context != null && context.isRunCancelled()) {
                    abortStream(context, future, streamCall, partialContent);
                    return;
                }
                completeExceptionallyIfActive(future, error);
            }, () -> {
                try {
                    if (abortIfInactive(context, future, streamCall, partialContent)) {
                        return;
                    }
                    if (pushToClient && messageId != null && streamBuffer.length() > 0) {
                        sendStreamEvent(context, messageId, context.getStreamMessageType(),
                                streamBuffer.toString(), runtimeTiming, false);
                    }
                    if (pushToClient && messageId != null && emitFinalSnapshot) {
                        String visibleFinalContent = extractVisibleContent(allContent.toString(), hiddenStartMarker).trim();
                        if (StringUtils.isNotBlank(visibleFinalContent)) {
                            completeRuntimeTiming(runtimeTiming);
                            sendStreamEvent(context, messageId, context.getStreamMessageType(),
                                    visibleFinalContent, runtimeTiming, true);
                        }
                    }
                    completeRuntimeTiming(runtimeTiming);
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
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        armStream(context, future, streamCall, partialContent, timeoutSeconds);
        return future;
    }

    public CompletableFuture<LLM.ToolCallResponse> handleToolCallStream(AgentContext context,
                                                                         LlmCompletionPort.StreamCall streamCall,
                                                                         long startTimeMs) {
        return handleToolCallStream(context, streamCall, startTimeMs, true);
    }

    public CompletableFuture<LLM.ToolCallResponse> handleToolCallStream(AgentContext context,
                                                                         LlmCompletionPort.StreamCall streamCall,
                                                                         long startTimeMs,
                                                                         boolean pushToClient) {
        return handleToolCallStream(context, streamCall, startTimeMs, pushToClient, 0, null);
    }

    public CompletableFuture<LLM.ToolCallResponse> handleToolCallStream(AgentContext context,
                                                                         LlmCompletionPort.StreamCall streamCall,
                                                                         long startTimeMs,
                                                                         boolean pushToClient,
                                                                         int timeoutSeconds) {
        return handleToolCallStream(context, streamCall, startTimeMs, pushToClient, timeoutSeconds, null);
    }

    public CompletableFuture<LLM.ToolCallResponse> handleToolCallStream(AgentContext context,
                                                                          LlmCompletionPort.StreamCall streamCall,
                                                                          long startTimeMs,
                                                                          boolean pushToClient,
                                                                          int timeoutSeconds,
                                                                          ReplayTiming runtimeTiming) {
        if (context != null && context.isRunCancelled()) {
            return failedFuture(new LlmCancelledException(context.getRunCancelReason(), null));
        }
        if (streamCall == null) {
            return failedFuture(new IllegalArgumentException("LLM stream call must not be null"));
        }

        CompletableFuture<LLM.ToolCallResponse> future = new CompletableFuture<>();
        StringBuilder allContent = new StringBuilder();
        StringBuilder allReasoning = new StringBuilder();
        StringBuilder streamBuffer = new StringBuilder();
        StringBuilder reasoningBuffer = new StringBuilder();
        String messageId = canAllocateStreamMessageId(context) ? StringUtil.getUUID() : null;
        String reasoningMessageId = canAllocateStreamMessageId(context) ? StringUtil.getUUID() : null;
        int[] intervals = resolveIntervals();
        int[] tokenIndex = new int[]{1};
        Map<String, ToolCallAccumulator> toolCallAccumulators = new LinkedHashMap<>();
        String[] finishReason = new String[1];
        LlmUsageSnapshot[] usageHolder = new LlmUsageSnapshot[]{LlmUsageSnapshot.empty()};
        int[] chunkCount = new int[]{0};
        int[] toolDeltaCount = new int[]{0};
        AtomicReference<String> partial = new AtomicReference<>();
        java.util.function.Supplier<String> partialContent = partial::get;

        try {
            streamCall.subscribe(event -> {
                try {
                    if (abortIfInactive(context, future, streamCall, partialContent)) {
                        return;
                    }
                    chunkCount[0]++;
                    List<LlmToolCall> toolCalls = event == null ? null : event.getToolCalls();
                    if (toolCalls != null) {
                        toolDeltaCount[0] += toolCalls.size();
                        mergeToolCalls(toolCalls, toolCallAccumulators);
                    }

                    String chunkReasoning = ReasoningContentExtractor.extractDeltaReasoning(event);
                    String chunkContent = ReasoningContentExtractor.extractDeltaContent(event);
                    if (StringUtils.isNotEmpty(chunkContent)
                            && StringUtils.containsIgnoreCase(chunkContent, "<think>")) {
                        ReasoningContentExtractor.SplitResult tagged =
                                ReasoningContentExtractor.split(chunkContent, chunkReasoning);
                        chunkContent = tagged.content();
                        if (StringUtils.isNotEmpty(tagged.reasoningContent())) {
                            chunkReasoning = tagged.reasoningContent();
                        }
                    }

                    if (StringUtils.isNotEmpty(chunkReasoning)) {
                        String reasoningDelta = appendReasoningChunk(allReasoning, chunkReasoning);
                        if (StringUtils.isNotEmpty(reasoningDelta)
                                && pushToClient && reasoningMessageId != null && context.getPrinter() != null) {
                            reasoningBuffer.append(reasoningDelta);
                            if (shouldFlush(tokenIndex[0], intervals[0], intervals[1])
                                    || reasoningBuffer.length() >= 24) {
                                sendStreamEvent(context, reasoningMessageId, ReasoningContentExtractor.EVENT_TYPE,
                                        reasoningBuffer.toString(), runtimeTiming, false);
                                reasoningBuffer.setLength(0);
                            }
                        }
                    }

                    if (StringUtils.isNotEmpty(chunkContent)) {
                        allContent.append(chunkContent);
                        partial.set(allContent.toString().trim());
                        if (pushToClient && messageId != null && context.getPrinter() != null) {
                            streamBuffer.append(chunkContent);
                            if (shouldFlush(tokenIndex[0], intervals[0], intervals[1])) {
                                sendStreamEvent(context, messageId, context.getStreamMessageType(),
                                        streamBuffer.toString(), runtimeTiming, false);
                                streamBuffer.setLength(0);
                            }
                            tokenIndex[0]++;
                        }
                    }

                    if (event != null && StringUtils.isNotBlank(event.getFinishReason())) {
                        finishReason[0] = event.getFinishReason();
                    }
                    usageHolder[0] = usageHolder[0].mergeLatest(
                            LlmUsageSnapshot.resolve(event == null ? null : event.getUsage()));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    streamCall.cancel();
                }
            }, error -> {
                if (context != null && context.isRunCancelled()) {
                    abortStream(context, future, streamCall, partialContent);
                    return;
                }
                completeExceptionallyIfActive(future, error);
            }, () -> {
                try {
                    if (abortIfInactive(context, future, streamCall, partialContent)) {
                        return;
                    }
                    List<ToolCall> toolCalls = buildToolCalls(toolCallAccumulators);
                    ReasoningContentExtractor.SplitResult finalSplit =
                            ReasoningContentExtractor.split(allContent.toString(), allReasoning.toString());
                    String content = finalSplit.content();
                    String reasoningContent = finalSplit.reasoningContent();
                    boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
                    boolean hasReasoning = StringUtils.isNotBlank(reasoningContent);
                    boolean hasContent = StringUtils.isNotBlank(content);

                    if (pushToClient && reasoningMessageId != null && context.getPrinter() != null && hasReasoning) {
                        if (reasoningBuffer.length() > 0) {
                            sendStreamEvent(context, reasoningMessageId, ReasoningContentExtractor.EVENT_TYPE,
                                    reasoningBuffer.toString(), runtimeTiming, false);
                            reasoningBuffer.setLength(0);
                        }
                        completeRuntimeTiming(runtimeTiming);
                        sendStreamEvent(context, reasoningMessageId, ReasoningContentExtractor.EVENT_TYPE,
                                reasoningContent, runtimeTiming, true);
                    }
                    if (pushToClient && messageId != null && hasContent && context.getPrinter() != null) {
                        if (streamBuffer.length() > 0) {
                            sendStreamEvent(context, messageId, context.getStreamMessageType(),
                                    streamBuffer.toString(), runtimeTiming, false);
                            streamBuffer.setLength(0);
                        }
                        completeRuntimeTiming(runtimeTiming);
                        sendStreamEvent(context, messageId, context.getStreamMessageType(),
                                content, runtimeTiming, true);
                    }

                    completeRuntimeTiming(runtimeTiming);
                    if (!hasContent && !hasToolCalls && !hasReasoning) {
                        String requestId = context == null ? "-" : context.getRequestId();
                        log.warn("{} empty streaming tool-call response: chunks={}, toolDeltas={}, accumulators={}, finishReason={}, usage={}",
                                requestId, chunkCount[0], toolDeltaCount[0], toolCallAccumulators.size(),
                                finishReason[0], usageHolder[0].getTotalTokens());
                        future.completeExceptionally(new IllegalArgumentException(
                                String.format("Empty response from streaming LLM (chunks=%d, toolDeltas=%d, accumulators=%d, finishReason=%s). "
                                                + "Check model endpoint, tools schema size, and whether tool_call deltas were dropped.",
                                        chunkCount[0], toolDeltaCount[0], toolCallAccumulators.size(), finishReason[0])));
                        return;
                    }

                    LLM.ToolCallResponse response = LLM.ToolCallResponse.builder()
                            .content(hasContent ? content : null)
                            .reasoningContent(hasReasoning ? reasoningContent : null)
                            .toolCalls(toolCalls)
                            .streamMessageId(messageId)
                            .finishReason(finishReason[0])
                            .duration(System.currentTimeMillis() - startTimeMs)
                            .timing(snapshotTiming(runtimeTiming))
                            .build();
                    applyUsage(response, usageHolder[0]);
                    future.complete(response);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        armStream(context, future, streamCall, partialContent, timeoutSeconds);
        return future;
    }

    private void armStream(AgentContext context,
                           CompletableFuture<?> future,
                           LlmCompletionPort.StreamCall streamCall,
                           java.util.function.Supplier<String> partialContent,
                           int timeoutSeconds) {
        Runnable unregister = context == null
                ? () -> {
                }
                : context.registerRunAbort(() -> abortStream(context, future, streamCall, partialContent));
        wireStreamLifecycle(future, streamCall, timeoutSeconds, unregister);
    }

    private static void wireStreamLifecycle(CompletableFuture<?> future,
                                            LlmCompletionPort.StreamCall streamCall,
                                            int timeoutSeconds,
                                            Runnable unregister) {
        if (future == null) {
            return;
        }
        Runnable release = () -> {
            if (unregister != null) {
                unregister.run();
            }
            cancelQuietly(streamCall);
        };
        if (timeoutSeconds > 0) {
            CompletableFuture.delayedExecutor(timeoutSeconds, TimeUnit.SECONDS).execute(() -> {
                if (future.completeExceptionally(new TimeoutException("LLM stream timeout after " + timeoutSeconds + "s"))) {
                    release.run();
                }
            });
        }
        future.whenComplete((ignored, error) -> release.run());
    }

    private static boolean abortIfInactive(AgentContext context,
                                           CompletableFuture<?> future,
                                           LlmCompletionPort.StreamCall streamCall,
                                           java.util.function.Supplier<String> partialContent) {
        if (future.isDone()) {
            cancelQuietly(streamCall);
            return true;
        }
        if (context != null && context.isRunCancelled()) {
            abortStream(context, future, streamCall, partialContent);
            return true;
        }
        return false;
    }

    private static void abortStream(AgentContext context,
                                    CompletableFuture<?> future,
                                    LlmCompletionPort.StreamCall streamCall,
                                    java.util.function.Supplier<String> partialContent) {
        if (future == null || future.isDone()) {
            cancelQuietly(streamCall);
            return;
        }
        String partial = null;
        if (partialContent != null) {
            try {
                partial = partialContent.get();
            } catch (RuntimeException ignored) {
                partial = null;
            }
        }
        String reason = context == null ? null : context.getRunCancelReason();
        future.completeExceptionally(new LlmCancelledException(reason, partial));
        cancelQuietly(streamCall);
    }

    private static void completeExceptionallyIfActive(CompletableFuture<?> future, Throwable error) {
        if (future != null && !future.isDone()) {
            future.completeExceptionally(error);
        }
    }

    private static void cancelQuietly(LlmCompletionPort.StreamCall streamCall) {
        if (streamCall != null) {
            try {
                streamCall.cancel();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private boolean canAllocateStreamMessageId(AgentContext context) {
        return context != null
                && Boolean.TRUE.equals(context.getIsStream())
                && StringUtils.isNotBlank(context.getStreamMessageType());
    }

    private void sendStreamEvent(AgentContext context,
                                 String messageId,
                                 String messageType,
                                 Object message,
                                 ReplayTiming runtimeTiming,
                                 boolean isFinal) {
        if (context == null || context.getPrinter() == null) {
            return;
        }
        if (runtimeTiming == null) {
            context.getPrinter().send(messageId, messageType, message, isFinal);
            return;
        }
        context.getPrinter().sendWithResultMap(messageId, messageType, message,
                Map.of("timing", snapshotTiming(runtimeTiming)), isFinal);
    }

    private void completeRuntimeTiming(ReplayTiming timing) {
        if (timing == null || timing.getFinishedAt() != null || timing.getStartedAt() == null) {
            return;
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        timing.setFinishedAt(finishedAt);
        timing.setDurationMs(Math.max(0L, Duration.between(timing.getStartedAt(), finishedAt).toMillis()));
        timing.setSource(ReplayTiming.SOURCE_RUNTIME);
    }

    private ReplayTiming snapshotTiming(ReplayTiming timing) {
        if (timing == null) {
            return null;
        }
        return ReplayTiming.builder()
                .startedAt(timing.getStartedAt())
                .finishedAt(timing.getFinishedAt())
                .durationMs(timing.getDurationMs())
                .source(timing.getSource())
                .build();
    }

    private int[] resolveIntervals() {
        int firstInterval = 1;
        int sendInterval = 3;
        try {
            String rawConfig = reactorConfig == null || reactorConfig.getMessageInterval() == null
                    ? "1,3"
                    : reactorConfig.getMessageInterval().getOrDefault("llm", "1,3");
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

    private static String appendReasoningChunk(StringBuilder all, String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        String soFar = all.toString();
        if (soFar.isEmpty()) {
            all.append(chunk);
            return chunk;
        }
        if (chunk.startsWith(soFar)) {
            String delta = chunk.substring(soFar.length());
            all.setLength(0);
            all.append(chunk);
            return delta;
        }
        if (soFar.startsWith(chunk)) {
            return "";
        }
        all.append(chunk);
        return chunk;
    }

    private String extractVisibleContent(String allContent, String hiddenStartMarker) {
        if (StringUtils.isBlank(hiddenStartMarker)) {
            return allContent;
        }
        int markerIndex = allContent.indexOf(hiddenStartMarker);
        return markerIndex >= 0 ? allContent.substring(0, markerIndex) : allContent;
    }

    private void mergeToolCalls(List<LlmToolCall> toolCalls,
                                Map<String, ToolCallAccumulator> toolCallAccumulators) {
        int index = 0;
        for (LlmToolCall toolCall : toolCalls) {
            if (toolCall == null) {
                index++;
                continue;
            }
            String key = StringUtils.isNotBlank(toolCall.getId()) ? toolCall.getId() : "idx#" + index;
            if (StringUtils.isNotBlank(toolCall.getId())
                    && toolCallAccumulators.containsKey("idx#" + index)) {
                ToolCallAccumulator existing = toolCallAccumulators.remove("idx#" + index);
                toolCallAccumulators.put(toolCall.getId(), existing);
                key = toolCall.getId();
            }
            ToolCallAccumulator accumulator = toolCallAccumulators.computeIfAbsent(key,
                    ignored -> new ToolCallAccumulator());
            accumulator.merge(toolCall);
            index++;
        }
    }

    private List<ToolCall> buildToolCalls(Map<String, ToolCallAccumulator> accumulators) {
        List<ToolCall> toolCalls = new ArrayList<>();
        for (ToolCallAccumulator accumulator : accumulators.values()) {
            ToolCall toolCall = accumulator.toToolCall();
            if (toolCall != null) {
                toolCalls.add(toolCall);
            }
        }
        return toolCalls;
    }

    private void applyUsage(LLM.ToolCallResponse response, LlmUsageSnapshot usage) {
        if (response == null || usage == null) {
            return;
        }
        response.setPromptTokens(usage.getPromptTokens());
        response.setCompletionTokens(usage.getCompletionTokens());
        response.setTotalTokens(usage.getTotalTokens());
        response.setCachedPromptTokens(usage.getCachedPromptTokens());
        response.setPromptTextTokens(usage.getPromptTextTokens());
        response.setPromptAudioTokens(usage.getPromptAudioTokens());
        response.setPromptImageTokens(usage.getPromptImageTokens());
        response.setCompletionTextTokens(usage.getCompletionTextTokens());
        response.setCompletionAudioTokens(usage.getCompletionAudioTokens());
        response.setReasoningTokens(usage.getReasoningTokens());
    }

    private static class ToolCallAccumulator {
        private String id;
        private String type;
        private String name;
        private String arguments = "";

        void merge(LlmToolCall toolCall) {
            if (StringUtils.isNotBlank(toolCall.getId())) {
                id = toolCall.getId();
            }
            if (StringUtils.isNotBlank(toolCall.getType())) {
                type = toolCall.getType();
            }
            if (StringUtils.isNotBlank(toolCall.getName())) {
                name = toolCall.getName();
            }
            String incoming = StringUtils.defaultString(toolCall.getArguments());
            if (StringUtils.isBlank(incoming)) {
                return;
            }
            if (StringUtils.isBlank(arguments)) {
                arguments = incoming;
            } else if (!incoming.equals(arguments) && !arguments.startsWith(incoming)) {
                if (incoming.startsWith(arguments)) {
                    arguments = incoming;
                } else {
                    arguments = arguments + incoming;
                }
            }
        }

        ToolCall toToolCall() {
            if (StringUtils.isBlank(name)) {
                return null;
            }
            return ToolCall.builder()
                    .id(StringUtils.defaultIfBlank(id, StringUtil.getUUID()))
                    .type(StringUtils.defaultIfBlank(type, "function"))
                    .function(ToolCall.Function.builder()
                            .name(name)
                            .arguments(StringUtils.defaultIfBlank(arguments, "{}"))
                            .build())
                    .build();
        }
    }

    private <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    @Data
    @AllArgsConstructor
    public static class StringStreamResult {
        private String content;
        private LlmUsageSnapshot usage;
    }
}

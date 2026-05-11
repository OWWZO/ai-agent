package org.wwz.ai.domain.agent.runtime.llm;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
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
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder allContent = new StringBuilder();
        StringBuilder streamBuffer = new StringBuilder();
        String messageId = canAllocateStreamMessageId(context) ? StringUtil.getUUID() : null;
        int[] intervals = resolveIntervals();
        int[] tokenIndex = new int[]{1};
        int[] emittedLength = new int[]{0};

        flux.subscribe(response -> {
            try {
                String chunkContent = extractText(response);
                if (StringUtils.isBlank(chunkContent)) {
                    return;
                }
                allContent.append(chunkContent);
                if (pushToClient && messageId != null) {
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
                    future.complete(finalContent);
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

        // 内容收集器
        StringBuilder allContent = new StringBuilder();      // 完整内容
        StringBuilder streamBuffer = new StringBuilder();    // 推送缓冲区

        // 流式推送配置
        String messageId = canAllocateStreamMessageId(context) ? StringUtil.getUUID() : null;
        int[] intervals = resolveIntervals();   // 推送间隔配置
        int[] tokenIndex = new int[]{1};        // token计数器

        // 工具调用收集器 (按id合并多chunk)
        Map<String, ToolCallAccumulator> toolCallAccumulators = new LinkedHashMap<>();

        // 元数据
        String[] finishReason = new String[1];   // 结束原因
        Integer[] totalTokens = new Integer[1];  // 总token数

        // 订阅数据流
        flux.subscribe(
            // === 处理每个数据块 ===
            response -> {
                try {
                    // 提取文本内容
                    Generation generation = response != null ? response.getResult() : null;
                    AssistantMessage output = generation != null ? generation.getOutput() : null;
                    String chunkContent = output != null ? output.getText() : null;

                    // 累积文本内容
                    if (StringUtils.isNotBlank(chunkContent)) {
                        allContent.append(chunkContent);

                        // 流式推送：缓冲+按条件刷新
                        if (pushToClient && messageId != null) {
                            streamBuffer.append(chunkContent);
                            if (shouldFlush(tokenIndex[0], intervals[0], intervals[1])) {
                                // 发送缓冲区内容(非结束)
                                context.getPrinter().send(messageId, context.getStreamMessageType(),
                                    streamBuffer.toString(), false);
                                streamBuffer.setLength(0);  // 清空缓冲
                            }
                            tokenIndex[0]++;
                        }
                    }

                    // 收集工具调用片段
                    if (output != null && output.getToolCalls() != null) {
                        mergeToolCalls(output.getToolCalls(), toolCallAccumulators);
                    }

                    // 提取结束原因
                    if (generation != null && generation.getMetadata() != null
                        && StringUtils.isNotBlank(generation.getMetadata().getFinishReason())) {
                        finishReason[0] = generation.getMetadata().getFinishReason();
                    }

                    // 提取token用量
                    Integer usage = resolveTotalTokens(response != null ? response.getMetadata() : null);
                    if (usage != null) {
                        totalTokens[0] = usage;
                    }

                } catch (Exception e) {
                    future.completeExceptionally(e);  // 异常结束
                }
            },

            // === 流异常 ===
            future::completeExceptionally,

            // === 流完成 ===
            () -> {
                try {
                    // 发送剩余缓冲内容
                    if (pushToClient && messageId != null && streamBuffer.length() > 0) {
                        context.getPrinter().send(messageId, context.getStreamMessageType(),
                            streamBuffer.toString(), false);
                    }

                    // 构建最终工具调用列表
                    List<ToolCall> toolCalls = buildToolCalls(toolCallAccumulators);
                    String content = allContent.toString();

                    // 发送结束标记(带完整内容)
                    if (pushToClient && messageId != null && StringUtils.isNotBlank(content)) {
                        context.getPrinter().send(messageId, context.getStreamMessageType(),
                            content, true);  // true=结束
                    }

                    // 空响应校验
                    if (StringUtils.isBlank(content) && toolCalls.isEmpty()) {
                        future.completeExceptionally(
                            new IllegalArgumentException("Empty response from streaming LLM"));
                        return;
                    }

                    // 组装并返回最终结果
                    future.complete(LLM.ToolCallResponse.builder()
                        .content(StringUtils.isBlank(content) ? null : content)
                        .toolCalls(toolCalls)
                        .streamMessageId(messageId)
                        .finishReason(finishReason[0])
                        .totalTokens(totalTokens[0])
                        .duration(System.currentTimeMillis() - startTimeMs)
                        .build());

                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        );

        return future;
    }

    private boolean shouldPushStream(AgentContext context, boolean pushToClient) {
        if (!pushToClient) {
            return false;
        }
        return context != null
                && Boolean.TRUE.equals(context.getIsStream())
                && context.getPrinter() != null
                && StringUtils.isNotBlank(context.getStreamMessageType());
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

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private Integer resolveTotalTokens(ChatResponseMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        Usage usage = metadata.getUsage();
        return usage != null ? usage.getTotalTokens() : null;
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
            String key = StringUtils.defaultIfBlank(toolCall.id(), toolCall.name() + "#" + index);
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
}

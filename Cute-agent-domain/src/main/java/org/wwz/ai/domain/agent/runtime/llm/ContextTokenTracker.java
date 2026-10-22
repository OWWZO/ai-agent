package org.wwz.ai.domain.agent.runtime.llm;

import lombok.Builder;
import lombok.Value;
import org.wwz.ai.domain.agent.runtime.dto.Message;

import java.util.List;
import java.util.Objects;

/**
 * 单个 Agent 运行态的上下文 token tracker。
 * 不放入共享 {@code AgentRunState}，避免父/子 Agent 与 LTM fork 互相污染。
 */
public final class ContextTokenTracker {

    public static final String SOURCE_PROVIDER_USAGE = TokenCounter.SOURCE_PROVIDER_USAGE;
    public static final String SOURCE_LOCAL_ESTIMATE = TokenCounter.SOURCE_LOCAL_ESTIMATE;

    private final TokenCounter tokenCounter;

    private int lastProviderContextTokens;
    private int anchorMessageCount;
    private String promptShapeFingerprint;
    private boolean usageAvailable;

    public ContextTokenTracker() {
        this(new TokenCounter());
    }

    public ContextTokenTracker(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter == null ? new TokenCounter() : tokenCounter;
    }

    /**
     * 主 Agent 正式 askTool 响应写入 Memory 之后调用。
     * {@code anchorMessageCount} 指向 assistant 已入列后的位置。
     */
    public synchronized void recordProviderUsage(LlmUsageSnapshot usage,
                                                 int anchorAfterAssistant,
                                                 String fingerprint) {
        if (!isUsableUsage(usage)) {
            return;
        }
        int prompt = Math.max(0, usage.getPromptTokens());
        int completion = usage.getCompletionTokens() == null ? 0 : Math.max(0, usage.getCompletionTokens());
        // promptTokens 已含 cached，禁止再加 cachedPromptTokens
        this.lastProviderContextTokens = prompt + completion;
        this.anchorMessageCount = Math.max(0, anchorAfterAssistant);
        this.promptShapeFingerprint = fingerprint;
        this.usageAvailable = lastProviderContextTokens > 0;
    }

    public synchronized void reset() {
        lastProviderContextTokens = 0;
        anchorMessageCount = 0;
        promptShapeFingerprint = null;
        usageAvailable = false;
    }

    public synchronized Snapshot snapshot() {
        return Snapshot.builder()
                .usageAvailable(usageAvailable)
                .lastProviderContextTokens(lastProviderContextTokens)
                .anchorMessageCount(anchorMessageCount)
                .promptShapeFingerprint(promptShapeFingerprint)
                .build();
    }

    public ContextTokenEstimate estimateCurrentContext(PromptShape shape) {
        return estimateCurrent(snapshot(), shape, tokenCounter);
    }

    public static ContextTokenEstimate estimateCurrent(Snapshot snapshot,
                                                       PromptShape shape,
                                                       TokenCounter counter) {
        TokenCounter tokenCounter = counter == null ? new TokenCounter() : counter;
        TokenCounter.PromptEstimate local = tokenCounter.estimatePrompt(shape);
        if (snapshot != null
                && snapshot.isUsageAvailable()
                && snapshot.getLastProviderContextTokens() > 0
                && Objects.equals(snapshot.getPromptShapeFingerprint(), local.getPromptShapeFingerprint())) {
            List<Message> messages = shape == null ? List.of() : shape.getMessages();
            int size = messages == null ? 0 : messages.size();
            int anchor = Math.min(Math.max(snapshot.getAnchorMessageCount(), 0), size);
            List<Message> delta = (messages == null || anchor >= size)
                    ? List.of()
                    : messages.subList(anchor, size);
            int deltaTokens = tokenCounter.estimateMessageDelta(delta);
            int estimated = snapshot.getLastProviderContextTokens() + deltaTokens;
            return ContextTokenEstimate.builder()
                    .estimatedTokens(estimated)
                    .providerContextTokens(snapshot.getLastProviderContextTokens())
                    .deltaTokens(deltaTokens)
                    .systemTokens(local.getSystemTokens())
                    .messageTokens(local.getMessageTokens())
                    .toolTokens(local.getToolTokens())
                    .estimateSource(SOURCE_PROVIDER_USAGE)
                    .promptShapeFingerprint(local.getPromptShapeFingerprint())
                    .toolSchemaFingerprint(local.getToolSchemaFingerprint())
                    .build();
        }
        return ContextTokenEstimate.builder()
                .estimatedTokens(local.getEstimatedTotalTokens())
                .providerContextTokens(0)
                .deltaTokens(0)
                .systemTokens(local.getSystemTokens())
                .messageTokens(local.getMessageTokens())
                .toolTokens(local.getToolTokens())
                .estimateSource(SOURCE_LOCAL_ESTIMATE)
                .promptShapeFingerprint(local.getPromptShapeFingerprint())
                .toolSchemaFingerprint(local.getToolSchemaFingerprint())
                .build();
    }

    private static boolean isUsableUsage(LlmUsageSnapshot usage) {
        if (usage == null || usage.getPromptTokens() == null) {
            return false;
        }
        int prompt = Math.max(0, usage.getPromptTokens());
        int completion = usage.getCompletionTokens() == null ? 0 : Math.max(0, usage.getCompletionTokens());
        return prompt + completion > 0;
    }

    @Value
    @Builder
    public static class Snapshot {
        boolean usageAvailable;
        int lastProviderContextTokens;
        int anchorMessageCount;
        String promptShapeFingerprint;
    }

    @Value
    @Builder
    public static class ContextTokenEstimate {
        int estimatedTokens;
        int providerContextTokens;
        int deltaTokens;
        int systemTokens;
        int messageTokens;
        int toolTokens;
        String estimateSource;
        String promptShapeFingerprint;
        String toolSchemaFingerprint;

        public String toLogLine() {
            return "estimateSource=" + estimateSource
                    + " estimatedTokens=" + estimatedTokens
                    + " providerContextTokens=" + providerContextTokens
                    + " deltaTokens=" + deltaTokens
                    + " systemTokens=" + systemTokens
                    + " messageTokens=" + messageTokens
                    + " toolTokens=" + toolTokens;
        }
    }
}

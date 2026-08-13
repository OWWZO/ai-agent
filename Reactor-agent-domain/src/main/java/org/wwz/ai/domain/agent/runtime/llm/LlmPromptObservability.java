package org.wwz.ai.domain.agent.runtime.llm;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 请求观测：日志 + 轻量落库元数据（systemFingerprint / est tokens / cache）。
 * 不再落库 prompt_payload_json / tool_names / role_seq / obs_log_json。
 */
@Slf4j
public final class LlmPromptObservability {

    private static final TokenCounter TOKEN_COUNTER = new TokenCounter();
    private static final ConcurrentHashMap<String, PromptSnapshot> LAST_BY_SESSION = new ConcurrentHashMap<>();
    private static final ThreadLocal<ObservationBundle> LAST_BUNDLE = new ThreadLocal<>();

    private LlmPromptObservability() {
    }

    public static ObservationBundle logRequest(AgentContext context,
                                               String model,
                                               String callKind,
                                               Message systemMessage,
                                               List<Message> messages,
                                               ToolCollection tools) {
        TokenCounter.PromptEstimate estimate = TOKEN_COUNTER.estimatePrompt(systemMessage, messages, tools);
        String requestId = context == null ? "-" : StringUtils.defaultString(context.getRequestId(), "-");
        String sessionId = context == null ? "-" : StringUtils.defaultString(context.getSessionId(), "-");
        String roleSeq = TOKEN_COUNTER.summarizeRoles(messages);

        String reqLine = "[LLM-REQ] kind=" + callKind + " model=" + model + " session=" + sessionId + " " + estimate.toLogLine();
        String roleLine = "[LLM-REQ] roleSeq=[" + roleSeq + "] messageCount=" + estimate.getMessageCount();
        log.info("{} {}", requestId, reqLine);
        log.info("{} {}", requestId, roleLine);

        PromptSnapshot prev = LAST_BY_SESSION.get(sessionId);
        PromptSnapshot curr = new PromptSnapshot(
                estimate.getSystemFingerprint(),
                String.join(",", estimate.getToolNames() == null ? List.of() : estimate.getToolNames()),
                estimate.getMessageCount(),
                estimate.getSystemChars()
        );

        String cacheStatus = "UNKNOWN";
        String cacheRiskFlags = null;
        String cacheLine;
        if (prev != null && !"-".equals(sessionId)) {
            boolean systemChanged = !Objects.equals(prev.systemFingerprint(), curr.systemFingerprint());
            boolean toolsChanged = !Objects.equals(prev.toolNamesKey(), curr.toolNamesKey());
            boolean messagesShrunk = curr.messageCount() + 1 < prev.messageCount();
            if (systemChanged || toolsChanged || messagesShrunk) {
                cacheStatus = "RISK";
                List<String> flags = new ArrayList<>();
                if (systemChanged) {
                    flags.add("systemChanged");
                }
                if (toolsChanged) {
                    flags.add("toolsChanged");
                }
                if (messagesShrunk) {
                    flags.add("messagesShrunk");
                }
                cacheRiskFlags = String.join(",", flags);
                cacheLine = "[PROMPT-CACHE-RISK] session=" + sessionId
                        + " systemChanged=" + systemChanged
                        + " toolsChanged=" + toolsChanged
                        + " messagesShrunk=" + messagesShrunk
                        + " prev(systemFp=" + prev.systemFingerprint()
                        + ",tools=" + prev.toolNamesKey()
                        + ",msgN=" + prev.messageCount()
                        + ") curr(systemFp=" + curr.systemFingerprint()
                        + ",tools=" + curr.toolNamesKey()
                        + ",msgN=" + curr.messageCount() + ")";
                log.warn("{} {}", requestId, cacheLine);
            } else {
                cacheStatus = "OK";
                cacheLine = "[PROMPT-CACHE-OK] session=" + sessionId
                        + " systemFp=" + curr.systemFingerprint()
                        + " tools=" + curr.toolNamesKey()
                        + " msgN=" + curr.messageCount();
                log.info("{} {}", requestId, cacheLine);
            }
        } else {
            cacheLine = "[PROMPT-CACHE-OK] session=" + sessionId + " firstSeen=true systemFp=" + curr.systemFingerprint();
            cacheStatus = "OK";
            log.info("{} {}", requestId, cacheLine);
        }
        if (!"-".equals(sessionId)) {
            LAST_BY_SESSION.put(sessionId, curr);
        }

        List<String> lines = new ArrayList<>();
        lines.add(reqLine);
        lines.add(roleLine);
        lines.add(cacheLine);

        ObservationBundle bundle = ObservationBundle.builder()
                .estimate(estimate)
                .systemFingerprint(estimate.getSystemFingerprint())
                .cacheStatus(cacheStatus)
                .cacheRiskFlags(cacheRiskFlags)
                .obsLines(lines)
                .build();
        LAST_BUNDLE.set(bundle);
        return bundle;
    }

    public static void logResponse(AgentContext context,
                                   String model,
                                   String callKind,
                                   Integer promptTokens,
                                   Integer completionTokens,
                                   Integer totalTokens,
                                   Integer cachedPromptTokens,
                                   long durationMs) {
        logResponse(context, model, callKind, LlmUsageSnapshot.builder()
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .cachedPromptTokens(cachedPromptTokens)
                .build(), durationMs);
    }

    public static void logResponse(AgentContext context,
                                   String model,
                                   String callKind,
                                   LlmUsageSnapshot usage,
                                   long durationMs) {
        LlmUsageSnapshot snapshot = usage == null ? LlmUsageSnapshot.empty() : usage;
        ObservationBundle bundle = LAST_BUNDLE.get();
        TokenCounter.PromptEstimate estimate = bundle == null ? null : bundle.getEstimate();
        String requestId = context == null ? "-" : StringUtils.defaultString(context.getRequestId(), "-");
        int est = estimate == null ? -1 : estimate.getEstimatedTotalTokens();
        Integer promptTokens = snapshot.getPromptTokens();
        Integer completionTokens = snapshot.getCompletionTokens();
        Integer totalTokens = snapshot.getTotalTokens();
        Integer cachedPromptTokens = snapshot.getCachedPromptTokens();
        Integer uncached = null;
        if (promptTokens != null && cachedPromptTokens != null) {
            uncached = Math.max(0, promptTokens - cachedPromptTokens);
        }
        double cacheHitRatio = -1;
        if (promptTokens != null && promptTokens > 0 && cachedPromptTokens != null) {
            cacheHitRatio = cachedPromptTokens * 1.0 / promptTokens;
        }
        String respLine = "[LLM-RESP] kind=" + callKind
                + " model=" + model
                + " durationMs=" + durationMs
                + " usage(prompt=" + promptTokens
                + ",completion=" + completionTokens
                + ",total=" + totalTokens
                + ",cachedPrompt=" + cachedPromptTokens
                + ",uncachedPrompt=" + uncached
                + ",promptText=" + snapshot.getPromptTextTokens()
                + ",promptAudio=" + snapshot.getPromptAudioTokens()
                + ",promptImage=" + snapshot.getPromptImageTokens()
                + ",completionText=" + snapshot.getCompletionTextTokens()
                + ",completionAudio=" + snapshot.getCompletionAudioTokens()
                + ",reasoning=" + snapshot.getReasoningTokens()
                + ",cacheHitRatio=" + (cacheHitRatio < 0 ? "n/a" : String.format("%.2f", cacheHitRatio))
                + ") estPromptTotal~" + est
                + " estDelta=" + ((promptTokens == null || est < 0) ? "n/a" : (promptTokens - est));
        log.info("{} {}", requestId, respLine);

        String missLine = null;
        String cacheStatus = bundle == null ? "UNKNOWN" : bundle.getCacheStatus();
        String cacheRiskFlags = bundle == null ? null : bundle.getCacheRiskFlags();
        if (cachedPromptTokens != null && cachedPromptTokens == 0 && promptTokens != null && promptTokens > 500) {
            missLine = "[PROMPT-CACHE-MISS?] cachedPrompt=0 promptTokens=" + promptTokens;
            log.warn("{} {}", requestId, missLine);
            if (!"RISK".equals(cacheStatus)) {
                cacheStatus = "MISS";
            } else if (cacheRiskFlags == null || !cacheRiskFlags.contains("cachedZero")) {
                cacheRiskFlags = cacheRiskFlags == null ? "cachedZero" : cacheRiskFlags + ",cachedZero";
            }
        }

        if (bundle != null) {
            List<String> lines = new ArrayList<>(bundle.getObsLines() == null ? List.of() : bundle.getObsLines());
            lines.add(respLine);
            if (missLine != null) {
                lines.add(missLine);
            }
            bundle.setCacheStatus(cacheStatus);
            bundle.setCacheRiskFlags(cacheRiskFlags);
            bundle.setUsage(snapshot);
            bundle.setCachedPromptTokens(cachedPromptTokens);
            bundle.setObsLines(lines);
        }
        // keep bundle for finishLlmInvocation to read via current()
    }

    public static ObservationBundle current() {
        return LAST_BUNDLE.get();
    }

    public static void restore(ObservationBundle bundle) {
        if (bundle == null) {
            return;
        }
        LAST_BUNDLE.set(bundle);
    }

    public static void clear() {
        LAST_BUNDLE.remove();
    }

    public static LlmUsageSnapshot resolveUsage(ChatResponseMetadata metadata) {
        return LlmUsageSnapshot.resolve(metadata);
    }

    public static Integer resolveCachedPromptTokens(ChatResponseMetadata metadata) {
        return resolveUsage(metadata).getCachedPromptTokens();
    }

    private record PromptSnapshot(String systemFingerprint, String toolNamesKey, int messageCount, int systemChars) {
    }

    @Data
    @Builder
    public static class ObservationBundle {
        private TokenCounter.PromptEstimate estimate;
        private String systemFingerprint;
        private String cacheStatus;
        private String cacheRiskFlags;
        private LlmUsageSnapshot usage;
        private Integer cachedPromptTokens;
        private List<String> obsLines;
    }
}

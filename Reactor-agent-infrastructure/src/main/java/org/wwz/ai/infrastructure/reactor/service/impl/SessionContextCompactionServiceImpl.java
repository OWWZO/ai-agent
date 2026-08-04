package org.wwz.ai.infrastructure.reactor.service.impl;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.memory.CompactionBudget;
import org.wwz.ai.domain.agent.memory.CompactionPrompt;
import org.wwz.ai.domain.agent.memory.SessionContextCompactionService;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.memory.WorkingMemoryCompactionEvent;
import org.wwz.ai.domain.agent.memory.WorkingMemoryCompactor;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.memory.ltm.LtmManager;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwnerResolver;
import org.wwz.ai.domain.agent.memory.ltm.MemoryFlushPolicy;
import org.wwz.ai.domain.agent.memory.ltm.LtmServices;
import org.wwz.ai.domain.agent.memory.ltm.MemoryFlushService;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.printer.LogPrinter;
import org.wwz.ai.infrastructure.dao.reactor.IWorkingMemoryCompactionDao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工作记忆压缩流水线（对齐 cc-haha query.ts）：
 * <pre>
 *   microcompact → (若仍超阈)
 *     session-memory compact（已有 notes + recent tail）
 *     → full LLM compact
 *     → drop-oldest 兜底
 * </pre>
 * 只改 hydrate 投影；ledger 不变。
 * 每次有效压缩写入 ai_agent_working_memory_compaction 审计表。
 */
@Slf4j
@Service
public class SessionContextCompactionServiceImpl implements SessionContextCompactionService {

    private static final String COMPACT_REQUEST_PREFIX = "wm-compact-";
    private static final int MAX_AUDIT_JSON_CHARS = 500_000;

    private final ObjectProvider<ReactorRuntimeDependencies> runtimeDependenciesProvider;
    private final SessionWorkingMemoryService sessionWorkingMemoryService;
    private final IWorkingMemoryCompactionDao workingMemoryCompactionDao;
    private final WorkingMemoryCompactor compactor = new WorkingMemoryCompactor();
    private final ConcurrentHashMap<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();

    private final boolean enabled;
    private final boolean llmEnabled;
    private final boolean microEnabled;
    private final boolean sessionMemoryEnabled;
    private final int bufferTokens;
    private final int maxOutputReserve;
    private final int keepMinTokens;
    private final int keepMinTextMessages;
    private final int keepMaxTokens;
    private final int maxConsecutiveFailures;
    private final double temperature;
    private final int messageContentCharLimit;
    private final int microKeepRecentToolResults;
    private final int microToolResultMaxChars;
    private final boolean persistProjection;
    private final boolean auditEnabled;
    private final boolean auditStoreMessages;
    private final boolean midRunEnabled;

    public SessionContextCompactionServiceImpl(
            ObjectProvider<ReactorRuntimeDependencies> runtimeDependenciesProvider,
            SessionWorkingMemoryService sessionWorkingMemoryService,
            IWorkingMemoryCompactionDao workingMemoryCompactionDao,
            @Value("${autobots.autoagent.compaction.enabled:true}") boolean enabled,
            @Value("${autobots.autoagent.compaction.llm-enabled:true}") boolean llmEnabled,
            @Value("${autobots.autoagent.compaction.micro-enabled:true}") boolean microEnabled,
            @Value("${autobots.autoagent.compaction.session-memory-enabled:true}") boolean sessionMemoryEnabled,
            @Value("${autobots.autoagent.compaction.buffer-tokens:13000}") int bufferTokens,
            @Value("${autobots.autoagent.compaction.max-output-reserve:20000}") int maxOutputReserve,
            @Value("${autobots.autoagent.compaction.keep-min-tokens:10000}") int keepMinTokens,
            @Value("${autobots.autoagent.compaction.keep-min-text-messages:5}") int keepMinTextMessages,
            @Value("${autobots.autoagent.compaction.keep-max-tokens:40000}") int keepMaxTokens,
            @Value("${autobots.autoagent.compaction.max-consecutive-failures:3}") int maxConsecutiveFailures,
            @Value("${autobots.autoagent.compaction.temperature:0.2}") double temperature,
            @Value("${autobots.autoagent.compaction.message-content-char-limit:4000}") int messageContentCharLimit,
            @Value("${autobots.autoagent.compaction.micro-keep-recent-tool-results:5}") int microKeepRecentToolResults,
            @Value("${autobots.autoagent.compaction.micro-tool-result-max-chars:8000}") int microToolResultMaxChars,
            @Value("${autobots.autoagent.compaction.persist-projection:true}") boolean persistProjection,
            @Value("${autobots.autoagent.compaction.audit-enabled:true}") boolean auditEnabled,
            @Value("${autobots.autoagent.compaction.audit-store-messages:true}") boolean auditStoreMessages,
            @Value("${autobots.autoagent.compaction.mid-run-enabled:true}") boolean midRunEnabled) {
        this.runtimeDependenciesProvider = runtimeDependenciesProvider;
        this.sessionWorkingMemoryService = sessionWorkingMemoryService;
        this.workingMemoryCompactionDao = workingMemoryCompactionDao;
        this.enabled = enabled;
        this.llmEnabled = llmEnabled;
        this.microEnabled = microEnabled;
        this.sessionMemoryEnabled = sessionMemoryEnabled;
        this.bufferTokens = bufferTokens;
        this.maxOutputReserve = maxOutputReserve;
        this.keepMinTokens = keepMinTokens;
        this.keepMinTextMessages = keepMinTextMessages;
        this.keepMaxTokens = keepMaxTokens;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.temperature = temperature;
        this.messageContentCharLimit = messageContentCharLimit;
        this.microKeepRecentToolResults = microKeepRecentToolResults;
        this.microToolResultMaxChars = microToolResultMaxChars;
        this.persistProjection = persistProjection;
        this.auditEnabled = auditEnabled;
        this.auditStoreMessages = auditStoreMessages;
        this.midRunEnabled = midRunEnabled;
    }
    private ReactorRuntimeDependencies runtimeDependencies() {
        return runtimeDependenciesProvider.getObject();
    }


    @Override
    public List<Message> applyIfNeeded(String sessionId, String requestId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : messages;
        }
        // 防止 compact 自己的 LLM 调用再触发压缩（对齐 cc-haha querySource=compact）
        if (StringUtils.isNotBlank(requestId) && requestId.contains("-compact")) {
            return messages;
        }
        CompactionBudget budget = resolveBudget();
        if (!budget.isEnabled()) {
            return messages;
        }

        int originalTokens = compactor.estimateTokens(messages);
        List<Message> current = messages;

        if (budget.isMicroEnabled()) {
            List<Message> microed = compactor.microcompact(current, budget);
            if (microed != current && !microed.equals(current)) {
                int afterMicro = compactor.estimateTokens(microed);
                log.info("microcompact sessionId={} before={} after={} msgs={}",
                        sessionId, originalTokens, afterMicro, microed.size());
                current = microed;
            }
        }

        if (!compactor.shouldCompact(current, budget)) {
            String microCompactId = maybePersistIfChanged(sessionId, requestId, messages, current, originalTokens, "micro-only", budget);
            if (current != messages && !current.equals(messages)) {
                recordCompactionEvent(sessionId, requestId, messages, current, originalTokens, "micro-only", budget, null, microCompactId);
            }
            return current;
        }

        // 即将重压缩：独立 Memory Flush 小回合 + Provider onPreCompress（失败不阻断）
        current = applyLtmPreCompactHooks(sessionId, requestId, current);

        int beforeAuto = compactor.estimateTokens(current);
        log.info("auto-compact triggered sessionId={} requestId={} tokens={} threshold={}",
                sessionId, requestId, beforeAuto, budget.threshold());

        List<Message> compacted = null;
        String strategy = null;
        String error = null;

        if (budget.isSessionMemoryEnabled()) {
            String notes = compactor.extractSessionNotes(current);
            List<Message> sm = compactor.trySessionMemoryCompact(current, notes, budget);
            if (sm != null) {
                compacted = sm;
                strategy = "session-memory";
                clearFailures(sessionId);
                log.info("session-memory compact sessionId={} before={} after={} msgs={}",
                        sessionId, beforeAuto, compactor.estimateTokens(sm), sm.size());
            }
        }

        if (compacted == null && budget.isLlmEnabled() && !isCircuitOpen(sessionId, budget)) {
            try {
                List<Message> full = fullCompact(sessionId, requestId, current, budget);
                int after = compactor.estimateTokens(full);
                if (after >= budget.threshold()) {
                    log.warn("full compact still over threshold sessionId={} after={} threshold={}",
                            sessionId, after, budget.threshold());
                    recordFailure(sessionId);
                    error = "full compact still over threshold: " + after;
                } else {
                    compacted = full;
                    strategy = "full-llm";
                    clearFailures(sessionId);
                }
            } catch (Exception e) {
                log.warn("full compact failed sessionId={} requestId={}: {}",
                        sessionId, requestId, e.getMessage());
                recordFailure(sessionId);
                error = e.getMessage();
            }
        }

        if (compacted == null) {
            compacted = compactor.dropOldestToFit(current, budget);
            strategy = "drop-oldest";
            log.info("drop-oldest sessionId={} beforeMsgs={} afterMsgs={} afterTokens={}",
                    sessionId, current.size(), compacted.size(), compactor.estimateTokens(compacted));
        }

        String compactRequestId = maybePersistIfChanged(sessionId, requestId, messages, compacted, originalTokens, strategy, budget);
        recordCompactionEvent(sessionId, requestId, messages, compacted, originalTokens, strategy, budget, error, compactRequestId);
        List<Message> result = compacted == null ? current : compacted;
        // 压缩后提醒：可用 memory / session_search 找回耐久事实与账本细节
        return MemoryFlushPolicy.prependPostCompactReminder(result);
    }

    /**
     * ① 独立 Memory Flush 小回合（写 curated）
     * ② Provider onPreCompress insight
     * 失败均不阻断后续压缩。
     */
    private List<Message> applyLtmPreCompactHooks(String sessionId, String requestId, List<Message> current) {
        List<Message> working = current;
        try {
            ReactorRuntimeDependencies deps = runtimeDependencies();
            if (deps == null) {
                return working;
            }
            // ① 独立 flush 小回合（对齐 Hermes）；优先运行时绑定
            MemoryFlushService flushService = LtmServices.memoryFlush();
            if (flushService == null) {
                flushService = deps.getOptionalMemoryFlushService();
            }
            if (flushService != null) {
                int applied = flushService.flushBeforeCompact(sessionId, requestId, null, working);
                if (applied > 0) {
                    log.info("memory-flush wrote {} curated entries before compact sessionId={}",
                            applied, sessionId);
                }
            } else {
                int userTurns = MemoryFlushPolicy.countUserTurns(working);
                if (MemoryFlushPolicy.shouldFlush(userTurns, deps.resolveLtmFlushMinTurns(), true)) {
                    working = MemoryFlushPolicy.prependFlushNudge(working);
                }
            }
            // ② Provider insight
            LtmManager ltmManager = deps.getOptionalLtmManager();
            if (ltmManager != null) {
                List<Map<String, Object>> payload = new ArrayList<>();
                for (Message message : working) {
                    if (message == null) {
                        continue;
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("role", message.getRole() == null ? null : message.getRole().name());
                    row.put("content", message.getContent());
                    payload.add(row);
                }
                String insight = ltmManager.onPreCompress(payload);
                if (StringUtils.isNotBlank(insight)) {
                    List<Message> withInsight = new ArrayList<>(working.size() + 1);
                    withInsight.add(Message.userMessage(
                            "[memory-pre-compress] " + insight.trim(), null));
                    withInsight.addAll(working);
                    working = withInsight;
                }
            }
        } catch (Exception e) {
            log.warn("LTM pre-compact hooks failed sessionId={}: {}", sessionId, e.toString());
        }
        return working;
    }

    @Override
    public List<Message> applyIfNeededMidRun(String sessionId, String requestId, List<Message> messages) {
        if (!midRunEnabled) {
            return messages == null ? List.of() : messages;
        }
        return applyIfNeeded(sessionId, requestId, messages);
    }

    private List<Message> fullCompact(String sessionId,
                                      String requestId,
                                      List<Message> messages,
                                      CompactionBudget budget) throws Exception {
        List<Message> body = messages;
        String existingNotes = null;
        if (compactor.isCompactSummaryMessage(messages.get(0))) {
            existingNotes = messages.get(0).getContent();
            body = messages.size() > 1 ? messages.subList(1, messages.size()) : List.of();
        }

        List<Message> toKeep = compactor.keepRecentTail(body, budget);
        int keepStart = body.size() - toKeep.size();
        List<Message> toSummarize;
        if (keepStart > 0 && keepStart < body.size()) {
            toSummarize = body.subList(0, keepStart);
        } else {
            toSummarize = body;
            toKeep = List.of();
        }
        if (toSummarize.isEmpty() && StringUtils.isNotBlank(existingNotes)) {
            return compactor.buildPostCompactMessages(existingNotes, toKeep);
        }
        if (toSummarize.isEmpty()) {
            return messages;
        }

        List<Message> prepared = compactor.prepareMessagesForSummarizer(toSummarize, budget.getMessageContentCharLimit());
        if (StringUtils.isNotBlank(existingNotes)) {
            List<Message> withNotes = new ArrayList<>();
            withNotes.add(Message.userMessage(
                    "Previous session summary (may be incomplete; merge and refresh):\n" + existingNotes, null));
            withNotes.addAll(prepared);
            prepared = withNotes;
        }

        String modelName = resolveCompactModelName();
        LLM llm = new LLM(modelName, "", runtimeDependencies());

        AgentRequest fakeRequest = new AgentRequest();
        fakeRequest.setRequestId(StringUtils.defaultIfBlank(requestId, "compact") + "-compact");
        fakeRequest.setSessionId(sessionId);

        AgentContext context = AgentContext.builder()
                .requestId(fakeRequest.getRequestId())
                .sessionId(sessionId)
                .query("session-context-compaction")
                .isStream(false)
                .runtimeDependencies(runtimeDependencies())
                .printer(new LogPrinter(fakeRequest))
                .build();
        context.markExecutionPosition("compaction", null);

        Message system = Message.systemMessage(CompactionPrompt.getCompactPrompt(), null);
        List<Message> askMessages = new ArrayList<>(prepared);
        askMessages.add(Message.userMessage(
                "Please provide the conversation summary now, following the required <analysis> and <summary> structure.",
                null));

        String raw = llm.ask(
                context,
                askMessages,
                List.of(system),
                false,
                false,
                budget.getTemperature(),
                ExecutionLedgerConstants.CALL_KIND_INTERNAL_COMPACT
        ).get();

        String formatted = CompactionPrompt.formatCompactSummary(raw);
        if (StringUtils.isBlank(formatted)) {
            throw new IllegalStateException("empty compact summary");
        }
        String reinject = CompactionPrompt.wrapSummaryForReinject(formatted, !toKeep.isEmpty());
        List<Message> post = compactor.buildPostCompactMessages(reinject, toKeep);
        log.info("full-llm compact sessionId={} summarizeMsgs={} keepMsgs={} postMsgs={} postTokens={}",
                sessionId, toSummarize.size(), toKeep.size(), post.size(), compactor.estimateTokens(post));
        return post;
    }

    private CompactionBudget resolveBudget() {
        int contextWindow = CompactionBudget.DEFAULT_CONTEXT_WINDOW;
        int maxOutput = CompactionBudget.DEFAULT_MAX_OUTPUT_TOKENS;
        try {
            ReactorConfig config = runtimeDependencies().requireReactorConfig();
            String modelName = resolveCompactModelName(config);
            LLMSettings settings = runtimeDependencies().resolveLlmSettings(modelName);
            if (settings != null) {
                if (settings.getMaxInputTokens() > 0) {
                    contextWindow = settings.getMaxInputTokens();
                }
                if (settings.getMaxTokens() > 0) {
                    maxOutput = settings.getMaxTokens();
                }
            }
        } catch (Exception e) {
            log.debug("resolve compact budget fallback defaults: {}", e.getMessage());
        }
        return CompactionBudget.builder()
                .enabled(enabled)
                .llmEnabled(llmEnabled)
                .microEnabled(microEnabled)
                .sessionMemoryEnabled(sessionMemoryEnabled)
                .contextWindow(contextWindow)
                .maxOutputTokens(maxOutput)
                .bufferTokens(bufferTokens)
                .maxOutputReserve(maxOutputReserve)
                .keepMinTokens(keepMinTokens)
                .keepMinTextMessages(keepMinTextMessages)
                .keepMaxTokens(keepMaxTokens)
                .maxConsecutiveFailures(maxConsecutiveFailures)
                .temperature(temperature)
                .messageContentCharLimit(messageContentCharLimit)
                .microKeepRecentToolResults(microKeepRecentToolResults)
                .microToolResultMaxChars(microToolResultMaxChars)
                .build();
    }

    private String resolveCompactModelName() {
        try {
            return resolveCompactModelName(runtimeDependencies().requireReactorConfig());
        } catch (Exception e) {
            return "";
        }
    }

    private String resolveCompactModelName(ReactorConfig config) {
        if (config == null) {
            return "";
        }
        if (StringUtils.isNotBlank(config.getSummaryModelName())) {
            return config.getSummaryModelName().trim();
        }
        if (StringUtils.isNotBlank(config.getReactModelName())) {
            return config.getReactModelName().trim();
        }
        return StringUtils.defaultString(config.getPlannerModelName());
    }

    private boolean isCircuitOpen(String sessionId, CompactionBudget budget) {
        if (StringUtils.isBlank(sessionId)) {
            return false;
        }
        AtomicInteger failures = consecutiveFailures.get(sessionId);
        return failures != null && failures.get() >= budget.getMaxConsecutiveFailures();
    }

    private void recordFailure(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        consecutiveFailures.computeIfAbsent(sessionId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private void clearFailures(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        consecutiveFailures.remove(sessionId);
    }

    private String maybePersistIfChanged(String sessionId,
                                         String requestId,
                                         List<Message> original,
                                         List<Message> result,
                                         int originalTokens,
                                         String strategy,
                                         CompactionBudget budget) {
        if (!persistProjection || result == null || result.isEmpty()) {
            return null;
        }
        int after = compactor.estimateTokens(result);
        boolean structural = result.size() < original.size()
                || compactor.isCompactSummaryMessage(result.get(0));
        boolean tokenSaved = after < originalTokens;
        if (!structural && !tokenSaved) {
            return null;
        }
        if ("micro-only".equals(strategy) && !tokenSaved) {
            return null;
        }
        String compactRequestId = tryPersist(sessionId, requestId, result);
        log.info("persist compacted projection sessionId={} strategy={} tokens {}->{} compactRequestId={}",
                sessionId, strategy, originalTokens, after, compactRequestId);
        return compactRequestId;
    }

    private String tryPersist(String sessionId, String requestId, List<Message> compacted) {
        if (sessionWorkingMemoryService == null || StringUtils.isBlank(sessionId) || compacted == null || compacted.isEmpty()) {
            return null;
        }
        try {
            String compactRequestId = COMPACT_REQUEST_PREFIX + StringUtils.defaultIfBlank(requestId, "anon")
                    + "-" + System.currentTimeMillis();
            sessionWorkingMemoryService.replaceReadyProjection(sessionId, compactRequestId, compacted);
            return compactRequestId;
        } catch (Exception e) {
            log.warn("persist compacted working memory failed sessionId={}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    private void recordCompactionEvent(String sessionId,
                                       String requestId,
                                       List<Message> before,
                                       List<Message> after,
                                       int beforeTokens,
                                       String strategy,
                                       CompactionBudget budget,
                                       String errorMessage,
                                       String compactRequestId) {
        if (!auditEnabled || workingMemoryCompactionDao == null || StringUtils.isBlank(sessionId)) {
            return;
        }
        if (after == null || after.isEmpty()) {
            return;
        }
        int afterTokens = compactor.estimateTokens(after);
        if (afterTokens >= beforeTokens && !"full-llm".equals(strategy) && !"session-memory".equals(strategy)
                && !"drop-oldest".equals(strategy) && !"micro-only".equals(strategy)) {
            return;
        }
        try {
            String summary = null;
            if (!after.isEmpty() && compactor.isCompactSummaryMessage(after.get(0))) {
                summary = after.get(0).getContent();
            }
            WorkingMemoryCompactionEvent event = WorkingMemoryCompactionEvent.builder()
                    .sessionId(sessionId)
                    .triggerRequestId(StringUtils.defaultIfBlank(requestId, "unknown"))
                    .compactRequestId(compactRequestId)
                    .strategy(StringUtils.defaultIfBlank(strategy, "unknown"))
                    .status(StringUtils.isBlank(errorMessage) || afterTokens < beforeTokens
                            ? WorkingMemoryCompactionEvent.STATUS_SUCCESS
                            : WorkingMemoryCompactionEvent.STATUS_FAILED)
                    .beforeTokens(beforeTokens)
                    .afterTokens(afterTokens)
                    .beforeMessageCount(before == null ? 0 : before.size())
                    .afterMessageCount(after.size())
                    .thresholdTokens(budget == null ? 0 : budget.threshold())
                    .summaryText(summary)
                    .beforeMessagesJson(auditStoreMessages ? toAuditJson(before) : null)
                    .afterMessagesJson(auditStoreMessages ? toAuditJson(after) : null)
                    .errorMessage(StringUtils.left(errorMessage, 1000))
                    .deleted(0)
                    .build();
            workingMemoryCompactionDao.insertEvent(event);
        } catch (Exception e) {
            log.warn("record compaction event failed sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    private String toAuditJson(List<Message> messages) {
        if (messages == null) {
            return null;
        }
        try {
            // 审计快照去掉 base64 图片，避免撑爆表
            List<Message> slim = new ArrayList<>(messages.size());
            for (Message m : messages) {
                if (m == null) {
                    continue;
                }
                slim.add(Message.builder()
                        .role(m.getRole())
                        .content(m.getContent())
                        .toolCallId(m.getToolCallId())
                        .toolCalls(m.getToolCalls())
                        .base64Image(StringUtils.isNotBlank(m.getBase64Image()) ? "[image-omitted]" : null)
                        .build());
            }
            String json = JSON.toJSONString(slim);
            if (json != null && json.length() > MAX_AUDIT_JSON_CHARS) {
                return json.substring(0, MAX_AUDIT_JSON_CHARS) + "...[truncated]";
            }
            return json;
        } catch (Exception e) {
            return null;
        }
    }
}


package org.wwz.ai.infrastructure.reactor.service.impl;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.memory.CompactionBudget;
import org.wwz.ai.domain.agent.memory.CompactionPrompt;
import org.wwz.ai.domain.agent.memory.SessionContextCompactionService;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.memory.WorkingMemoryCompactionEvent;
import org.wwz.ai.domain.agent.memory.WorkingMemoryCompactor;
import org.wwz.ai.domain.agent.memory.WorkingMemoryScopes;
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
import org.wwz.ai.domain.agent.runtime.llm.ContextTokenTracker;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.llm.PromptShape;
import org.wwz.ai.domain.agent.runtime.llm.TokenCounter;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.printer.LogPrinter;
import org.wwz.ai.infrastructure.dao.reactor.IWorkingMemoryCompactionDao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

/**
 * 工作记忆压缩流水线：
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

    public static final String STRATEGY_FULL_LLM = "full-llm";
    public static final String STRATEGY_LOCAL_FALLBACK = "local-fallback";
    public static final String STRATEGY_ABORT_UNCHANGED = "abort-unchanged";
    public static final String STRATEGY_DROP_OLDEST = "drop-oldest";

    private static final String COMPACT_REQUEST_PREFIX = "wm-compact-";
    private static final int MAX_AUDIT_JSON_CHARS = 500_000;
    private static final ThreadLocal<LtmFlushParity> FLUSH_PARITY = new ThreadLocal<>();
    private static final ThreadLocal<PromptShape> PROMPT_SHAPE = new ThreadLocal<>();
    private static final ThreadLocal<ContextTokenTracker.Snapshot> TOKEN_SNAPSHOT = new ThreadLocal<>();

    private final ObjectProvider<ReactorRuntimeDependencies> runtimeDependenciesProvider;
    private final SessionWorkingMemoryService sessionWorkingMemoryService;
    private final IWorkingMemoryCompactionDao workingMemoryCompactionDao;
    private final WorkingMemoryCompactor compactor = new WorkingMemoryCompactor();
    private final ConcurrentHashMap<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();

    private final boolean enabled;
    private final boolean llmEnabled;
    private final boolean microEnabled;
    private final boolean sessionMemoryEnabled;
    private final double thresholdPercent;
    private final int keepMinTokens;
    private final int keepMinTextMessages;
    private final int keepMaxTokens;
    private final int maxConsecutiveFailures;
    private final double temperature;
    private final int messageContentCharLimit;
    private final int microKeepRecentToolResults;
    private final int microToolResultMaxChars;
    private final double summaryTargetRatio;
    private final int protectFirstN;
    private final int protectLastN;
    private final int contentMaxChars;
    private final int contentHeadChars;
    private final int contentTailChars;
    private final int summaryInputMaxChars;
    private final int summarizerTimeoutSeconds;
    private final boolean persistProjection;
    private final boolean auditEnabled;
    private final boolean auditStoreMessages;
    private final boolean midRunEnabled;

    @Autowired
    public SessionContextCompactionServiceImpl(
            ObjectProvider<ReactorRuntimeDependencies> runtimeDependenciesProvider,
            SessionWorkingMemoryService sessionWorkingMemoryService,
            IWorkingMemoryCompactionDao workingMemoryCompactionDao,
            @Value("${autobots.autoagent.compaction.enabled:true}") boolean enabled,
            @Value("${autobots.autoagent.compaction.llm-enabled:true}") boolean llmEnabled,
            @Value("${autobots.autoagent.compaction.micro-enabled:true}") boolean microEnabled,
            @Value("${autobots.autoagent.compaction.session-memory-enabled:false}") boolean sessionMemoryEnabled,
            @Value("${autobots.autoagent.compaction.threshold-percent:0.50}") double thresholdPercent,
            @Value("${autobots.autoagent.compaction.keep-min-tokens:10000}") int keepMinTokens,
            @Value("${autobots.autoagent.compaction.keep-min-text-messages:5}") int keepMinTextMessages,
            @Value("${autobots.autoagent.compaction.keep-max-tokens:40000}") int keepMaxTokens,
            @Value("${autobots.autoagent.compaction.max-consecutive-failures:3}") int maxConsecutiveFailures,
            @Value("${autobots.autoagent.compaction.temperature:0.2}") double temperature,
            @Value("${autobots.autoagent.compaction.message-content-char-limit:4000}") int messageContentCharLimit,
            @Value("${autobots.autoagent.compaction.micro-keep-recent-tool-results:5}") int microKeepRecentToolResults,
             @Value("${autobots.autoagent.compaction.micro-tool-result-max-chars:8000}") int microToolResultMaxChars,
             @Value("${autobots.autoagent.compaction.summary-target-ratio:0.20}") double summaryTargetRatio,
             @Value("${autobots.autoagent.compaction.protect-first-n:3}") int protectFirstN,
             @Value("${autobots.autoagent.compaction.protect-last-n:8}") int protectLastN,
             @Value("${autobots.autoagent.compaction.content-max-chars:6000}") int contentMaxChars,
             @Value("${autobots.autoagent.compaction.content-head-chars:4000}") int contentHeadChars,
             @Value("${autobots.autoagent.compaction.content-tail-chars:1500}") int contentTailChars,
             @Value("${autobots.autoagent.compaction.summary-input-max-chars:160000}") int summaryInputMaxChars,
             @Value("${autobots.autoagent.compaction.summarizer-timeout-seconds:120}") int summarizerTimeoutSeconds,
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
        this.thresholdPercent = thresholdPercent;
        this.keepMinTokens = keepMinTokens;
        this.keepMinTextMessages = keepMinTextMessages;
        this.keepMaxTokens = keepMaxTokens;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.temperature = temperature;
        this.messageContentCharLimit = messageContentCharLimit;
        this.microKeepRecentToolResults = microKeepRecentToolResults;
        this.microToolResultMaxChars = microToolResultMaxChars;
        this.summaryTargetRatio = summaryTargetRatio;
        this.protectFirstN = protectFirstN;
        this.protectLastN = protectLastN;
        this.contentMaxChars = contentMaxChars;
        this.contentHeadChars = contentHeadChars;
        this.contentTailChars = contentTailChars;
        this.summaryInputMaxChars = summaryInputMaxChars;
        this.summarizerTimeoutSeconds = summarizerTimeoutSeconds;
        this.persistProjection = persistProjection;
        this.auditEnabled = auditEnabled;
        this.auditStoreMessages = auditStoreMessages;
        this.midRunEnabled = midRunEnabled;
    }

    public SessionContextCompactionServiceImpl(
            ObjectProvider<ReactorRuntimeDependencies> runtimeDependenciesProvider,
            SessionWorkingMemoryService sessionWorkingMemoryService,
            IWorkingMemoryCompactionDao workingMemoryCompactionDao,
            boolean enabled, boolean llmEnabled, boolean microEnabled, boolean sessionMemoryEnabled,
            double thresholdPercent, int keepMinTokens, int keepMinTextMessages,
            int keepMaxTokens, int maxConsecutiveFailures, double temperature, int messageContentCharLimit,
            int microKeepRecentToolResults, int microToolResultMaxChars,
            boolean persistProjection, boolean auditEnabled, boolean auditStoreMessages, boolean midRunEnabled) {
        this(runtimeDependenciesProvider, sessionWorkingMemoryService, workingMemoryCompactionDao,
                enabled, llmEnabled, microEnabled, sessionMemoryEnabled, thresholdPercent,
                keepMinTokens, keepMinTextMessages, keepMaxTokens, maxConsecutiveFailures, temperature,
                messageContentCharLimit, microKeepRecentToolResults, microToolResultMaxChars,
                0.20d, 3, 8, 6000, 4000, 1500, 160000, 120,
                persistProjection, auditEnabled, auditStoreMessages, midRunEnabled);
    }
    private ReactorRuntimeDependencies runtimeDependencies() {
        return runtimeDependenciesProvider.getObject();
    }


    @Override
    public List<Message> applyIfNeeded(String sessionId, String memoryScope, String requestId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : messages;
        }
        String scope = WorkingMemoryScopes.normalize(memoryScope);
        // 防止 compact / LTM fork 自己的 LLM 调用再触发压缩
        if (StringUtils.isNotBlank(requestId)
                && (requestId.contains("-compact")
                || requestId.contains("-flush")
                || requestId.contains("-bg-review"))) {
            return messages;
        }
        CompactionBudget budget = resolveBudget();
        if (!budget.isEnabled()) {
            return messages;
        }
        LtmFlushParity parity = FLUSH_PARITY.get();
        String systemPrompt = parity == null ? null : parity.parentSystemPrompt();
        ToolCollection tools = parity == null ? null : parity.parentTools();
        ContextTokenTracker.ContextTokenEstimate initialEstimate =
                resolveTokenEstimate(messages, systemPrompt, tools, false);
        int extraFixedTokens = initialEstimate.getSystemTokens() + initialEstimate.getToolTokens();

        // 永久失败必须回到入口快照，忽略后续 micro/LTM/reminder 改写。
        List<Message> originalSnapshot = List.copyOf(messages);
        int originalTokens = initialEstimate.getEstimatedTokens();
        List<Message> current = messages;

        if (budget.isMicroEnabled()) {
            List<Message> microed = compactor.microcompact(current, budget);
            if (microed != current && !microed.equals(current)) {
                int afterMicro = compactor.estimateRequestTokens(systemPrompt, microed, tools);
                log.info("microcompact sessionId={} scope={} before={} after={} msgs={}",
                        sessionId, scope, originalTokens, afterMicro, microed.size());
                current = microed;
            }
        }

        boolean mutatedByMicro = current != messages && !current.equals(messages);
        ContextTokenTracker.ContextTokenEstimate decisionEstimate =
                resolveTokenEstimate(current, systemPrompt, tools, mutatedByMicro);
        if (!compactor.shouldCompact(decisionEstimate.getEstimatedTokens(), budget)) {
            log.info("context-token skip compact sessionId={} scope={} {} threshold={}",
                    sessionId, scope, decisionEstimate.toLogLine(), budget.threshold());
            String microCompactId = maybePersistIfChanged(sessionId, scope, requestId, messages, current, originalTokens, "micro-only", budget);
            if (current != messages && !current.equals(messages)) {
                recordCompactionEvent(sessionId, requestId, messages, current, originalTokens, "micro-only", budget, null, microCompactId);
            }
            return current;
        }

        // 子 Agent 只压缩自己的 working_memory，不向共享长期记忆 flush 或调用 provider hook。
        if (!WorkingMemoryScopes.isSubScope(scope)) {
            current = applyLtmPreCompactHooks(sessionId, requestId, current);
        }

        int beforeAuto = compactor.estimateRequestTokens(systemPrompt, current, tools);
        log.info("auto-compact triggered sessionId={} scope={} requestId={} {} threshold={} extraFixed={}",
                sessionId, scope, requestId, decisionEstimate.toLogLine(), budget.threshold(), extraFixedTokens);

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
                log.info("session-memory compact sessionId={} scope={} before={} after={} msgs={}",
                        sessionId, scope, beforeAuto, compactor.estimateRequestTokens(systemPrompt, sm, tools), sm.size());
            }
        }

        if (compacted == null && budget.isLlmEnabled() && !isCircuitOpen(sessionId, budget)) {
            try {
                List<Message> full = fullCompact(sessionId, requestId, current, budget);
                compacted = full;
                strategy = STRATEGY_FULL_LLM;
                clearFailures(sessionId);
            } catch (Exception e) {
                log.warn("full compact failed sessionId={} scope={} requestId={}: {}",
                        sessionId, scope, requestId, e.getMessage());
                String failure = classifyFailure(e);
                error = e.getMessage();
                if ("permanent".equals(failure)) {
                    strategy = STRATEGY_ABORT_UNCHANGED;
                    compacted = originalSnapshot;
                } else {
                    recordFailure(sessionId);
                    compacted = localFallback(current, budget);
                    strategy = STRATEGY_LOCAL_FALLBACK;
                }
            }
        }

        if (compacted == null && isCircuitOpen(sessionId, budget)) {
            compacted = localFallback(current, budget);
            strategy = STRATEGY_LOCAL_FALLBACK;
            error = "compaction circuit open";
        }

        // handoff 组装后仍超阈 → 在保留 summary 的前提下 drop-oldest
        if (compacted != null
                && !STRATEGY_ABORT_UNCHANGED.equals(strategy)
                && compactor.estimateRequestTokens(systemPrompt, compacted, tools) >= budget.threshold()) {
            log.warn("compacted still over threshold sessionId={} scope={} strategy={} after={} threshold={}",
                    sessionId, scope, strategy,
                    compactor.estimateRequestTokens(systemPrompt, compacted, tools), budget.threshold());
            compacted = compactor.dropOldestToFit(compacted, budget, true, extraFixedTokens);
            strategy = strategy == null ? STRATEGY_DROP_OLDEST : strategy + "+" + STRATEGY_DROP_OLDEST;
        }

        if (compacted == null) {
            compacted = compactor.dropOldestToFit(current, budget, false, extraFixedTokens);
            strategy = STRATEGY_DROP_OLDEST;
            log.info("drop-oldest sessionId={} scope={} beforeMsgs={} afterMsgs={} afterTokens={}",
                    sessionId, scope, current.size(), compacted.size(),
                    compactor.estimateRequestTokens(systemPrompt, compacted, tools));
        }

        if (STRATEGY_ABORT_UNCHANGED.equals(strategy)) {
            recordCompactionEvent(sessionId, requestId, originalSnapshot, originalSnapshot,
                    originalTokens, strategy, budget, error, null);
            return originalSnapshot;
        }
        compacted = compacted == null ? current : compacted;
        String compactRequestId = maybePersistIfChanged(sessionId, scope, requestId, messages, compacted, originalTokens, strategy, budget);
        recordCompactionEvent(sessionId, requestId, messages, compacted, originalTokens, strategy, budget, error, compactRequestId);
        // 压缩后提醒：可用 memory / session_search 找回耐久事实与账本细节
        return MemoryFlushPolicy.prependPostCompactReminder(compacted);
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
            LtmFlushParity parity = FLUSH_PARITY.get();
            // ① 独立 flush 小回合；优先运行时绑定
            MemoryFlushService flushService = LtmServices.memoryFlush();
            if (flushService == null) {
                flushService = deps.getOptionalMemoryFlushService();
            }
            if (flushService != null) {
                int applied = flushService.flushBeforeCompact(
                        sessionId,
                        requestId,
                        parity == null ? null : parity.owner(),
                        working,
                        parity == null ? null : parity.parentSystemPrompt(),
                        parity == null ? null : parity.parentTools());
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
    public List<Message> applyIfNeededMidRun(String sessionId,
                                             String memoryScope,
                                             String requestId,
                                             List<Message> messages,
                                             LtmFlushParity flushParity,
                                             PromptShape promptShape,
                                             ContextTokenTracker.Snapshot tokenSnapshot) {
        if (!midRunEnabled) {
            return messages == null ? List.of() : messages;
        }
        FLUSH_PARITY.set(flushParity);
        PROMPT_SHAPE.set(promptShape);
        TOKEN_SNAPSHOT.set(tokenSnapshot);
        try {
            return applyIfNeeded(sessionId, memoryScope, requestId, messages);
        } finally {
            FLUSH_PARITY.remove();
            PROMPT_SHAPE.remove();
            TOKEN_SNAPSHOT.remove();
        }
    }

    /**
     * mid-run token 判断只读内存参数，不访问 MySQL。
     * {@code forceLocal} 在消息已被 micro/compact 改写后回退完整本地估算。
     */
    private ContextTokenTracker.ContextTokenEstimate resolveTokenEstimate(List<Message> messages,
                                                                          String systemPrompt,
                                                                          ToolCollection tools,
                                                                          boolean forceLocal) {
        PromptShape shape = PROMPT_SHAPE.get();
        PromptShape effective = shape != null
                ? shape.withMessages(messages)
                : PromptShape.functionCall(
                StringUtils.isBlank(systemPrompt) ? null : Message.systemMessage(systemPrompt, null),
                messages,
                tools);
        ContextTokenTracker.Snapshot snapshot = forceLocal ? null : TOKEN_SNAPSHOT.get();
        return ContextTokenTracker.estimateCurrent(snapshot, effective, new TokenCounter());
    }

    private static final int MAX_COMPACT_PTL_RETRIES = 3;

    protected List<Message> fullCompact(String sessionId,
                                      String requestId,
                                       List<Message> messages,
                                       CompactionBudget budget) throws Exception {
        WorkingMemoryCompactor.CompactionWindow window = compactor.splitForFullCompact(messages, budget);
        String previous = window.previousSummary();
        List<Message> body = new ArrayList<>(messages);
        int handoff = -1;
        for (int i = 0; i < body.size() && i < 12; i++) {
            if (compactor.isCompactSummaryMessage(body.get(i))) {
                handoff = i;
                break;
            }
        }
        if (handoff >= 0) {
            body.remove(handoff);
        }
        int tailStart = Math.min(window.tailStart(), body.size());
        int headEnd = Math.min(window.headEnd(), tailStart);
        List<Message> head = new ArrayList<>(body.subList(0, headEnd));
        List<Message> middle = new ArrayList<>(body.subList(headEnd, tailStart));
        List<Message> tail = new ArrayList<>(body.subList(tailStart, body.size()));
        if (middle.isEmpty()) {
            log.warn("full compact empty middle sessionId={} headEnd={} tailStart={} msgs={}",
                    sessionId, headEnd, tailStart, body.size());
            return messages;
        }

        boolean iterative = StringUtils.isNotBlank(previous);
        String systemPrompt = iterative
                ? CompactionPrompt.getIterativeUpdatePrompt()
                : CompactionPrompt.getCompactPrompt();

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

        Message system = Message.systemMessage(systemPrompt, null);

        // 压缩请求自身 413/超上下文时，按 tool-safe 丢掉 middle 最旧段再重试。
        Exception lastError = null;
        List<Message> middleForSummary = middle;
        for (int attempt = 0; attempt <= MAX_COMPACT_PTL_RETRIES; attempt++) {
            String serialized = compactor.serializeMiddleForSummarizer(middleForSummary, budget);
            String userPayload = iterative
                    ? CompactionPrompt.buildIterativeUserPayload(previous, serialized)
                    : serialized + "\n\nPlease provide the conversation checkpoint now.";
            List<Message> askMessages = List.of(Message.userMessage(userPayload, null));
            try {
                String raw = llm.ask(
                        context,
                        askMessages,
                        List.of(system),
                        true,
                        false,
                        budget.getTemperature(),
                        ExecutionLedgerConstants.CALL_KIND_INTERNAL_COMPACT
                ).get(Math.max(1, budget.getSummarizerTimeoutSeconds()), TimeUnit.SECONDS);

                String formatted = CompactionPrompt.formatCompactSummary(raw);
                if (StringUtils.isBlank(formatted)) {
                    throw new IllegalStateException("empty compact summary");
                }
                String latestUser = latestActionableUser(body);
                String grounded = CompactionPrompt.groundHistoricalTaskSnapshot(formatted, latestUser);
                String reinject = CompactionPrompt.wrapSummaryForReinject(grounded, !tail.isEmpty());
                List<Message> post = compactor.buildPostCompactMessages(head, reinject,
                        compactor.chooseHandoffRole(head, tail), tail);
                log.info("full-llm compact sessionId={} attempt={} summarizeMsgs={} keepMsgs={} postMsgs={} postTokens={}",
                        sessionId, attempt, middleForSummary.size(), tail.size(), post.size(),
                        compactor.estimateTokens(post));
                return post;
            } catch (Exception e) {
                lastError = e;
                if (attempt >= MAX_COMPACT_PTL_RETRIES || !isPromptTooLongForCompact(e)) {
                    throw e;
                }
                List<Message> truncated = compactor.truncateHeadForCompactRetry(middleForSummary);
                if (truncated == null || truncated.size() >= middleForSummary.size()) {
                    throw e;
                }
                log.warn("compact request too long, truncate middle and retry sessionId={} attempt={} beforeMsgs={} afterMsgs={}",
                        sessionId, attempt + 1, middleForSummary.size(), truncated.size());
                middleForSummary = truncated;
            }
        }
        throw lastError != null ? lastError : new IllegalStateException("compact failed without error");
    }

    protected List<Message> localFallback(List<Message> messages, CompactionBudget budget) {
        WorkingMemoryCompactor.CompactionWindow window = compactor.splitForFullCompact(messages, budget);
        String previous = window.previousSummary();
        List<Message> body = new ArrayList<>(messages);
        int handoff = -1;
        for (int i = 0; i < body.size() && i < 12; i++) {
            if (compactor.isCompactSummaryMessage(body.get(i))) {
                handoff = i;
                break;
            }
        }
        if (handoff >= 0) {
            body.remove(handoff);
        }
        int tailStart = Math.min(window.tailStart(), body.size());
        int headEnd = Math.min(window.headEnd(), tailStart);
        List<Message> head = body.subList(0, headEnd);
        List<Message> middle = body.subList(headEnd, tailStart);
        List<Message> tail = body.subList(tailStart, body.size());
        String summary = CompactionPrompt.groundHistoricalTaskSnapshot(
                compactor.buildStaticFallbackSummary(middle, previous, latestActionableUser(body)),
                latestActionableUser(body));
        String wrapped = CompactionPrompt.wrapSummaryForReinject(summary, !tail.isEmpty());
        return compactor.buildPostCompactMessages(head, wrapped, null, tail);
    }

    private String latestActionableUser(List<Message> messages) {
        if (messages == null) return "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m != null && m.getRole() == org.wwz.ai.domain.agent.runtime.enums.RoleType.USER
                    && StringUtils.isNotBlank(m.getContent()) && !compactor.isCompactSummaryMessage(m)) return m.getContent();
        }
        return "";
    }

    private static boolean isAuditedStrategy(String strategy) {
        return STRATEGY_FULL_LLM.equals(strategy)
                || "session-memory".equals(strategy)
                || STRATEGY_DROP_OLDEST.equals(strategy)
                || "micro-only".equals(strategy)
                || STRATEGY_ABORT_UNCHANGED.equals(strategy)
                || STRATEGY_LOCAL_FALLBACK.equals(strategy)
                || isSuccessfulTrimStrategy(strategy);
    }

    private static boolean isSuccessfulTrimStrategy(String strategy) {
        return STRATEGY_LOCAL_FALLBACK.equals(strategy)
                || STRATEGY_DROP_OLDEST.equals(strategy)
                || STRATEGY_FULL_LLM.equals(strategy)
                || (strategy != null && strategy.contains(STRATEGY_DROP_OLDEST));
    }

    public static String classifyFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.util.concurrent.TimeoutException
                    || current instanceof java.util.concurrent.CancellationException) {
                return "transient";
            }
            String message = StringUtils.defaultString(current.getMessage()).toLowerCase(Locale.ROOT);
            if (message.contains("401") || message.contains("403") || message.contains("unauthorized")
                    || message.contains("forbidden") || message.contains("auth")
                    || message.contains("quota") || message.contains("billing")
                    || message.contains("insufficient_quota")) {
                return "permanent";
            }
            if (message.contains("timeout") || message.contains("timed out")
                    || message.contains("empty") || message.contains("5xx")
                    || message.contains("502") || message.contains("503") || message.contains("504")) {
                return "transient";
            }
            current = current.getCause();
        }
        return "transient";
    }

    private static boolean isPromptTooLongForCompact(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("prompt is too long")
                        || lower.contains("prompt_too_long")
                        || lower.contains("context_length")
                        || lower.contains("context length")
                        || lower.contains("maximum context")
                        || lower.contains("max context")
                        || lower.contains("token limit")
                        || lower.contains("too many tokens")
                        || lower.contains("413")
                        || lower.contains("request too large")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    protected CompactionBudget resolveBudget() {
        int contextWindow = CompactionBudget.DEFAULT_CONTEXT_WINDOW;
        try {
            ReactorConfig config = runtimeDependencies().requireReactorConfig();
            String modelName = resolveCompactModelName(config);
            LLMSettings settings = runtimeDependencies().resolveLlmSettings(modelName);
            if (settings != null && settings.getMaxInputTokens() > 0) {
                contextWindow = settings.getMaxInputTokens();
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
                .thresholdPercent(thresholdPercent)
                .keepMinTokens(keepMinTokens)
                .keepMinTextMessages(keepMinTextMessages)
                .keepMaxTokens(keepMaxTokens)
                .maxConsecutiveFailures(maxConsecutiveFailures)
                .temperature(temperature)
                .messageContentCharLimit(messageContentCharLimit)
                .summaryTargetRatio(summaryTargetRatio)
                .protectFirstN(protectFirstN)
                .protectLastN(protectLastN)
                .contentMaxChars(contentMaxChars)
                .contentHeadChars(contentHeadChars)
                .contentTailChars(contentTailChars)
                .summaryInputMaxChars(summaryInputMaxChars)
                .summarizerTimeoutSeconds(summarizerTimeoutSeconds)
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
                                         String memoryScope,
                                         String requestId,
                                         List<Message> original,
                                         List<Message> result,
                                         int originalTokens,
                                         String strategy,
                                         CompactionBudget budget) {
        if (!persistProjection || STRATEGY_ABORT_UNCHANGED.equals(strategy) || result == null || result.isEmpty()) {
            return null;
        }
        int after = compactor.estimateTokens(result);
        boolean hasHandoff = false;
        for (Message message : result) {
            if (compactor.isCompactSummaryMessage(message)) {
                hasHandoff = true;
                break;
            }
        }
        boolean structural = result.size() < original.size() || hasHandoff;
        boolean tokenSaved = after < originalTokens;
        if (!structural && !tokenSaved) {
            return null;
        }
        if ("micro-only".equals(strategy) && !tokenSaved) {
            return null;
        }
        String compactRequestId = tryPersist(sessionId, memoryScope, requestId, result);
        log.info("persist compacted projection sessionId={} scope={} strategy={} tokens {}->{} compactRequestId={}",
                sessionId, memoryScope, strategy, originalTokens, after, compactRequestId);
        return compactRequestId;
    }

    private String tryPersist(String sessionId, String memoryScope, String requestId, List<Message> compacted) {
        if (sessionWorkingMemoryService == null || StringUtils.isBlank(sessionId) || compacted == null || compacted.isEmpty()) {
            return null;
        }
        try {
            // 必须 ≤ ai_agent_working_memory_turn.request_id VARCHAR(64)。
            // 旧拼接 wm-compact-{requestId}-{ts} 在 reactorsession 长 requestId 下可达 70+ 字符触发截断。
            // 与 trigger_request_id 的关联写在 compaction 审计表，不依赖嵌入原 requestId。
            String compactRequestId = COMPACT_REQUEST_PREFIX
                    + UUID.randomUUID().toString().replace("-", "");
            sessionWorkingMemoryService.replaceReadyProjection(
                    sessionId, WorkingMemoryScopes.normalize(memoryScope), compactRequestId, compacted);
            return compactRequestId;
        } catch (Exception e) {
            log.warn("persist compacted working memory failed sessionId={} scope={}: {}",
                    sessionId, memoryScope, e.getMessage());
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
        if (afterTokens >= beforeTokens && !isAuditedStrategy(strategy)) {
            return;
        }
        try {
            String summary = null;
            for (Message message : after) {
                if (compactor.isCompactSummaryMessage(message)) {
                    summary = message.getContent();
                    break;
                }
            }
            int status;
            if (STRATEGY_ABORT_UNCHANGED.equals(strategy)) {
                status = WorkingMemoryCompactionEvent.STATUS_FAILED;
            } else if (StringUtils.isBlank(errorMessage) || afterTokens < beforeTokens
                    || isSuccessfulTrimStrategy(strategy)) {
                status = WorkingMemoryCompactionEvent.STATUS_SUCCESS;
            } else {
                status = WorkingMemoryCompactionEvent.STATUS_FAILED;
            }
            WorkingMemoryCompactionEvent event = WorkingMemoryCompactionEvent.builder()
                    .sessionId(sessionId)
                    .triggerRequestId(StringUtils.defaultIfBlank(requestId, "unknown"))
                    .compactRequestId(compactRequestId)
                    .strategy(StringUtils.defaultIfBlank(strategy, "unknown"))
                    .status(status)
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

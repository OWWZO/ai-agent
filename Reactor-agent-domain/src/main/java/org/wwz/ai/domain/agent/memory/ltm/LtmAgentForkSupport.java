package org.wwz.ai.domain.agent.memory.ltm;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReactImplAgent;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.printer.LogPrinter;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolIsolation;
import org.wwz.ai.domain.agent.runtime.tool.common.MemoryTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LTM fork：对齐 Hermes background_review 前缀缓存策略。
 * <ul>
 *   <li>system 原样复用父 frozen prompt（不追加 fork directive）</li>
 *   <li>tools[] schema 与父全量一致（runtime whitelist 只放行 memory）</li>
 *   <li>历史 messages 前缀重放；review/flush 指令仅作为尾部 user</li>
 * </ul>
 */
public final class LtmAgentForkSupport {

    private static final ExecutorService FORK_POOL = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ltm-fork-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private LtmAgentForkSupport() {
    }

    /**
     * @deprecated 使用 {@link #runParityFork}；保留签名以免外部编译断裂。
     */
    @Deprecated
    public static LtmForkRunResult runMemoryOnlyFork(ReactorRuntimeDependencies deps,
                                                    CuratedMemoryStore store,
                                                    LtmOwner owner,
                                                    String sessionId,
                                                    String parentRequestId,
                                                    String parentSystemPrompt,
                                                    List<Message> conversationSnapshot,
                                                    String userDirective,
                                                    int maxSteps,
                                                    long timeoutSeconds,
                                                    String forkLabel) {
        return runParityFork(
                deps,
                store,
                owner,
                sessionId,
                parentRequestId,
                LtmForkParity.of(parentSystemPrompt, null, conversationSnapshot),
                userDirective,
                maxSteps,
                timeoutSeconds,
                forkLabel);
    }

    public static LtmForkRunResult runParityFork(ReactorRuntimeDependencies deps,
                                                CuratedMemoryStore store,
                                                LtmOwner owner,
                                                String sessionId,
                                                String parentRequestId,
                                                LtmForkParity parity,
                                                String userDirective,
                                                int maxSteps,
                                                long timeoutSeconds,
                                                String forkLabel) {
        if (deps == null || store == null || owner == null) {
            return LtmForkRunResult.skipped("deps-or-store-or-owner-null");
        }
        if (parity == null) {
            return LtmForkRunResult.skipped("parity-null");
        }
        if (StringUtils.isBlank(userDirective)) {
            return LtmForkRunResult.skipped("blank-directive");
        }

        final String directive = userDirective;
        final int steps = Math.max(1, maxSteps);
        final String label = StringUtils.defaultIfBlank(forkLabel, "fork");
        final String sid = sessionId;
        final String reqId = parentRequestId;
        final LtmOwner forkOwner = owner;
        final CuratedMemoryStore forkStore = store;
        final ReactorRuntimeDependencies forkDeps = deps;
        final LtmForkParity forkParity = parity;
        final String forkRequestId = StringUtils.defaultIfBlank(reqId, "ltm") + "-" + label;

        Map<String, CuratedMemoryEntry> beforeMap = snapshotActiveEntries(forkStore, forkOwner);
        int before = beforeMap.size();
        long startedAt = System.currentTimeMillis();
        Callable<LtmForkRunResult> task = () -> {
            AgentRequest fake = new AgentRequest();
            fake.setRequestId(forkRequestId);
            fake.setSessionId(sid);

            AgentContext child = AgentContext.builder()
                    .requestId(fake.getRequestId())
                    .sessionId(sid)
                    .query(directive)
                    .isStream(false)
                    .skipMemory(false)
                    .ltmSideEffectsDisabled(true)
                    .ltmOwner(forkOwner)
                    .ltmMemoryContext(null)
                    .toolDispatchWhitelist(forkParity.getDispatchWhitelist())
                    .workingMemoryMessages(new ArrayList<>(forkParity.getConversationSnapshot()))
                    .runtimeDependencies(forkDeps)
                    .printer(new LogPrinter(fake))
                    .executionRecorder(null)
                    .agentRunState(null)
                    .build();

            ToolCollection tools = copyParentToolsForFork(forkParity.getParentTools(), child);
            child.setToolCollection(tools);

            ReactImplAgent agent = new ReactImplAgent(child);
            agent.setName("ltm-" + label);
            agent.setMaxSteps(steps);
            // Hermes：原样钉死父 system；无父 system 时才退回默认 React 底座（冷缓存）
            if (forkParity.hasSystemPrompt()) {
                agent.setSystemPrompt(forkParity.getFrozenSystemPrompt());
            }
            agent.run(directive);
            Map<String, CuratedMemoryEntry> afterMap = snapshotActiveEntries(forkStore, forkOwner);
            String writtenJson = buildWrittenEntriesJson(beforeMap, afterMap);
            int applied = countAdded(beforeMap, afterMap);
            return LtmForkRunResult.builder()
                    .status(LtmForkRunResult.STATUS_SUCCESS)
                    .appliedCount(applied)
                    .entriesBefore(before)
                    .entriesAfter(afterMap.size())
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .forkRequestId(forkRequestId)
                    .forkLabel(label)
                    .writtenEntriesJson(writtenJson)
                    .build();
        };

        Future<LtmForkRunResult> future = FORK_POOL.submit(task);
        try {
            LtmForkRunResult result = future.get(Math.max(5L, timeoutSeconds), TimeUnit.SECONDS);
            if (result == null) {
                return LtmForkRunResult.failed(forkRequestId, label, before,
                        System.currentTimeMillis() - startedAt, "null result");
            }
            if (result.getDurationMs() <= 0) {
                result.setDurationMs(System.currentTimeMillis() - startedAt);
            }
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            return LtmForkRunResult.timeout(forkRequestId, label, before,
                    System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            return LtmForkRunResult.failed(forkRequestId, label, before,
                    System.currentTimeMillis() - startedAt,
                    e.getClass().getSimpleName() + ": " + StringUtils.left(String.valueOf(e.getMessage()), 500));
        }
    }

    /**
     * 复制父 tools 映射（schema 对齐），再 bind 到 fork context；保证 memory 可执行。
     */
    public static ToolCollection copyParentToolsForFork(ToolCollection parent, AgentContext child) {
        ToolCollection copy = new ToolCollection();
        if (parent != null) {
            copy.setMcpToolExecutor(parent.getMcpToolExecutor());
            copy.restoreTaskScopedState(parent.snapshotTaskScopedState());
            if (parent.getToolMap() != null) {
                for (Map.Entry<String, BaseTool> entry : parent.getToolMap().entrySet()) {
                    if (entry.getValue() != null) {
                        copy.addTool(entry.getValue());
                    }
                }
            }
            if (parent.getMcpToolMap() != null) {
                for (Map.Entry<String, McpToolInfo> entry : parent.getMcpToolMap().entrySet()) {
                    if (entry.getValue() != null) {
                        copy.addMcpTool(entry.getValue());
                    }
                }
            }
        }
        if (copy.getTool(MemoryTool.TOOL_NAME) == null) {
            MemoryTool memoryTool = new MemoryTool();
            copy.addTool(memoryTool);
        }
        ToolIsolation.bindAll(copy, child);
        MemoryTool memory = (MemoryTool) copy.getTool(MemoryTool.TOOL_NAME);
        if (memory != null) {
            memory.setAgentContext(child);
        }
        return copy;
    }

    private static Map<String, CuratedMemoryEntry> snapshotActiveEntries(CuratedMemoryStore store, LtmOwner owner) {
        Map<String, CuratedMemoryEntry> out = new LinkedHashMap<>();
        if (store == null || owner == null) {
            return out;
        }
        try {
            for (CuratedMemoryScope scope : List.of(CuratedMemoryScope.USER, CuratedMemoryScope.CURATED)) {
                List<CuratedMemoryEntry> rows = store.listActive(owner, scope);
                if (rows == null) {
                    continue;
                }
                for (CuratedMemoryEntry e : rows) {
                    if (e == null || StringUtils.isBlank(e.getContent())) {
                        continue;
                    }
                    String scopeCode = e.getScope() == null ? scope.getCode() : e.getScope().getCode();
                    out.put(scopeCode + "\u0001" + e.getContent().trim(), e);
                }
            }
        } catch (Exception ignored) {
            // keep partial
        }
        return out;
    }

    private static int countAdded(Map<String, CuratedMemoryEntry> before, Map<String, CuratedMemoryEntry> after) {
        if (after == null || after.isEmpty()) {
            return 0;
        }
        Set<String> beforeKeys = before == null ? Set.of() : before.keySet();
        int n = 0;
        for (String k : after.keySet()) {
            if (!beforeKeys.contains(k)) {
                n++;
            }
        }
        return n;
    }

    private static String buildWrittenEntriesJson(Map<String, CuratedMemoryEntry> before,
                                                 Map<String, CuratedMemoryEntry> after) {
        JSONArray added = new JSONArray();
        JSONArray removed = new JSONArray();
        Set<String> beforeKeys = before == null ? Set.of() : before.keySet();
        Set<String> afterKeys = after == null ? Set.of() : after.keySet();
        if (after != null) {
            for (Map.Entry<String, CuratedMemoryEntry> e : after.entrySet()) {
                if (beforeKeys.contains(e.getKey())) {
                    continue;
                }
                added.add(toEntryJson(e.getValue(), e.getKey()));
            }
        }
        if (before != null) {
            for (Map.Entry<String, CuratedMemoryEntry> e : before.entrySet()) {
                if (afterKeys.contains(e.getKey())) {
                    continue;
                }
                removed.add(toEntryJson(e.getValue(), e.getKey()));
            }
        }
        JSONObject root = new JSONObject(true);
        root.put("added", added);
        root.put("removed", removed);
        root.put("added_count", added.size());
        root.put("removed_count", removed.size());
        return root.toJSONString();
    }

    private static JSONObject toEntryJson(CuratedMemoryEntry e, String key) {
        JSONObject o = new JSONObject(true);
        if (e != null) {
            o.put("id", e.getId());
            o.put("scope", e.getScope() == null ? null : e.getScope().getCode());
            o.put("content", e.getContent());
            o.put("write_origin", e.getWriteOrigin());
            o.put("source_request_id", e.getSourceRequestId());
        } else if (key != null && key.contains("\u0001")) {
            int i = key.indexOf('\u0001');
            o.put("scope", key.substring(0, i));
            o.put("content", key.substring(i + 1));
        }
        return o;
    }

    /** @see LtmPromptGuidance#FLUSH_DIRECTIVE */
    public static final String FLUSH_DIRECTIVE = LtmPromptGuidance.FLUSH_DIRECTIVE;

    /** @see LtmPromptGuidance#REVIEW_DIRECTIVE */
    public static final String REVIEW_DIRECTIVE = LtmPromptGuidance.REVIEW_DIRECTIVE;
}

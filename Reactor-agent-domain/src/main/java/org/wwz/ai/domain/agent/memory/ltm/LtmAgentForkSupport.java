package org.wwz.ai.domain.agent.memory.ltm;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReactImplAgent;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.printer.LogPrinter;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
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
 * LTM fork：同 runtime、全量消息前缀、仅 memory 工具。
 * 用于压前 flush 与 background review，对齐 prefix-cache 友好重放。
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
        if (deps == null || store == null || owner == null) {
            return LtmForkRunResult.skipped("deps-or-store-or-owner-null");
        }
        final List<Message> snapshot = conversationSnapshot == null
                ? List.of()
                : new ArrayList<>(conversationSnapshot);
        if (StringUtils.isBlank(userDirective)) {
            return LtmForkRunResult.skipped("blank-directive");
        }

        final String directive = userDirective;
        final String systemPrompt = parentSystemPrompt;
        final int steps = Math.max(1, maxSteps);
        final String label = StringUtils.defaultIfBlank(forkLabel, "fork");
        final String sid = sessionId;
        final String reqId = parentRequestId;
        final LtmOwner forkOwner = owner;
        final CuratedMemoryStore forkStore = store;
        final ReactorRuntimeDependencies forkDeps = deps;
        final String forkRequestId = StringUtils.defaultIfBlank(reqId, "ltm") + "-" + label;

        Map<String, CuratedMemoryEntry> beforeMap = snapshotActiveEntries(forkStore, forkOwner);
        int before = beforeMap.size();
        long startedAt = System.currentTimeMillis();
        Callable<LtmForkRunResult> task = () -> {
            AgentRequest fake = new AgentRequest();
            fake.setRequestId(forkRequestId);
            fake.setSessionId(sid);

            ToolCollection tools = new ToolCollection();
            MemoryTool memoryTool = new MemoryTool();
            tools.addTool(memoryTool);

            AgentContext child = AgentContext.builder()
                    .requestId(fake.getRequestId())
                    .sessionId(sid)
                    .query(directive)
                    .isStream(false)
                    // 允许 memory tool 写入；禁止再 sync/再调度 review
                    .skipMemory(false)
                    .ltmSideEffectsDisabled(true)
                    .ltmOwner(forkOwner)
                    .ltmMemoryContext(null)
                    .workingMemoryMessages(new ArrayList<>(snapshot))
                    .runtimeDependencies(forkDeps)
                    .toolCollection(tools)
                    .printer(new LogPrinter(fake))
                    .executionRecorder(null)
                    .agentRunState(null)
                    .build();
            tools.setAgentContext(child);
            memoryTool.setAgentContext(child);

            ReactImplAgent agent = new ReactImplAgent(child);
            agent.setName("ltm-" + label);
            agent.setMaxSteps(steps);
            // 父 system（cache 友好）+ 与主路径一致的写入标准；无父 system 时仍注入完整 guidance
            agent.setSystemPrompt(LtmPromptGuidance.forkSystemPrompt(systemPrompt));
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

    private static int countEntries(CuratedMemoryStore store, LtmOwner owner) {
        return snapshotActiveEntries(store, owner).size();
    }

    /** key = scope + \\u0001 + content */
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

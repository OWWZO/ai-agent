package org.wwz.ai.domain.agent.memory.ltm;

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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hermes 风格 LTM fork：同 runtime、全量消息前缀、仅 memory 工具。
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

    public static int runMemoryOnlyFork(ReactorRuntimeDependencies deps,
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
            return 0;
        }
        final List<Message> snapshot = conversationSnapshot == null
                ? List.of()
                : new ArrayList<>(conversationSnapshot);
        if (StringUtils.isBlank(userDirective)) {
            return 0;
        }

        final String directive = userDirective;
        final String systemPrompt = parentSystemPrompt;
        final int steps = Math.max(1, maxSteps);
        final String label = forkLabel;
        final String sid = sessionId;
        final String reqId = parentRequestId;
        final LtmOwner forkOwner = owner;
        final CuratedMemoryStore forkStore = store;
        final ReactorRuntimeDependencies forkDeps = deps;

        int before = countEntries(forkStore, forkOwner);
        Callable<Integer> task = () -> {
            AgentRequest fake = new AgentRequest();
            fake.setRequestId(StringUtils.defaultIfBlank(reqId, "ltm") + "-" + label);
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
            if (StringUtils.isNotBlank(systemPrompt)) {
                // 继承父 system 前缀（同模型下利于 prefix cache）+ 本 fork 指令
                agent.setSystemPrompt(systemPrompt.trim() + "\n\n# LTM fork directive\n"
                        + "You may ONLY use the memory tool. Do not call any other tools. "
                        + "Focus on durable user preferences and environment facts only.\n");
            }
            agent.run(directive);
            int after = countEntries(forkStore, forkOwner);
            return Math.max(0, after - before);
        };

        Future<Integer> future = FORK_POOL.submit(task);
        try {
            return future.get(Math.max(5L, timeoutSeconds), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int countEntries(CuratedMemoryStore store, LtmOwner owner) {
        try {
            return store.listActive(owner, CuratedMemoryScope.USER).size()
                    + store.listActive(owner, CuratedMemoryScope.CURATED).size();
        } catch (Exception e) {
            return 0;
        }
    }

    public static final String FLUSH_DIRECTIVE = """
            The conversation window above is about to be compacted.
            Save durable memories NOW via the memory tool only:
            - target=user: preferences, identity, communication style
            - target=curated: stable environment/project conventions
            Do NOT save one-off tasks, full procedures, or tool noise.
            If nothing durable, call nothing and finish.
            """;

    public static final String REVIEW_DIRECTIVE = """
            Review the conversation above and update long-term memory via the memory tool only.
            Save durable declarative facts:
            - target=user: preferences, identity, style
            - target=curated: stable environment/project facts
            Do NOT dump full how-to procedures into memory (those belong in skills).
            Do NOT save one-off tasks or unresolved failures.
            If nothing new is worth saving, finish without tool calls.
            """;
}

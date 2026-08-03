package org.wwz.ai.test.domain.subagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ContextIsolatableTool;
import org.wwz.ai.domain.agent.runtime.tool.ContextScopedTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 嵌套 tool 池硬化 + ContextScopedTool 隔离（app 模块 classpath 可初始化 slf4j）。
 */
public class AgentToolPoolNestingHardeningTest {

    @Test
    public void parallelAgentDispatchShouldNotDeadlockSharedToolPool() throws Exception {
        int agentCount = 8;
        ThreadPoolExecutor toolPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
        ThreadPoolExecutor taskPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(agentCount);
        try {
            AtomicInteger nestedToolRuns = new AtomicInteger();
            CountDownLatch nestedStarted = new CountDownLatch(agentCount);

            ToolCollection tools = new ToolCollection();
            tools.addTool(new NestedWorkTool(nestedToolRuns, nestedStarted));
            tools.addTool(new FakeAgentTool(tools));

            ReactorRuntimeDependencies deps = ReactorRuntimeDependencies.builder()
                    .taskExecutor(taskPool)
                    .toolExecutor(toolPool)
                    .toolBatchTimeoutSeconds(15L)
                    .build();

            AgentContext context = AgentContext.builder()
                    .requestId("req-nest")
                    .sessionId("sess-nest")
                    .query("parallel agents")
                    .toolCollection(tools)
                    .runtimeDependencies(deps)
                    .build();
            tools.setAgentContext(context);

            TestAgent agent = new TestAgent(context);
            List<ToolCall> batch = new ArrayList<>();
            for (int i = 0; i < agentCount; i++) {
                batch.add(ToolCall.builder()
                        .id("agent-" + i)
                        .type("function")
                        .function(ToolCall.Function.builder()
                                .name(AgentDispatchTool.NAME)
                                .arguments("{\"description\":\"t" + i + "\",\"prompt\":\"do work\"}")
                                .build())
                        .build());
            }

            Map<String, String> outcomes = agent.exposeExecuteTools(batch);
            Assert.assertEquals(agentCount, outcomes.size());
            for (String observation : outcomes.values()) {
                Assert.assertNotNull(observation);
                Assert.assertFalse(observation.toLowerCase().contains("error"));
            }
            Assert.assertEquals(agentCount, nestedToolRuns.get());
            Assert.assertTrue(nestedStarted.await(0, TimeUnit.SECONDS));
        } finally {
            toolPool.shutdownNow();
            taskPool.shutdownNow();
        }
    }

    @Test
    public void toolBatchTimeoutShouldMarkUnfinishedFailed() throws Exception {
        ExecutorService hangPool = Executors.newFixedThreadPool(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        try {
            ToolCollection tools = new ToolCollection();
            tools.addTool(new HangTool(interrupted));

            ReactorRuntimeDependencies deps = ReactorRuntimeDependencies.builder()
                    .taskExecutor(Runnable::run)
                    .toolExecutor(hangPool)
                    .toolBatchTimeoutSeconds(1L)
                    .build();

            AgentContext context = AgentContext.builder()
                    .requestId("req-timeout")
                    .sessionId("sess-timeout")
                    .query("timeout")
                    .toolCollection(tools)
                    .runtimeDependencies(deps)
                    .build();
            tools.setAgentContext(context);

            TestAgent agent = new TestAgent(context);
            ToolCall call = ToolCall.builder()
                    .id("hang-1")
                    .type("function")
                    .function(ToolCall.Function.builder()
                            .name("hang")
                            .arguments("{}")
                            .build())
                    .build();

            Map<String, String> outcomes = agent.exposeExecuteTools(Collections.singletonList(call));
            String observation = outcomes.get("hang-1");
            Assert.assertNotNull(observation);
            Assert.assertTrue(observation.contains("超时")
                    || observation.contains("timeout")
                    || observation.contains("TOOL_BATCH_TIMEOUT")
                    || observation.contains("终止等待"));
            Assert.assertTrue("批次超时后应中断底层工具任务",
                    interrupted.await(2, TimeUnit.SECONDS));
        } finally {
            hangPool.shutdownNow();
        }
    }

    @Test
    public void parallelScopedToolsShouldNotCrossAgentContext() throws Exception {
        SharedContextTool shared = new SharedContextTool();
        AgentContext parent = AgentContext.builder().requestId("parent").sessionId("s").query("q").build();
        AgentContext childA = AgentContext.builder().requestId("child-a").sessionId("s").query("qa").build();
        AgentContext childB = AgentContext.builder().requestId("child-b").sessionId("s").query("qb").build();
        shared.setAgentContext(parent);

        BaseTool toolA = ContextScopedTool.bind(shared, childA);
        BaseTool toolB = ContextScopedTool.bind(shared, childB);

        // SharedContextTool 可反射 fork：应是独立实例，而不是共享锁包装
        Assert.assertNotSame(shared, toolA);
        Assert.assertNotSame(shared, toolB);
        Assert.assertNotSame(toolA, toolB);
        Assert.assertFalse(toolA instanceof ContextScopedTool);
        Assert.assertFalse(toolB instanceof ContextScopedTool);

        int rounds = 40;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> fa = pool.submit(() -> {
                for (int i = 0; i < rounds; i++) {
                    Assert.assertEquals("child-a", toolA.execute(null));
                }
            });
            Future<?> fb = pool.submit(() -> {
                for (int i = 0; i < rounds; i++) {
                    Assert.assertEquals("child-b", toolB.execute(null));
                }
            });
            fa.get(5, TimeUnit.SECONDS);
            fb.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        Assert.assertEquals("parent", shared.getAgentContext().getRequestId());
        Assert.assertNull(shared.leakedRequestId.get());
    }

    @Test
    public void parallelIsolatedLongToolsShouldOverlapWithoutSharedLock() throws Exception {
        LongRunningTool prototype = new LongRunningTool();
        AgentContext parent = AgentContext.builder().requestId("parent").sessionId("s").query("q").build();
        AgentContext childA = AgentContext.builder().requestId("child-a").sessionId("s").query("qa").build();
        AgentContext childB = AgentContext.builder().requestId("child-b").sessionId("s").query("qb").build();
        prototype.setAgentContext(parent);

        BaseTool toolA = ContextScopedTool.bind(prototype, childA);
        BaseTool toolB = ContextScopedTool.bind(prototype, childB);
        Assert.assertNotSame(toolA, toolB);

        CountDownLatch bothStarted = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        long startedAt = System.currentTimeMillis();
        try {
            Future<?> fa = pool.submit(() -> {
                bothStarted.countDown();
                bothStarted.await(2, TimeUnit.SECONDS);
                return toolA.execute(null);
            });
            Future<?> fb = pool.submit(() -> {
                bothStarted.countDown();
                bothStarted.await(2, TimeUnit.SECONDS);
                return toolB.execute(null);
            });
            Assert.assertEquals("child-a", fa.get(3, TimeUnit.SECONDS));
            Assert.assertEquals("child-b", fb.get(3, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        // 串行约 2x200ms；并行应接近单次耗时（允许调度抖动）
        long elapsed = System.currentTimeMillis() - startedAt;
        Assert.assertTrue("expected parallel execution, elapsed=" + elapsed, elapsed < 320L);
    }

    @Test
    public void bindAllShouldGiveEachChildIndependentToolInstances() {
        ToolCollection parentTools = new ToolCollection();
        LongRunningTool shared = new LongRunningTool();
        AgentContext parent = AgentContext.builder().requestId("parent").sessionId("s").query("q").build();
        shared.setAgentContext(parent);
        parentTools.addTool(shared);
        parentTools.setAgentContext(parent);

        ToolCollection childTools = new ToolCollection();
        childTools.addTool(shared);
        AgentContext child = AgentContext.builder().requestId("child").sessionId("s").query("c").build();
        ContextScopedTool.bindAll(childTools, child);

        BaseTool bound = childTools.getTool("long_running");
        Assert.assertNotNull(bound);
        Assert.assertNotSame(shared, bound);
        Assert.assertEquals("child", bound.execute(null));
        Assert.assertEquals("parent", shared.getAgentContext().getRequestId());
    }

    private static final class TestAgent extends BaseAgent {
        private TestAgent(AgentContext context) {
            setContext(context);
            setAvailableTools(context.getToolCollection());
            setPrinter(new NoopPrinter());
        }

        Map<String, String> exposeExecuteTools(List<ToolCall> commands) {
            return executeTools(commands);
        }

        @Override
        public String step() {
            return "";
        }
    }

    private static final class FakeAgentTool implements BaseTool {
        private final ToolCollection tools;
        private AgentContext agentContext;

        private FakeAgentTool(ToolCollection tools) {
            this.tools = tools;
        }

        public void setAgentContext(AgentContext agentContext) {
            this.agentContext = agentContext;
        }

        public AgentContext getAgentContext() {
            return agentContext;
        }

        @Override
        public String getName() {
            return AgentDispatchTool.NAME;
        }

        @Override
        public String getDescription() {
            return "fake agent";
        }

        @Override
        public Map<String, Object> toParams() {
            return Collections.emptyMap();
        }

        @Override
        public Object execute(Object input) {
            AgentContext parent = agentContext != null ? agentContext : tools.getAgentContext();
            if (parent == null) {
                return ToolResultPayload.failure("parent missing", "parent missing", null, "parent missing");
            }
            NestedAgent nested = new NestedAgent(parent, tools);
            ToolCall nestedCall = ToolCall.builder()
                    .id("nested-" + Thread.currentThread().getId() + "-" + System.nanoTime())
                    .type("function")
                    .function(ToolCall.Function.builder()
                            .name("nested_work")
                            .arguments("{}")
                            .build())
                    .build();
            Map<String, String> outcomes = nested.exposeExecuteTools(Collections.singletonList(nestedCall));
            String observation = outcomes.values().iterator().next();
            if (observation == null || observation.toLowerCase().contains("error")
                    || observation.contains("超时") || observation.contains("failed")) {
                return ToolResultPayload.failure("nested failed", "nested failed", null, "nested failed");
            }
            return ToolResultPayload.text("agent-done");
        }
    }

    private static final class NestedAgent extends BaseAgent {
        private NestedAgent(AgentContext parent, ToolCollection tools) {
            AgentContext child = AgentContext.builder()
                    .requestId(parent.getRequestId() + ":child")
                    .sessionId(parent.getSessionId())
                    .query("nested")
                    .toolCollection(tools)
                    .runtimeDependencies(parent.getRuntimeDependencies())
                    .build();
            setContext(child);
            setAvailableTools(tools);
            setPrinter(new NoopPrinter());
        }

        Map<String, String> exposeExecuteTools(List<ToolCall> commands) {
            return executeTools(commands);
        }

        @Override
        public String step() {
            return "";
        }
    }

    private static final class NestedWorkTool implements BaseTool {
        private final AtomicInteger runs;
        private final CountDownLatch started;

        private NestedWorkTool(AtomicInteger runs, CountDownLatch started) {
            this.runs = runs;
            this.started = started;
        }

        @Override
        public String getName() {
            return "nested_work";
        }

        @Override
        public String getDescription() {
            return "nested_work";
        }

        @Override
        public Map<String, Object> toParams() {
            return Collections.emptyMap();
        }

        @Override
        public Object execute(Object input) {
            started.countDown();
            runs.incrementAndGet();
            return ToolResultPayload.text("nested-ok");
        }
    }

    private static final class HangTool implements BaseTool {
        private final CountDownLatch interrupted;

        private HangTool(CountDownLatch interrupted) {
            this.interrupted = interrupted;
        }

        @Override
        public String getName() {
            return "hang";
        }

        @Override
        public String getDescription() {
            return "hang";
        }

        @Override
        public Map<String, Object> toParams() {
            return Collections.emptyMap();
        }

        @Override
        public Object execute(Object input) {
            try {
                Thread.sleep(30_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                interrupted.countDown();
            }
            return ToolResultPayload.text("late");
        }
    }

    private static final class SharedContextTool implements ContextIsolatableTool {
        private AgentContext agentContext;
        private final AtomicReference<String> leakedRequestId = new AtomicReference<>();

        public void setAgentContext(AgentContext agentContext) {
            this.agentContext = agentContext;
        }

        public AgentContext getAgentContext() {
            return agentContext;
        }

        @Override
        public BaseTool isolateFor(AgentContext context) {
            SharedContextTool copy = new SharedContextTool();
            copy.setAgentContext(context);
            return copy;
        }

        @Override
        public String getName() {
            return "shared";
        }

        @Override
        public String getDescription() {
            return "shared";
        }

        @Override
        public Map<String, Object> toParams() {
            return Collections.emptyMap();
        }

        @Override
        public Object execute(Object input) {
            String expected = agentContext == null ? null : agentContext.getRequestId();
            try {
                Thread.sleep(2L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String actual = agentContext == null ? null : agentContext.getRequestId();
            if (expected != null && !expected.equals(actual)) {
                leakedRequestId.compareAndSet(null, expected + "->" + actual);
            }
            return actual;
        }
    }

    private static final class LongRunningTool implements ContextIsolatableTool {
        private AgentContext agentContext;

        public void setAgentContext(AgentContext agentContext) {
            this.agentContext = agentContext;
        }

        public AgentContext getAgentContext() {
            return agentContext;
        }

        @Override
        public BaseTool isolateFor(AgentContext context) {
            LongRunningTool copy = new LongRunningTool();
            copy.setAgentContext(context);
            return copy;
        }

        @Override
        public String getName() {
            return "long_running";
        }

        @Override
        public String getDescription() {
            return "long_running";
        }

        @Override
        public Map<String, Object> toParams() {
            return Collections.emptyMap();
        }

        @Override
        public Object execute(Object input) {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return agentContext == null ? null : agentContext.getRequestId();
        }
    }

    private static final class NoopPrinter implements Printer {
        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        }

        @Override
        public void send(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, String digitalEmployee, Boolean isFinal) {
        }

        @Override
        public void send(String messageType, Object message) {
        }

        @Override
        public void send(String messageType, Object message, String digitalEmployee) {
        }

        @Override
        public void send(String messageId, String messageType, Object message, Boolean isFinal) {
        }

        @Override
        public void sendWithResultMap(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, Boolean isFinal) {
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(AgentType agentType) {
        }
    }
}

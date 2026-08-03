package org.wwz.ai.test.domain;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.config.executor.BoundedVirtualThreadExecutor;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 有界虚拟线程执行器在工具、子 Agent 和 JDBC 等待场景下的回归测试。
 */
public class VirtualThreadToolExecutionRegressionTest {

    @Test
    public void shouldRunParallelToolsAndReleasePermitAfterFailure() throws Exception {
        BoundedVirtualThreadExecutor taskExecutor = new BoundedVirtualThreadExecutor("task", 2, "test-task-");
        BoundedVirtualThreadExecutor toolExecutor = new BoundedVirtualThreadExecutor("tool", 2, "test-tool-");
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ToolCollection tools = new ToolCollection();
        tools.addTool(new BlockingTool(started, release));
        tools.addTool(new FailingTool());
        AgentContext context = newContext("parallel-tools", tools, taskExecutor, toolExecutor, 5L);
        TestAgent agent = new TestAgent(context);

        try {
            List<ToolCall> batch = List.of(toolCall("blocking-1", "blocking"),
                    toolCall("blocking-2", "blocking"));
            CompletableFuture<Map<String, String>> result = CompletableFuture.supplyAsync(
                    () -> agent.exposeExecuteTools(batch));

            Assert.assertTrue("两个阻塞工具应同时进入虚拟线程执行器",
                    started.await(2, TimeUnit.SECONDS));
            release.countDown();
            Map<String, String> outcomes = result.get(2, TimeUnit.SECONDS);
            Assert.assertEquals(2, outcomes.size());
            Assert.assertEquals("ok", outcomes.get("blocking-1"));
            Assert.assertEquals("ok", outcomes.get("blocking-2"));

            Map<String, String> failure = agent.exposeExecuteTools(
                    Collections.singletonList(toolCall("failure-1", "failure")));
            Assert.assertTrue(failure.get("failure-1").contains("Error"));
            Map<String, String> afterFailure = agent.exposeExecuteTools(
                    Collections.singletonList(toolCall("after-failure", "blocking")));
            Assert.assertEquals("ok", afterFailure.get("after-failure"));
            waitUntil(() -> toolExecutor.getRunningTasks() == 0, 2_000L);
        } finally {
            toolExecutor.shutdownNow();
            taskExecutor.shutdownNow();
        }
    }

    @Test
    public void shouldCancelTimedOutToolAndReusePermit() throws Exception {
        BoundedVirtualThreadExecutor taskExecutor = new BoundedVirtualThreadExecutor("task-timeout", 1, "test-task-timeout-");
        BoundedVirtualThreadExecutor toolExecutor = new BoundedVirtualThreadExecutor("tool-timeout", 1, "test-tool-timeout-");
        CountDownLatch interrupted = new CountDownLatch(1);
        ToolCollection tools = new ToolCollection();
        tools.addTool(new HangingTool(interrupted));
        tools.addTool(new QuickTool());
        AgentContext context = newContext("timeout-tools", tools, taskExecutor, toolExecutor, 1L);
        TestAgent agent = new TestAgent(context);

        try {
            Map<String, String> timeout = agent.exposeExecuteTools(
                    Collections.singletonList(toolCall("hang-1", "hang")));
            Assert.assertTrue(timeout.get("hang-1").contains("超时")
                    || timeout.get("hang-1").contains("TIMEOUT"));
            Assert.assertTrue("工具超时后应中断外部等待", interrupted.await(2, TimeUnit.SECONDS));
            waitUntil(() -> toolExecutor.getRunningTasks() == 0, 2_000L);

            Map<String, String> afterTimeout = agent.exposeExecuteTools(
                    Collections.singletonList(toolCall("quick-1", "quick")));
            Assert.assertEquals("quick-ok", afterTimeout.get("quick-1"));
            Assert.assertTrue(toolExecutor.snapshot().cancelledTasks() >= 1);
        } finally {
            toolExecutor.shutdownNow();
            taskExecutor.shutdownNow();
        }
    }

    @Test
    public void shouldKeepNestedAgentAndJdbcCapacityIndependent() throws Exception {
        BoundedVirtualThreadExecutor taskExecutor = new BoundedVirtualThreadExecutor("task-nested", 2, "test-task-nested-");
        BoundedVirtualThreadExecutor toolExecutor = new BoundedVirtualThreadExecutor("tool-nested", 2, "test-tool-nested-");
        ToolCollection tools = new ToolCollection();
        AtomicInteger nestedRuns = new AtomicInteger();
        tools.addTool(new NestedWorkTool(nestedRuns));
        tools.addTool(new NestedAgentTool(tools));
        AgentContext context = newContext("nested-tools", tools, taskExecutor, toolExecutor, 5L);
        TestAgent agent = new TestAgent(context);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:h2:mem:virtual_thread_jdbc;DB_CLOSE_DELAY=-1");
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setMinimumIdle(0);
        hikariConfig.setConnectionTimeout(3_000L);
        try (HikariDataSource dataSource = new HikariDataSource(hikariConfig)) {
            try {
                Map<String, String> outcomes = agent.exposeExecuteTools(List.of(
                        toolCall("nested-1", AgentDispatchTool.NAME),
                        toolCall("nested-2", AgentDispatchTool.NAME)));
                Assert.assertEquals(2, outcomes.size());
                Assert.assertEquals("agent-ok", outcomes.get("nested-1"));
                Assert.assertEquals("agent-ok", outcomes.get("nested-2"));
                Assert.assertEquals(2, nestedRuns.get());

                CountDownLatch firstAcquired = new CountDownLatch(1);
                CountDownLatch releaseFirst = new CountDownLatch(1);
                CountDownLatch secondAcquired = new CountDownLatch(1);
                CompletableFuture<Void> first = AgentExecutorSupport.supplyAsync(toolExecutor, "jdbc-first", () -> {
                    try (Connection ignored = dataSource.getConnection()) {
                        firstAcquired.countDown();
                        await(releaseFirst);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    return null;
                });
                Assert.assertTrue(firstAcquired.await(2, TimeUnit.SECONDS));
                CompletableFuture<Void> second = AgentExecutorSupport.supplyAsync(toolExecutor, "jdbc-second", () -> {
                    try (Connection ignored = dataSource.getConnection()) {
                        secondAcquired.countDown();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    return null;
                });
                Assert.assertFalse("JDBC 连接池只有一个连接时，第二个虚拟线程应等待连接",
                        secondAcquired.await(300, TimeUnit.MILLISECONDS));
                releaseFirst.countDown();
                first.get(2, TimeUnit.SECONDS);
                second.get(2, TimeUnit.SECONDS);
                Assert.assertTrue(secondAcquired.await(1, TimeUnit.SECONDS));
            } finally {
                toolExecutor.shutdownNow();
                taskExecutor.shutdownNow();
            }
        }
    }

    private static AgentContext newContext(String requestId,
                                            ToolCollection tools,
                                            BoundedVirtualThreadExecutor taskExecutor,
                                            BoundedVirtualThreadExecutor toolExecutor,
                                            long timeoutSeconds) {
        ReactorRuntimeDependencies dependencies = ReactorRuntimeDependencies.builder()
                .taskExecutor(taskExecutor)
                .toolExecutor(toolExecutor)
                .toolBatchTimeoutSeconds(timeoutSeconds)
                .build();
        AgentContext context = AgentContext.builder()
                .requestId(requestId)
                .sessionId("session-" + requestId)
                .query(requestId)
                .toolCollection(tools)
                .runtimeDependencies(dependencies)
                .build();
        tools.setAgentContext(context);
        return context;
    }

    private static ToolCall toolCall(String id, String name) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .function(ToolCall.Function.builder().name(name).arguments("{}").build())
                .build();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitUntil(Check check, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!check.matches() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        Assert.assertTrue("等待执行器状态收敛超时", check.matches());
    }

    private static final class TestAgent extends BaseAgent {
        private TestAgent(AgentContext context) {
            setContext(context);
            setAvailableTools(context.getToolCollection());
            setPrinter(new NoopPrinter());
        }

        private Map<String, String> exposeExecuteTools(List<ToolCall> commands) {
            return executeTools(commands);
        }

        @Override
        public String step() {
            return "";
        }
    }

    private static final class BlockingTool implements BaseTool {
        private final CountDownLatch started;
        private final CountDownLatch release;

        private BlockingTool(CountDownLatch started, CountDownLatch release) {
            this.started = started;
            this.release = release;
        }

        @Override
        public String getName() {
            return "blocking";
        }

        @Override
        public String getDescription() {
            return "blocking";
        }

        @Override
        public Map<String, Object> toParams() {
            return Collections.emptyMap();
        }

        @Override
        public Object execute(Object input) {
            started.countDown();
            await(release);
            return ToolResultPayload.text("ok");
        }
    }

    private static final class FailingTool implements BaseTool {
        @Override
        public String getName() {
            return "failure";
        }

        @Override
        public String getDescription() {
            return "failure";
        }

        @Override
        public Map<String, Object> toParams() {
            return Collections.emptyMap();
        }

        @Override
        public Object execute(Object input) {
            throw new IllegalStateException("expected tool failure");
        }
    }

    private static final class HangingTool implements BaseTool {
        private final CountDownLatch interrupted;

        private HangingTool(CountDownLatch interrupted) {
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

    private static final class QuickTool implements BaseTool {
        @Override
        public String getName() {
            return "quick";
        }

        @Override
        public String getDescription() {
            return "quick";
        }

        @Override
        public Map<String, Object> toParams() {
            return Collections.emptyMap();
        }

        @Override
        public Object execute(Object input) {
            return ToolResultPayload.text("quick-ok");
        }
    }

    private static final class NestedWorkTool implements BaseTool {
        private final AtomicInteger runs;

        private NestedWorkTool(AtomicInteger runs) {
            this.runs = runs;
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
            runs.incrementAndGet();
            return ToolResultPayload.text("nested-ok");
        }
    }

    private static final class NestedAgentTool implements BaseTool {
        private final ToolCollection tools;

        private NestedAgentTool(ToolCollection tools) {
            this.tools = tools;
        }

        @Override
        public String getName() {
            return AgentDispatchTool.NAME;
        }

        @Override
        public String getDescription() {
            return "nested agent";
        }

        @Override
        public Map<String, Object> toParams() {
            return Collections.emptyMap();
        }

        @Override
        public Object execute(Object input) {
            AgentContext parent = tools.getAgentContext();
            AgentContext child = AgentContext.builder()
                    .requestId(parent.getRequestId() + ":child")
                    .sessionId(parent.getSessionId())
                    .query("nested")
                    .toolCollection(tools)
                    .runtimeDependencies(parent.getRuntimeDependencies())
                    .build();
            TestAgent childAgent = new TestAgent(child);
            Map<String, String> result = childAgent.exposeExecuteTools(
                    Collections.singletonList(toolCall("nested-work-" + System.nanoTime(), "nested_work")));
            return result.values().stream().findFirst()
                    .map(value -> ToolResultPayload.text("agent-ok"))
                    .orElseGet(() -> ToolResultPayload.failure("nested failed", "nested failed", null, "nested failed"));
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

    @FunctionalInterface
    private interface Check {
        boolean matches();
    }
}

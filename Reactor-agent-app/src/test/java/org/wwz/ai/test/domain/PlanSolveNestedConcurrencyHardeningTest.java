package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ExecutorAgent;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.SubTaskExecutionResult;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.Step2PlanExecuteNode;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PlanSolve 嵌套并发硬化测试。
 * 聚焦批次限流、child context 隔离、artifact 视图语义与父状态归并。
 */
public class PlanSolveNestedConcurrencyHardeningTest {

    @Test
    public void shouldExecuteParallelTasksInConfiguredBatchesAndMergeDeterministicState() throws Exception {
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "plannerMaxParallelTasks", 2);
        ReflectionTestUtils.setField(reactorConfig, "plannerMaxSteps", 10);
        ReflectionTestUtils.setField(reactorConfig, "executorModelName", "test-model");
        ReflectionTestUtils.setField(reactorConfig, "maxObserve", "2048");
        ReflectionTestUtils.setField(reactorConfig, "taskPrePrompt", "");
        reactorConfig.setExecutorSystemPromptMap("{}");
        reactorConfig.setExecutorNextStepPromptMap("{}");
        reactorConfig.setExecutorSopPromptMap("{}");
        ReactorRuntimeDependencies runtimeDependencies = ReactorRuntimeTestSupport.runtimeDependencies(
                reactorConfig,
                null,
                new MockEnvironment()
                        .withProperty("llm.default.base_url", "http://127.0.0.1")
                        .withProperty("llm.default.apikey", "test-key")
                        .withProperty("llm.default.model", "test-model")
        );

        AgentContext parentContext = AgentContext.builder()
                .requestId("req-plansolve-001")
                .sessionId("session-plansolve-001")
                .query("执行并发任务")
                .task("")
                .printer(new SilentPrinter())
                .runtimeDependencies(runtimeDependencies)
                .productFiles(new ArrayList<>())
                .taskProductFiles(new ArrayList<>())
                .historyDialogue("")
                .basePrompt("")
                .sopPrompt("")
                .dateInfo("2026-05-10")
                .toolCollection(new ToolCollection())
                .build();
        parentContext.getToolCollection().setAgentContext(parentContext);
        parentContext.getProductFiles().add(org.wwz.ai.domain.agent.runtime.dto.File.builder()
                .fileName("parent-session.md")
                .ossUrl("https://session")
                .domainUrl("https://session")
                .isInternalFile(false)
                .build());
        parentContext.getTaskProductFiles().add(org.wwz.ai.domain.agent.runtime.dto.File.builder()
                .fileName("parent-task.md")
                .ossUrl("https://task")
                .domainUrl("https://task")
                .isInternalFile(false)
                .build());

        ExecutorAgent parentExecutor = new ExecutorAgent(parentContext);
        parentExecutor.getMemory().clear();
        parentExecutor.getMemory().addMessage(Message.userMessage("父任务上下文", null));

        AtomicInteger currentConcurrency = new AtomicInteger();
        AtomicInteger maxObservedConcurrency = new AtomicInteger();
        List<String> childObservedTasks = new CopyOnWriteArrayList<>();
        Semaphore started = new Semaphore(0);
        Semaphore release = new Semaphore(0);

        TestStep2PlanExecuteNode node = new TestStep2PlanExecuteNode(
                reactorConfig,
                Executors.newFixedThreadPool(4),
                currentConcurrency,
                maxObservedConcurrency,
                childObservedTasks,
                started,
                release
        );

        List<String> tasks = List.of("你的任务是：task-1", "你的任务是：task-2", "你的任务是：task-3");
        AgentRequest request = AgentRequest.builder()
                .requestId(parentContext.getRequestId())
                .sessionId(parentContext.getSessionId())
                .query(parentContext.getQuery())
                .outputStyle("html")
                .build();

        Thread executionThread = new Thread(() -> node.executeParallelTasks(parentContext, request, parentExecutor, tasks));
        executionThread.start();

        started.acquireUninterruptibly(2);
        Assert.assertEquals(2, maxObservedConcurrency.get());
        Assert.assertEquals(2, childObservedTasks.size());

        release.release(2);
        started.acquireUninterruptibly(1);
        Assert.assertEquals(2, maxObservedConcurrency.get());
        release.release(1);
        executionThread.join();

        List<SubTaskExecutionResult> results = node.getCapturedResults();
        Assert.assertEquals(3, results.size());
        Assert.assertEquals(List.of("你的任务是：task-1", "你的任务是：task-2", "你的任务是：task-3"),
                results.stream().map(SubTaskExecutionResult::getTask).toList());
        Assert.assertEquals(AgentState.ERROR, node.reduceState(results));

        node.mergeIntoParent(parentExecutor, results);
        Assert.assertEquals(AgentState.ERROR, parentExecutor.getState());
        Assert.assertEquals(4, parentExecutor.getMemory().size());
        Assert.assertEquals("child-observation:你的任务是：task-1", parentExecutor.getMemory().get(1).getContent());
        Assert.assertEquals("child-observation:你的任务是：task-2", parentExecutor.getMemory().get(2).getContent());
        Assert.assertEquals("child-observation:你的任务是：task-3", parentExecutor.getMemory().get(3).getContent());
        Assert.assertEquals("parent-task.md", parentContext.getTaskProductFiles().get(0).getFileName());
        Assert.assertEquals(1, parentContext.getTaskProductFiles().size());
        Assert.assertEquals(1, parentContext.getProductFiles().size());
        Assert.assertEquals("result:你的任务是：task-1\nresult:你的任务是：task-2\nresult:你的任务是：task-3",
                node.joinResults(results));
    }

    private static final class TestStep2PlanExecuteNode extends Step2PlanExecuteNode {
        private final Executor taskExecutor;
        private final AtomicInteger currentConcurrency;
        private final AtomicInteger maxObservedConcurrency;
        private final List<String> childObservedTasks;
        private final Semaphore started;
        private final Semaphore release;
        private volatile List<SubTaskExecutionResult> capturedResults = List.of();

        private TestStep2PlanExecuteNode(ReactorConfig reactorConfig,
                                         Executor taskExecutor,
                                         AtomicInteger currentConcurrency,
                                         AtomicInteger maxObservedConcurrency,
                                         List<String> childObservedTasks,
                                         Semaphore started,
                                         Semaphore release) {
            this.taskExecutor = taskExecutor;
            this.currentConcurrency = currentConcurrency;
            this.maxObservedConcurrency = maxObservedConcurrency;
            this.childObservedTasks = childObservedTasks;
            this.started = started;
            this.release = release;
            ReflectionTestUtils.setField(this, "reactorConfig", reactorConfig);
        }

        @Override
        protected Executor resolveTaskExecutor(AgentContext agentContext) {
            return taskExecutor;
        }

        @Override
        protected SubTaskExecutionResult executeSingleParallelTask(AgentContext parentContext,
                                                                  AgentRequest request,
                                                                  ExecutorAgent parentExecutor,
                                                                  String task) {
            AgentContext childContext = parentContext.forkForParallelTask(task);
            childObservedTasks.add(childContext.getTask());
            int active = currentConcurrency.incrementAndGet();
            maxObservedConcurrency.accumulateAndGet(active, Math::max);
            started.release();
            release.acquireUninterruptibly();
            currentConcurrency.decrementAndGet();

            AgentState state = task.endsWith("task-2") ? AgentState.ERROR : AgentState.FINISHED;
            return SubTaskExecutionResult.builder()
                    .task(task)
                    .taskResult("result:" + task)
                    .state(state)
                    .memoryIncrementMessages(List.of(Message.builder()
                            .role(RoleType.ASSISTANT)
                            .content("child-observation:" + task)
                            .build()))
                    .build();
        }

        @Override
        protected List<SubTaskExecutionResult> executeParallelTasks(AgentContext parentContext,
                                                                    AgentRequest request,
                                                                    ExecutorAgent parentExecutor,
                                                                    List<String> tasks) {
            capturedResults = super.executeParallelTasks(parentContext, request, parentExecutor, tasks);
            return capturedResults;
        }

        private List<SubTaskExecutionResult> getCapturedResults() {
            return capturedResults;
        }

        private AgentState reduceState(List<SubTaskExecutionResult> results) {
            return reduceParentState(results);
        }

        private void mergeIntoParent(ExecutorAgent parentExecutor, List<SubTaskExecutionResult> results) {
            mergeChildResultsIntoParent(parentExecutor, results);
        }

        private String joinResults(List<SubTaskExecutionResult> results) {
            return joinTaskResults(results);
        }
    }

    private static final class SilentPrinter implements Printer {

        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        }

        @Override
        public void send(String messageId, String messageType, Object message, java.util.Map<String, Object> extraResultMap, String digitalEmployee, Boolean isFinal) {
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
        public void sendWithResultMap(String messageId, String messageType, Object message, java.util.Map<String, Object> extraResultMap, Boolean isFinal) {
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, java.util.Map<String, Object> extraResultMap) {
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(org.wwz.ai.domain.agent.runtime.enums.AgentType agentType) {
        }
    }
}

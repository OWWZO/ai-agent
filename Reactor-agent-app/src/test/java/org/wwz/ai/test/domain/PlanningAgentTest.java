package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.PlanningAgent;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.common.PlanningTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.ledger.model.AgentRunState;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * PlanningAgent 回归测试。
 * 聚焦 PlanSolve 规划侧在普通 replan 与兼容模式下的主链路语义。
 */
public class PlanningAgentTest {

    @Test
    public void shouldDriveOrdinaryReplanFromCreateToFinish() {
        PlanningAgent agent = newPlanningAgent("0");
        agent.setToolCalls(List.of());
        PlanningTool tool = agent.getPlanningTool();

        tool.execute(command(
                "command", "create",
                "title", "普通 replan",
                "steps", List.of("步骤一", "步骤二")
        ));

        Assert.assertEquals("步骤一", agent.act());

        tool.execute(command(
                "command", "mark_step",
                "step_index", 0,
                "step_status", "completed",
                "step_notes", "已完成"
        ));
        tool.execute(command(
                "command", "update",
                "title", "重排后的计划",
                "steps", List.of("新步骤A", "新步骤B")
        ));

        Assert.assertEquals("新步骤A", agent.act());
        Assert.assertEquals("重排后的计划", tool.getPlan().getTitle());
        Assert.assertEquals(List.of("步骤一", "新步骤A", "新步骤B"), tool.getPlan().getSteps());

        tool.execute(command(
                "command", "mark_step",
                "step_index", 1,
                "step_status", "completed",
                "step_notes", "重排后首步完成"
        ));
        Assert.assertEquals("新步骤B", agent.act());

        tool.execute(command(
                "command", "mark_step",
                "step_index", 2,
                "step_status", "completed",
                "step_notes", "全部完成"
        ));

        Assert.assertEquals("finish", agent.act());
        Assert.assertEquals(List.of("completed", "completed", "completed"), tool.getPlan().getStepStatus());
    }

    @Test
    public void shouldEmitNextOrdinaryReplanTaskOnlyOncePerAct() {
        RecordingPrinter printer = new RecordingPrinter();
        PlanningAgent agent = newPlanningAgent("0", printer);
        agent.setToolCalls(List.of());
        PlanningTool tool = agent.getPlanningTool();

        tool.execute(command(
                "command", "create",
                "title", "普通 replan",
                "steps", List.of("步骤一", "步骤二")
        ));
        Assert.assertEquals("步骤一", agent.act());

        tool.execute(command(
                "command", "mark_step",
                "step_index", 0,
                "step_status", "completed",
                "step_notes", "第一步完成"
        ));

        Assert.assertEquals("步骤二", agent.act());
        List<String> taskEvents = printer.events.stream()
                .filter(event -> "task".equals(event.messageType))
                .map(event -> String.valueOf(event.message))
                .collect(Collectors.toList());
        Assert.assertEquals(List.of("步骤一", "步骤二"), taskEvents);
    }

    @Test
    public void shouldRejectRedispatchingSameOrdinaryReplanTaskWithoutPlanMutation() {
        PlanningAgent agent = newPlanningAgent("0");
        agent.setToolCalls(List.of());
        PlanningTool tool = agent.getPlanningTool();

        tool.execute(command(
                "command", "create",
                "title", "普通 replan",
                "steps", List.of("步骤一", "步骤二")
        ));
        Assert.assertEquals("步骤一", agent.act());

        try {
            agent.act();
            Assert.fail("未发生计划推进时，不应重复下发同一任务");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("already dispatched"));
        }
    }

    @Test
    public void shouldEmitStablePlannerRoundIdOnThoughtAndPlan() {
        RecordingPrinter printer = new RecordingPrinter();
        PlanningAgent agent = newPlanningAgent("0", printer);
        agent.setToolCalls(List.of(planningToolCall(
                "plan-call-round-001",
                command(
                        "command", "create",
                        "title", "普通 replan",
                        "steps", List.of("步骤一", "步骤二")
                )
        )));

        agent.think();
        agent.act();

        List<Event> plannerEvents = printer.events.stream()
                .filter(event -> "plan_thought".equals(event.messageType) || "plan".equals(event.messageType))
                .toList();

        Assert.assertEquals("plan_thought", plannerEvents.get(0).messageType);
        Assert.assertEquals("1", plannerEvents.get(0).plannerRoundId);
        Assert.assertEquals("plan", plannerEvents.get(1).messageType);
        Assert.assertEquals("1", plannerEvents.get(1).plannerRoundId);
    }

    @Test
    public void shouldAdvanceCompatibilityPlanOneStepPerCycle() {
        PlanningAgent agent = newPlanningAgent("1");
        PlanningTool tool = agent.getPlanningTool();

        tool.execute(command(
                "command", "create",
                "title", "兼容计划",
                "steps", List.of("步骤一", "步骤二", "步骤三")
        ));

        Assert.assertTrue(agent.think());
        Assert.assertEquals("步骤二", agent.act());
        Assert.assertEquals(List.of("completed", "in_progress", "not_started"), tool.getPlan().getStepStatus());

        Assert.assertTrue(agent.think());
        Assert.assertEquals("步骤三", agent.act());
        Assert.assertEquals(List.of("completed", "completed", "in_progress"), tool.getPlan().getStepStatus());

        Assert.assertTrue(agent.think());
        Assert.assertEquals("finish", agent.act());
        Assert.assertEquals(List.of("completed", "completed", "completed"), tool.getPlan().getStepStatus());
    }

    @Test
    public void shouldNotRedispatchCurrentTaskWhenPlanningToolCallFails() {
        RecordingPrinter printer = new RecordingPrinter();
        PlanningAgent agent = newPlanningAgent("0", printer);
        agent.setToolCalls(List.of());
        PlanningTool tool = agent.getPlanningTool();

        tool.execute(command(
                "command", "create",
                "title", "普通 replan",
                "steps", List.of("步骤一", "步骤二")
        ));
        Assert.assertEquals("步骤一", agent.act());

        agent.setState(AgentState.IDLE);
        agent.setToolCalls(List.of(planningToolCall(
                "plan-call-invalid-001",
                command(
                        "command", "mark_step",
                        "step_index", 5,
                        "step_status", "completed"
                )
        )));

        String result = agent.act();

        Assert.assertEquals("Tool planning Error.", result);
        Assert.assertEquals("步骤一", tool.getPlan().getCurrentStep());
        Assert.assertEquals(List.of("步骤一"), printer.events.stream()
                .filter(event -> "task".equals(event.messageType))
                .map(event -> String.valueOf(event.message))
                .collect(Collectors.toList()));
    }

    @Test
    public void shouldInjectHistoryDialogueOnlyIntoSystemPrompt() {
        PlanningAgent agent = newPlanningAgent("0", new RecordingPrinter(), "历史上下文片段");

        Assert.assertTrue(agent.getSystemPrompt().contains("历史上下文片段"));
        Assert.assertFalse(agent.getNextStepPrompt().contains("历史上下文片段"));
        Assert.assertFalse(agent.getNextStepPrompt().contains("<history_dialogue>"));
    }

    private PlanningAgent newPlanningAgent(String closeUpdate) {
        return newPlanningAgent(closeUpdate, new RecordingPrinter());
    }

    private PlanningAgent newPlanningAgent(String closeUpdate, RecordingPrinter printer) {
        return newPlanningAgent(closeUpdate, printer, "");
    }

    private PlanningAgent newPlanningAgent(String closeUpdate, RecordingPrinter printer, String historyDialogue) {
        ReactorConfig reactorConfig = buildReactorConfig(closeUpdate);
        ReactorRuntimeDependencies runtimeDependencies = ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig);

        ToolCollection toolCollection = new ToolCollection();
        AgentExecutionRecorder executionRecorder = new InMemoryAgentExecutionRecorder();
        AgentRunState agentRunState = AgentRunState.builder().runId(100L).build();
        agentRunState.bindCurrentLlmInvocationId(10L);
        AgentContext context = AgentContext.builder()
                .requestId("req-planning-agent-" + closeUpdate)
                .sessionId("session-planning-agent-" + closeUpdate)
                .query("测试普通 replan")
                .dateInfo("2026-05-03")
                .basePrompt("")
                .sopPrompt("")
                .historyDialogue(historyDialogue)
                .printer(printer)
                .toolCollection(toolCollection)
                .productFiles(new ArrayList<>())
                .taskProductFiles(new ArrayList<>())
                .executionRecorder(executionRecorder)
                .agentRunState(agentRunState)
                .isStream(false)
                .runtimeDependencies(runtimeDependencies)
                .build();
        toolCollection.setAgentContext(context);
        PlanningAgent agent = new PlanningAgent(context);
        mockPlannerLlm(agent);
        return agent;
    }

    private ReactorConfig buildReactorConfig(String closeUpdate) {
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setPlannerSystemPromptMap("{}");
        reactorConfig.setPlannerNextStepPromptMap("{}");
        ReflectionTestUtils.setField(reactorConfig, "plannerModelName", "test-planner-model");
        ReflectionTestUtils.setField(reactorConfig, "plannerMaxSteps", 10);
        ReflectionTestUtils.setField(reactorConfig, "planningCloseUpdate", closeUpdate);
        ReflectionTestUtils.setField(reactorConfig, "llmSettingsMap", Map.of(
                "test-planner-model",
                LLMSettings.builder()
                        .model("test-planner-model")
                        .maxTokens(1024)
                        .temperature(0)
                        .baseUrl("http://127.0.0.1")
                        .interfaceUrl("/v1/chat/completions")
                        .functionCallType("function_call")
                        .apiKey("test-key")
                        .maxInputTokens(4096)
                        .build()
        ));
        return reactorConfig;
    }

    private void mockPlannerLlm(PlanningAgent agent) {
        LLM llm = Mockito.mock(LLM.class);
        Mockito.when(llm.askTool(Mockito.any(), Mockito.anyList(), Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyInt()))
                .thenAnswer(invocation -> {
                    List<ToolCall> toolCalls = agent.getToolCalls();
                    return CompletableFuture.completedFuture(LLM.ToolCallResponse.builder()
                            .content("mock planning thought")
                            .toolCalls(toolCalls == null ? List.of() : toolCalls)
                            .build());
                });
        agent.setLlm(llm);
    }

    private Map<String, Object> command(Object... kvPairs) {
        java.util.LinkedHashMap<String, Object> params = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            params.put(String.valueOf(kvPairs[i]), kvPairs[i + 1]);
        }
        return params;
    }

    private ToolCall planningToolCall(String toolCallId, Map<String, Object> params) {
        return ToolCall.builder()
                .id(toolCallId)
                .type("function")
                .function(ToolCall.Function.builder()
                        .name("planning")
                        .arguments(com.alibaba.fastjson.JSON.toJSONString(params))
                        .build())
                .build();
    }

    private static class RecordingPrinter implements Printer {
        private final List<Event> events = new ArrayList<>();

        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
            events.add(new Event(messageType, message, null));
        }

        @Override
        public void send(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, String digitalEmployee, Boolean isFinal) {
            events.add(new Event(messageType, message, readPlannerRoundId(extraResultMap)));
        }

        @Override
        public void send(String messageType, Object message) {
            events.add(new Event(messageType, message, null));
        }

        @Override
        public void send(String messageType, Object message, String digitalEmployee) {
            events.add(new Event(messageType, message, null));
        }

        @Override
        public void send(String messageId, String messageType, Object message, Boolean isFinal) {
            events.add(new Event(messageType, message, null));
        }

        @Override
        public void sendWithResultMap(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, Boolean isFinal) {
            events.add(new Event(messageType, message, readPlannerRoundId(extraResultMap)));
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
            events.add(new Event(messageType, message, readPlannerRoundId(extraResultMap)));
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(AgentType agentType) {
        }

        private String readPlannerRoundId(Map<String, Object> extraResultMap) {
            if (extraResultMap == null) {
                return null;
            }
            Object plannerRoundId = extraResultMap.get("plannerRoundId");
            return plannerRoundId == null ? null : String.valueOf(plannerRoundId);
        }
    }

    private record Event(String messageType, Object message, String plannerRoundId) {
    }

    /**
     * 最小账本桩。
     * 仅为规划测试补齐 toolInvocationId 映射，避免 plannerRoundId 在无持久化环境下丢失。
     */
    private static class InMemoryAgentExecutionRecorder implements AgentExecutionRecorder {
        private final AtomicLong nextToolInvocationId = new AtomicLong(1);

        @Override
        public Long createRun(DialogueRunStartRecord record) {
            return 1L;
        }

        @Override
        public void finishRun(DialogueRunFinishRecord record) {
        }

        @Override
        public Long createLlmInvocation(LlmInvocationStartRecord record) {
            return 1L;
        }

        @Override
        public void finishLlmInvocation(LlmInvocationFinishRecord record) {
        }

        @Override
        public Map<String, Long> createToolInvocations(ToolInvocationBatchStartRecord record) {
            Map<String, Long> mapping = new LinkedHashMap<>();
            if (record == null || record.getItems() == null) {
                return mapping;
            }
            for (ToolInvocationBatchStartRecord.Item item : record.getItems()) {
                if (item == null || item.getToolCallId() == null) {
                    continue;
                }
                mapping.put(item.getToolCallId(), nextToolInvocationId.getAndIncrement());
            }
            return mapping;
        }

        @Override
        public void finishToolInvocation(ToolInvocationFinishRecord record) {
        }

        @Override
        public void recordArtifacts(List<ArtifactRecordCommand> records) {
        }

        @Override
        public void recordArtifactsOrThrow(List<ArtifactRecordCommand> records) {
        }
    }
}

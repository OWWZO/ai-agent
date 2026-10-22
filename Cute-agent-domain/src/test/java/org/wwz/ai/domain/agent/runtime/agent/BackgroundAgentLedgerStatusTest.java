package org.wwz.ai.domain.agent.runtime.agent;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.ledger.model.AgentRunState;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.subagent.BackgroundSubAgentExecutor;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentResult;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTaskRegistry;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台派发 ACK 的账本语义回归（plans/032）：
 * 管线对后台 ACK 只能写 provisional（RUNNING + finished_at=null），
 * 真正的终态必须由 settle 覆盖写，否则 mapper 守卫挡掉结算 observation，
 * 历史回放把陈旧 ACK 当「运行中」展示（elapsed 无限累加）。
 */
public class BackgroundAgentLedgerStatusTest {

    private static final String TOOL_CALL_ID = "call-bg-1";
    private static final long TOOL_INVOCATION_ID = 555L;

    private AgentExecutionRecorder recorder;
    private AgentContext context;
    private ToolExecutionPipeline pipeline;

    @Test
    public void backgroundAckShouldNotWriteTerminalLedgerStatus() {
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        RuntimeBackgroundTask task = registry.registerLocalAgent("探索前端", "general-purpose", "scan ui");
        Map<String, Object> ack = backgroundAck(task.getId());
        setUpHarness(registry, AgentDispatchTool.NAME, () -> ToolResultPayload.fromData(ack));

        ToolExecutionOutcome outcome = pipeline.executeOne(newToolCall(AgentDispatchTool.NAME));
        Assert.assertTrue(outcome.isSuccess());

        ToolInvocationFinishRecord record = captureSingleFinish();
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_RUNNING), record.getStatus());
        Assert.assertNull("后台 ACK 不得写 finished_at，否则 settle 过不了 mapper 守卫", record.getFinishedAt());
        Assert.assertTrue(record.getLlmObservation().contains("\"run_in_background\":true"));
        Assert.assertTrue(record.getLlmObservation().contains("\"status\":\"running\""));
    }

    @Test
    public void backgroundSettlementShouldOverwriteProvisionalAckRow() {
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        RuntimeBackgroundTask task = registry.registerLocalAgent("探索前端", "general-purpose", "scan ui");
        Map<String, Object> ack = backgroundAck(task.getId());
        setUpHarness(registry, AgentDispatchTool.NAME, () -> ToolResultPayload.fromData(ack));

        pipeline.executeOne(newToolCall(AgentDispatchTool.NAME));

        registry.complete(task.getId(), SubAgentResult.builder()
                .status(SubAgentResult.STATUS_COMPLETED)
                .agentId("agent-x")
                .agentType("general-purpose")
                .description("探索前端")
                .content("子 Agent 已完成扫描")
                .totalToolUseCount(3)
                .totalDurationMs(1234L)
                .build());

        AgentDispatchTool.settleLedgerIfTerminal(context, TOOL_CALL_ID, JSON.toJSONString(ack));

        ArgumentCaptor<ToolInvocationFinishRecord> captor =
                ArgumentCaptor.forClass(ToolInvocationFinishRecord.class);
        verify(recorder, times(2)).finishToolInvocation(captor.capture());
        ToolInvocationFinishRecord provisional = captor.getAllValues().get(0);
        ToolInvocationFinishRecord settlement = captor.getAllValues().get(1);
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_RUNNING), provisional.getStatus());
        Assert.assertNull(provisional.getFinishedAt());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_SUCCESS), settlement.getStatus());
        Assert.assertNotNull(settlement.getFinishedAt());
        Assert.assertTrue(settlement.getLlmObservation().contains("\"status\":\"completed\""));
        Assert.assertTrue(settlement.getLlmObservation().contains("子 Agent 已完成扫描"));
        Assert.assertTrue(settlement.getLlmObservation().contains("\"totalDurationMs\":1234"));
    }

    @Test
    public void synchronousAgentDispatchShouldStillWriteTerminalStatus() {
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("tool", AgentDispatchTool.NAME);
        done.put("ok", true);
        done.put("status", "completed");
        done.put("content", "同步完成");
        setUpHarness(registry, AgentDispatchTool.NAME, () -> ToolResultPayload.fromData(done));

        pipeline.executeOne(newToolCall(AgentDispatchTool.NAME));

        ToolInvocationFinishRecord record = captureSingleFinish();
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_SUCCESS), record.getStatus());
        Assert.assertNotNull(record.getFinishedAt());
        Assert.assertTrue(record.getLlmObservation().contains("同步完成"));
    }

    @Test
    public void sendMessageBackgroundAckShouldFollowProvisionalThenSettleFlow() {
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        RuntimeBackgroundTask task = registry.registerLocalAgent("续跑指导", "general-purpose", "continue");
        Map<String, Object> ack = backgroundAck(task.getId());
        setUpHarness(registry, TaskToolNames.SEND_MESSAGE, () -> ToolResultPayload.fromData(ack));

        pipeline.executeOne(newToolCall(TaskToolNames.SEND_MESSAGE));

        registry.fail(task.getId(), SubAgentResult.builder()
                .status(SubAgentResult.STATUS_FAILED)
                .agentId("agent-x")
                .agentType("general-purpose")
                .content("")
                .errorMsg("子 Agent 中途失败")
                .build());

        BackgroundSubAgentExecutor.settleLedgerIfTerminal(
                context, TOOL_CALL_ID, TaskToolNames.SEND_MESSAGE, JSON.toJSONString(ack));

        ArgumentCaptor<ToolInvocationFinishRecord> captor =
                ArgumentCaptor.forClass(ToolInvocationFinishRecord.class);
        verify(recorder, times(2)).finishToolInvocation(captor.capture());
        ToolInvocationFinishRecord provisional = captor.getAllValues().get(0);
        ToolInvocationFinishRecord settlement = captor.getAllValues().get(1);
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_RUNNING), provisional.getStatus());
        Assert.assertNull(provisional.getFinishedAt());
        Assert.assertEquals(TaskToolNames.SEND_MESSAGE, provisional.getToolName());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_FAILED), settlement.getStatus());
        Assert.assertTrue(settlement.getLlmObservation().contains("子 Agent 中途失败"));
    }

    @Test
    public void isBackgroundAckObservationShouldDetectReceiptShape() {
        Assert.assertTrue(BackgroundSubAgentExecutor.isBackgroundAckObservation(
                "{\"tool\":\"Agent\",\"ok\":true,\"status\":\"running\",\"task_id\":\"t1\",\"run_in_background\":true}"));
        Assert.assertFalse(BackgroundSubAgentExecutor.isBackgroundAckObservation(
                "{\"tool\":\"Agent\",\"ok\":true,\"status\":\"completed\",\"content\":\"done\"}"));
        Assert.assertFalse(BackgroundSubAgentExecutor.isBackgroundAckObservation(
                "{\"run_in_background\":false}"));
        Assert.assertFalse(BackgroundSubAgentExecutor.isBackgroundAckObservation(null));
        Assert.assertFalse(BackgroundSubAgentExecutor.isBackgroundAckObservation(""));
        Assert.assertFalse(BackgroundSubAgentExecutor.isBackgroundAckObservation("Error: boom"));
    }

    private ToolInvocationFinishRecord captureSingleFinish() {
        ArgumentCaptor<ToolInvocationFinishRecord> captor =
                ArgumentCaptor.forClass(ToolInvocationFinishRecord.class);
        verify(recorder, times(1)).finishToolInvocation(captor.capture());
        return captor.getValue();
    }

    private void setUpHarness(RuntimeBackgroundTaskRegistry registry,
                              String toolName,
                              Supplier<ToolResultPayload> payloadSupplier) {
        recorder = mock(AgentExecutionRecorder.class);
        AgentRunState runState = AgentRunState.builder().runId(42L).build();
        runState.bindToolInvocationIds(Map.of(TOOL_CALL_ID, TOOL_INVOCATION_ID));
        context = AgentContext.builder()
                .requestId("req-bg-ledger")
                .sessionId("session-bg-ledger")
                .executionRecorder(recorder)
                .agentRunState(runState)
                .backgroundTasks(registry)
                .build();
        ToolCollection tools = new ToolCollection();
        tools.setAgentContext(context);
        tools.addTool(new StubTool(toolName, payloadSupplier));

        BaseAgent agent = mock(BaseAgent.class);
        when(agent.getContext()).thenReturn(context);
        when(agent.getPrinter()).thenReturn(new NoopPrinter());
        when(agent.getAvailableTools()).thenReturn(tools);
        when(agent.getName()).thenReturn("react");
        when(agent.getCurrentStep()).thenReturn(1);
        when(agent.buildFinalLlmObservation(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        pipeline = new ToolExecutionPipeline(agent);
    }

    private static ToolCall newToolCall(String toolName) {
        return ToolCall.builder()
                .id(TOOL_CALL_ID)
                .type("function")
                .function(ToolCall.Function.builder()
                        .name(toolName)
                        .arguments("{\"description\":\"探索前端\",\"prompt\":\"scan ui\"}")
                        .build())
                .build();
    }

    /** 与 BackgroundSubAgentExecutor.submit 同形的 ACK 回执。 */
    private static Map<String, Object> backgroundAck(String taskId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", AgentDispatchTool.NAME);
        data.put("ok", true);
        data.put("status", RuntimeBackgroundTask.STATUS_RUNNING);
        data.put("task_id", taskId);
        data.put("agentId", "agent-x");
        data.put("childSessionId", "sub-x");
        data.put("task_type", RuntimeBackgroundTask.TYPE_LOCAL_AGENT);
        data.put("description", "探索前端");
        data.put("agentType", "general-purpose");
        data.put("run_in_background", true);
        return data;
    }

    private static final class StubTool implements BaseTool {
        private final String name;
        private final Supplier<ToolResultPayload> payloadSupplier;

        private StubTool(String name, Supplier<ToolResultPayload> payloadSupplier) {
            this.name = name;
            this.payloadSupplier = payloadSupplier;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return name;
        }

        @Override
        public Map<String, Object> toParams() {
            return Map.of();
        }

        @Override
        public Object execute(Object input) {
            return payloadSupplier.get();
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
        public void updateAgentType(org.wwz.ai.domain.agent.runtime.enums.AgentType agentType) {
        }
    }
}

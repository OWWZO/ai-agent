package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.ledger.model.AgentRunState;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayTiming;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.agent.ToolExecutionOutcome;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class RuntimeTimingToolPipelineTest {

    @Test
    public void shouldUseInvocationBoundaryTimingForToolCallAndResult() {
        AgentExecutionRecorder recorder = Mockito.mock(AgentExecutionRecorder.class);
        Mockito.when(recorder.createToolInvocations(Mockito.any()))
                .thenReturn(Map.of("tool-call-timing", 1L));

        CapturingPrinter printer = new CapturingPrinter();
        ToolCollection tools = new ToolCollection();
        tools.addTool(new DurationPayloadTool());
        AgentContext context = AgentContext.builder()
                .requestId("req-runtime-tool-timing")
                .sessionId("session-runtime-tool-timing")
                .toolCollection(tools)
                .executionRecorder(recorder)
                .agentRunState(AgentRunState.builder().runId(1L).build())
                .printer(printer)
                .build();
        tools.setAgentContext(context);

        TimingAgent agent = new TimingAgent(context, printer, tools);
        ToolCall command = ToolCall.builder()
                .id("tool-call-timing")
                .type("function")
                .function(ToolCall.Function.builder()
                        .name("duration_payload_tool")
                        .arguments("{\"durationMs\":999999}")
                        .build())
                .build();

        ToolExecutionOutcome outcome = agent.executeOutcome(command);
        agent.emitToolResult(command, outcome);

        Assert.assertEquals(3, printer.events.size());
        ReplayTiming runningTiming = timing(printer.events.get(0).message);
        ReplayTiming finishedTiming = timing(printer.events.get(1).message);
        ReplayTiming resultTiming = (ReplayTiming) printer.events.get(2).extra.get("timing");

        Assert.assertNotNull(runningTiming.getStartedAt());
        Assert.assertNull(runningTiming.getFinishedAt());
        Assert.assertEquals(ReplayTiming.SOURCE_RUNTIME, runningTiming.getSource());
        Assert.assertNotNull(finishedTiming.getFinishedAt());
        Assert.assertNotNull(finishedTiming.getDurationMs());
        Assert.assertTrue(finishedTiming.getDurationMs() < 999999L);
        Assert.assertEquals(finishedTiming.getStartedAt(), resultTiming.getStartedAt());
        Assert.assertEquals(finishedTiming.getFinishedAt(), resultTiming.getFinishedAt());
        Assert.assertEquals(finishedTiming.getDurationMs(), resultTiming.getDurationMs());
        Assert.assertEquals("tool-call-timing", printer.events.get(2).message instanceof org.wwz.ai.domain.agent.runtime.stream.ToolResultStreamPayload
                ? ((org.wwz.ai.domain.agent.runtime.stream.ToolResultStreamPayload) printer.events.get(2).message).getToolCallId()
                : null);
    }

    private ReplayTiming timing(Object message) {
        Assert.assertTrue(message instanceof Map<?, ?>);
        Object timing = ((Map<?, ?>) message).get("timing");
        Assert.assertTrue(timing instanceof ReplayTiming);
        return (ReplayTiming) timing;
    }

    private static final class TimingAgent extends BaseAgent {
        private TimingAgent(AgentContext context, Printer printer, ToolCollection tools) {
            setContext(context);
            setPrinter(printer);
            setAvailableTools(tools);
        }

        private ToolExecutionOutcome executeOutcome(ToolCall command) {
            return executeToolOutcome(command);
        }

        private void emitToolResult(ToolCall command, ToolExecutionOutcome outcome) {
            sendToolResult(command, outcome);
        }

        @Override
        public String step() {
            return "";
        }
    }

    private static final class DurationPayloadTool implements BaseTool {
        @Override
        public String getName() {
            return "duration_payload_tool";
        }

        @Override
        public String getDescription() {
            return "returns a payload containing an unrelated duration";
        }

        @Override
        public Map<String, Object> toParams() {
            return Collections.emptyMap();
        }

        @Override
        public Object execute(Object input) {
            return ToolResultPayload.fromData(Map.of("durationMs", 999999L));
        }
    }

    private static final class CapturingPrinter implements Printer {
        private final List<CapturedEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void send(String messageId, String messageType, Object message,
                         String digitalEmployee, Boolean isFinal) {
            events.add(new CapturedEvent(messageType, message, Map.of()));
        }

        @Override
        public void send(String messageId, String messageType, Object message,
                         Map<String, Object> extraResultMap, String digitalEmployee, Boolean isFinal) {
            events.add(new CapturedEvent(messageType, message,
                    extraResultMap == null ? Map.of() : extraResultMap));
        }

        @Override
        public void send(String messageType, Object message) {
            events.add(new CapturedEvent(messageType, message, Map.of()));
        }

        @Override
        public void send(String messageType, Object message, String digitalEmployee) {
            events.add(new CapturedEvent(messageType, message, Map.of()));
        }

        @Override
        public void send(String messageId, String messageType, Object message, Boolean isFinal) {
            events.add(new CapturedEvent(messageType, message, Map.of()));
        }

        @Override
        public void sendWithResultMap(String messageId, String messageType, Object message,
                                      Map<String, Object> extraResultMap, Boolean isFinal) {
            events.add(new CapturedEvent(messageType, message,
                    extraResultMap == null ? Map.of() : extraResultMap));
        }

        @Override
        public void sendWithResultMap(String messageType, Object message,
                                      Map<String, Object> extraResultMap) {
            events.add(new CapturedEvent(messageType, message,
                    extraResultMap == null ? Map.of() : extraResultMap));
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(AgentType agentType) {
        }
    }

    private record CapturedEvent(String type, Object message, Map<String, Object> extra) {
    }
}

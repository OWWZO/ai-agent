package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.ledger.model.AgentRunState;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.tool.mcp.model.McpToolInfo;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.common.mcp.McpToolNames;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolCatalog;
import org.wwz.ai.domain.agent.runtime.tool.deferred.DeferredToolEntry;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactRegistry;
import org.wwz.ai.domain.agent.runtime.tool.mcp.port.McpToolExecutor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ToolCall 在 ledger / SSE 解包为真实 MCP 名；Memory 仍保留 ToolCall。
 */
public class ToolCallUnwrapPipelineTest {

    @Test
    public void ledgerAndSseUseUnwrappedMcpName() {
        Probe probe = Probe.create();
        Mockito.when(probe.executor.executeTool(Mockito.any(), Mockito.any())).thenReturn("remote-ok");
        Mockito.when(probe.recorder.createToolInvocations(Mockito.any()))
                .thenReturn(Map.of("call-1", 99L));

        ToolCall command = toolCall("{\"name\":\"mcp__demo__remote_tool\",\"arguments\":{\"q\":\"hi\"}}");
        probe.agent.getMemory().addMessage(Message.fromToolCalls("calling", List.of(command)));
        probe.agent.executeTool(command);

        ArgumentCaptor<ToolInvocationBatchStartRecord> captor =
                ArgumentCaptor.forClass(ToolInvocationBatchStartRecord.class);
        Mockito.verify(probe.recorder).createToolInvocations(captor.capture());
        Assert.assertEquals("mcp__demo__remote_tool", captor.getValue().getItems().get(0).getToolName());
        Assert.assertEquals("mcp__demo__remote_tool", probe.sseToolName.get());
        Mockito.verify(probe.executor).executeTool(Mockito.argThat(info ->
                "remote_tool".equals(info.getOriginalName())), Mockito.any());
        Assert.assertEquals(McpToolNames.TOOL_CALL, command.getFunction().getName());
    }

    @Test
    public void missingRequiredDoesNotCallExecutor() {
        Probe probe = Probe.create();
        Mockito.when(probe.recorder.createToolInvocations(Mockito.any()))
                .thenReturn(Map.of("call-1", 99L));
        ToolCall command = toolCall("{\"name\":\"mcp__demo__remote_tool\",\"arguments\":{}}");
        String observation = probe.agent.executeTool(command);
        Assert.assertTrue(observation.contains("required") || observation.contains("parameters")
                || observation.contains("NOT invoked"));
        Mockito.verifyNoInteractions(probe.executor);
    }

    @Test
    public void directDeferredNameDoesNotExecute() {
        Probe probe = Probe.create();
        Object result = probe.collection.execute("mcp__demo__remote_tool", Map.of("q", "hi"));
        Assert.assertTrue(String.valueOf(result).contains(McpToolNames.TOOL_CALL));
        Mockito.verifyNoInteractions(probe.executor);
    }

    @Test
    public void pipelineDirectDeferredNameDoesNotExecute() {
        Probe probe = Probe.create();
        ToolCall direct = ToolCall.builder()
                .id("call-direct")
                .type("function")
                .function(ToolCall.Function.builder()
                        .name("mcp__demo__remote_tool")
                        .arguments("{\"q\":\"hi\"}")
                        .build())
                .build();

        String observation = probe.agent.executeTool(direct);

        Assert.assertTrue(observation.contains("deferred"));
        Mockito.verifyNoInteractions(probe.executor);
    }

    private static ToolCall toolCall(String arguments) {
        return ToolCall.builder()
                .id("call-1")
                .type("function")
                .function(ToolCall.Function.builder()
                        .name(McpToolNames.TOOL_CALL)
                        .arguments(arguments)
                        .build())
                .build();
    }

    private static final class Probe {
        private final BaseAgent agent;
        private final ToolCollection collection;
        private final McpToolExecutor executor;
        private final AgentExecutionRecorder recorder;
        private final AtomicReference<String> sseToolName = new AtomicReference<>();

        private Probe(BaseAgent agent,
                      ToolCollection collection,
                      McpToolExecutor executor,
                      AgentExecutionRecorder recorder) {
            this.agent = agent;
            this.collection = collection;
            this.executor = executor;
            this.recorder = recorder;
        }

        static Probe create() {
            McpToolInfo tool = McpToolInfo.builder()
                    .name("mcp__demo__remote_tool")
                    .originalName("remote_tool")
                    .desc("远程工具")
                    .parameters("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}},\"required\":[\"q\"]}")
                    .serverKey("demo")
                    .mcpId("mcp-1")
                    .build();
            McpToolExecutor executor = Mockito.mock(McpToolExecutor.class);
            AgentExecutionRecorder recorder = Mockito.mock(AgentExecutionRecorder.class);
            ToolCollection collection = new ToolCollection();
             collection.setDeferredToolCatalog(new DeferredToolCatalog(List.of(DeferredToolEntry.mcp(
                     tool,
                     "demo",
                     Map.of("type", "object", "properties", Map.of("q", Map.of("type", "string")),
                             "required", List.of("q")),
                     ""))));
            collection.setMcpToolExecutor(executor);
            AgentRunState runState = AgentRunState.builder()
                    .runId(1L)
                    .runUid("run-1")
                    .build();
            runState.bindCurrentLlmInvocationId(10L);
            CapturingPrinter printer = new CapturingPrinter();
            AgentContext context = AgentContext.builder()
                    .requestId("req-unwrap")
                    .sessionId("sess-unwrap")
                    .toolCollection(collection)
                    .executionRecorder(recorder)
                    .agentRunState(runState)
                    .printer(printer)
                    .toolArtifactRegistry(new ToolArtifactRegistry())
                    .currentToolArtifactSourceHolder(new ThreadLocal<>())
                    .build();
            collection.setAgentContext(context);
            BaseAgent agent = new BaseAgent() {
                @Override
                public String step() {
                    return "ok";
                }
            };
            agent.setContext(context);
            agent.setAvailableTools(collection);
            agent.setPrinter(printer);
            agent.setName("probe");
            Probe probe = new Probe(agent, collection, executor, recorder);
            printer.sink = probe.sseToolName;
            return probe;
        }
    }

    private static final class CapturingPrinter implements Printer {
        private AtomicReference<String> sink = new AtomicReference<>();

        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
            capture(message);
        }

        @Override
        public void send(String messageId, String messageType, Object message, Map<String, Object> extraResultMap,
                         String digitalEmployee, Boolean isFinal) {
            capture(message);
        }

        @Override
        public void send(String messageType, Object message) {
            capture(message);
        }

        @Override
        public void send(String messageType, Object message, String digitalEmployee) {
            capture(message);
        }

        @Override
        public void send(String messageId, String messageType, Object message, Boolean isFinal) {
            capture(message);
        }

        @Override
        public void sendWithResultMap(String messageId, String messageType, Object message,
                                      Map<String, Object> extraResultMap, Boolean isFinal) {
            capture(message);
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
            capture(message);
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(org.wwz.ai.domain.agent.runtime.enums.AgentType agentType) {
        }

        @SuppressWarnings("unchecked")
        private void capture(Object message) {
            if (message instanceof Map<?, ?> map && "tool_call".equals(map.get("messageType"))) {
                Object name = map.get("toolName");
                if (name != null) {
                    sink.set(String.valueOf(name));
                }
            }
        }
    }
}

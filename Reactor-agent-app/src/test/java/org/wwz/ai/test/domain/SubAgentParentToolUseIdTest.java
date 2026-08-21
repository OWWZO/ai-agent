package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentContextFactory;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentPrinter;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentResult;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRunner;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 异步后台 Agent 不能依赖 ThreadLocal 取 parentToolUseId。
 */
public class SubAgentParentToolUseIdTest {

    @Test
    public void factoryShouldWrapPrinterWhenExplicitParentIdGivenAndThreadLocalEmpty() {
        RecordingPrinter parentPrinter = new RecordingPrinter();
        AgentContext parent = parentContext(parentPrinter);
        Assert.assertNull(parent.getCurrentToolArtifactSource());

        AgentContext child = SubAgentContextFactory.create(
                parent, "scan controllers", "explore", new ToolCollection(),
                "agent-1", SubAgentRegistry.TYPE_EXPLORE, "agent-call-1");

        Assert.assertEquals("agent-call-1", child.getParentToolUseId());
        Assert.assertTrue(child.getPrinter() instanceof SubAgentPrinter);

        child.getPrinter().send("tool_call", new LinkedHashMap<>());
        Assert.assertEquals("agent-call-1", parentPrinter.lastExtra.get(SubAgentPrinter.KEY_PARENT_TOOL_USE_ID));
        Assert.assertEquals("agent-1", parentPrinter.lastExtra.get(SubAgentPrinter.KEY_SUB_AGENT_ID));

        child.getPrinter().send("tool_thought", "中间回复");
        Assert.assertEquals("tool_thought", parentPrinter.lastType);
        Assert.assertEquals("agent-call-1", parentPrinter.lastExtra.get(SubAgentPrinter.KEY_PARENT_TOOL_USE_ID));

        child.getPrinter().send("llm_reasoning", "深度思考");
        Assert.assertEquals("llm_reasoning", parentPrinter.lastType);
        Assert.assertEquals("agent-call-1", parentPrinter.lastExtra.get(SubAgentPrinter.KEY_PARENT_TOOL_USE_ID));

        child.getPrinter().send("result", "子智能体终答");
        Assert.assertEquals("result", parentPrinter.lastType);
        Assert.assertEquals("agent-call-1", parentPrinter.lastExtra.get(SubAgentPrinter.KEY_PARENT_TOOL_USE_ID));
    }

    @Test
    public void factoryShouldNotWrapPrinterWhenParentIdMissing() {
        RecordingPrinter parentPrinter = new RecordingPrinter();
        AgentContext parent = parentContext(parentPrinter);

        AgentContext child = SubAgentContextFactory.create(
                parent, "scan controllers", "explore", new ToolCollection(),
                "agent-1", SubAgentRegistry.TYPE_EXPLORE, null);

        Assert.assertNull(child.getParentToolUseId());
        Assert.assertSame(parentPrinter, child.getPrinter());
    }

    @Test
    public void factoryShouldFallBackToThreadLocalWhenExplicitIdBlank() {
        RecordingPrinter parentPrinter = new RecordingPrinter();
        AgentContext parent = parentContext(parentPrinter);
        parent.bindCurrentToolArtifactSource(source("agent-call-sync"));
        try {
            AgentContext child = SubAgentContextFactory.create(
                    parent, "scan controllers", "explore", new ToolCollection(),
                    "agent-1", SubAgentRegistry.TYPE_EXPLORE, null);
            Assert.assertEquals("agent-call-sync", child.getParentToolUseId());
            Assert.assertTrue(child.getPrinter() instanceof SubAgentPrinter);
        } finally {
            parent.clearCurrentToolArtifactSource();
        }
    }

    @Test
    public void backgroundDispatchShouldPassCapturedParentIdAfterThreadLocalCleared() throws Exception {
        CapturingRunner runner = new CapturingRunner();
        AgentDispatchTool tool = new AgentDispatchTool(runner, new SubAgentRegistry());
        AgentContext parent = parentContext();
        parent.bindCurrentToolArtifactSource(source("agent-call-bg"));
        tool.setAgentContext(parent);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("description", "后台探索");
        input.put("prompt", "scan the repo");
        input.put("subagent_type", SubAgentRegistry.TYPE_EXPLORE);
        input.put("run_in_background", true);

        ToolResultPayload payload;
        try {
            payload = (ToolResultPayload) tool.execute(input);
        } finally {
            parent.clearCurrentToolArtifactSource();
        }

        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(runner.awaitCalled());
        Assert.assertEquals("agent-call-bg", runner.capturedParentToolUseId);
        Assert.assertNull(parent.getCurrentToolArtifactSource());
    }

    private static AgentContext parentContext() {
        return parentContext(new RecordingPrinter());
    }

    private static AgentContext parentContext(Printer printer) {
        return AgentContext.builder()
                .requestId("req-parent-id")
                .sessionId("s-parent-id")
                .query("q")
                .printer(printer)
                .toolCollection(new ToolCollection())
                .build();
    }

    private static ToolArtifactSource source(String toolCallId) {
        return ToolArtifactSource.builder()
                .sessionId("s-parent-id")
                .requestId("req-parent-id")
                .toolCallId(toolCallId)
                .toolName(AgentDispatchTool.NAME)
                .build();
    }

    private static final class CapturingRunner extends SubAgentRunner {
        private final CountDownLatch called = new CountDownLatch(1);
        private volatile String capturedParentToolUseId;

        private CapturingRunner() {
            super(new SubAgentRegistry());
        }

        @Override
        public SubAgentResult run(AgentContext parentContext,
                                  String description,
                                  String prompt,
                                  String subagentType,
                                  String resumeAgentId,
                                  RunCancellation cancellationOverride,
                                  String preferredAgentId,
                                  String explicitParentToolUseId) {
            capturedParentToolUseId = explicitParentToolUseId;
            called.countDown();
            return SubAgentResult.builder()
                    .status(SubAgentResult.STATUS_COMPLETED)
                    .agentId(preferredAgentId)
                    .agentType(subagentType)
                    .description(description)
                    .prompt(prompt)
                    .content("ok")
                    .build();
        }

        private boolean awaitCalled() throws InterruptedException {
            return called.await(3, TimeUnit.SECONDS);
        }
    }

    private static final class RecordingPrinter implements Printer {
        private String lastType;
        private Map<String, Object> lastExtra;

        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
            send(messageId, messageType, message, null, digitalEmployee, isFinal);
        }

        @Override
        public void send(String messageId, String messageType, Object message, Map<String, Object> extraResultMap,
                        String digitalEmployee, Boolean isFinal) {
            lastType = messageType;
            lastExtra = extraResultMap;
        }

        @Override
        public void send(String messageType, Object message) {
            send(null, messageType, message, null, true);
        }

        @Override
        public void send(String messageType, Object message, String digitalEmployee) {
            send(null, messageType, message, digitalEmployee, true);
        }

        @Override
        public void send(String messageId, String messageType, Object message, Boolean isFinal) {
            send(messageId, messageType, message, (String) null, isFinal);
        }

        @Override
        public void sendWithResultMap(String messageId, String messageType, Object message,
                                     Map<String, Object> extraResultMap, Boolean isFinal) {
            send(messageId, messageType, message, extraResultMap, null, isFinal);
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
            send(null, messageType, message, extraResultMap, null, true);
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(AgentType agentType) {
        }
    }
}

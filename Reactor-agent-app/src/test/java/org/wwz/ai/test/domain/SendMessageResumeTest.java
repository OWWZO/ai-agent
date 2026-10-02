package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentResult;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRunner;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTaskRegistry;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionAgentMailboxHub;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.AgentDispatchTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.SendMessageTool;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SendMessage：运行中只注入；已结束后台续跑。Agent(resume_agent_id) 失败。
 */
public class SendMessageResumeTest {

    @Test
    public void agentResumeAgentIdFailsWithoutStartingChild() {
        CapturingRunner runner = new CapturingRunner(true);
        AgentDispatchTool tool = new AgentDispatchTool(runner, new SubAgentRegistry());
        tool.setAgentContext(parentContext("sess-reject-resume", new RuntimeBackgroundTaskRegistry()));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("description", "续跑");
        input.put("prompt", "continue");
        input.put("resume_agent_id", "agent-old");
        ToolResultPayload payload = (ToolResultPayload) tool.execute(input);

        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(payload.getToolResult().contains("SendMessage"));
        Assert.assertEquals(0, runner.runCount.get());
    }

    @Test
    public void runningTargetInjectsWithoutNewTask() {
        String sessionId = "sess-inject-" + System.nanoTime();
        SessionAgentMailboxHub.clearAll();
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        RuntimeBackgroundTask task = registry.registerLocalAgent("live", "general-purpose", "work");
        registry.bindAgentId(task.getId(), "agent-live");
        SessionAgentMailboxHub.markActive(sessionId, "agent-live", true);
        SessionAgentMailboxHub.queue(sessionId, "agent-live");

        CapturingRunner runner = new CapturingRunner(true);
        SendMessageTool tool = new SendMessageTool(runner, null);
        tool.setAgentContext(parentContext(sessionId, registry));

        int runningBefore = registry.listRunning().size();
        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                "to", "agent-live",
                "message", "先列目录"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getLlmData();

        Assert.assertEquals(Boolean.TRUE, data.get("ok"));
        Assert.assertEquals(1, data.get("queued"));
        Assert.assertEquals(runningBefore, registry.listRunning().size());
        Assert.assertEquals(0, runner.runCount.get());
        SessionAgentMailboxHub.clearAll();
    }

    @Test
    public void completedTargetResumesWithNewTaskIdSameAgent() throws Exception {
        String sessionId = "sess-resume-" + System.nanoTime();
        SessionAgentMailboxHub.clearAll();
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        RuntimeBackgroundTask old = registry.registerLocalAgent("done", "general-purpose", "work");
        registry.bindAgentId(old.getId(), "agent-done");
        registry.complete(old.getId(), SubAgentResult.builder()
                .status(SubAgentResult.STATUS_COMPLETED)
                .agentId("agent-done")
                .content("ok")
                .build());

        CapturingRunner runner = new CapturingRunner(true);
        SendMessageTool tool = new SendMessageTool(runner, null);
        AgentContext parent = parentContext(sessionId, registry);
        parent.bindCurrentToolArtifactSource(ToolArtifactSource.builder()
                .sessionId(sessionId)
                .requestId("req-resume")
                .toolCallId("sm-1")
                .toolName(TaskToolNames.SEND_MESSAGE)
                .build());
        tool.setAgentContext(parent);

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                "to", "agent-done",
                "message", "继续写报告"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getLlmData();

        Assert.assertEquals(Boolean.TRUE, data.get("ok"));
        Assert.assertEquals(Boolean.TRUE, data.get("resumed"));
        Assert.assertEquals("agent-done", data.get("agentId"));
        Assert.assertNotEquals(old.getId(), data.get("task_id"));
        Assert.assertEquals(RuntimeBackgroundTask.STATUS_RUNNING, data.get("status"));
        Assert.assertTrue(runner.awaitCalled());
        Assert.assertEquals("agent-done", runner.capturedResumeAgentId);
        Assert.assertEquals("sm-1", runner.capturedParentToolUseId);
        Assert.assertEquals("继续写报告", runner.capturedPrompt);
        SessionAgentMailboxHub.clearAll();
    }

    @Test
    public void missingMemoryDoesNotStartTask() {
        String sessionId = "sess-nomem-" + System.nanoTime();
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        RuntimeBackgroundTask old = registry.registerLocalAgent("done", "general-purpose", "work");
        registry.bindAgentId(old.getId(), "agent-empty");
        registry.complete(old.getId(), SubAgentResult.builder()
                .status(SubAgentResult.STATUS_COMPLETED)
                .agentId("agent-empty")
                .content("ok")
                .build());

        CapturingRunner runner = new CapturingRunner(false);
        SendMessageTool tool = new SendMessageTool(runner, null);
        tool.setAgentContext(parentContext(sessionId, registry));

        int runningBefore = registry.listRunning().size();
        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                "to", "agent-empty",
                "message", "再来一次"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getLlmData();

        Assert.assertEquals(Boolean.FALSE, data.get("ok"));
        Assert.assertTrue(String.valueOf(data.get("message")).contains("工作记忆"));
        Assert.assertEquals(runningBefore, registry.listRunning().size());
        Assert.assertEquals(0, runner.runCount.get());
    }

    private static AgentContext parentContext(String sessionId, RuntimeBackgroundTaskRegistry registry) {
        return AgentContext.builder()
                .requestId("req-" + sessionId)
                .sessionId(sessionId)
                .query("q")
                .backgroundTasks(registry)
                .build();
    }

    private static final class CapturingRunner extends SubAgentRunner {
        private final boolean hasMemory;
        private final CountDownLatch called = new CountDownLatch(1);
        private final AtomicInteger runCount = new AtomicInteger();
        private volatile String capturedResumeAgentId;
        private volatile String capturedParentToolUseId;
        private volatile String capturedPrompt;

        private CapturingRunner(boolean hasMemory) {
            super(new SubAgentRegistry());
            this.hasMemory = hasMemory;
        }

        @Override
        public boolean hasPersistedMemory(String sessionId, String agentId) {
            return hasMemory;
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
            runCount.incrementAndGet();
            capturedResumeAgentId = resumeAgentId;
            capturedParentToolUseId = explicitParentToolUseId;
            capturedPrompt = prompt;
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
}

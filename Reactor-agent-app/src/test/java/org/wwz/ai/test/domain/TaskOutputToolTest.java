package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentResult;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTaskRegistry;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskOutputTool;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * TaskOutput：无 timeout；block=false 立即 peek；block=true 等到终态。
 */
public class TaskOutputToolTest {

    @Test
    public void schemaHasNoTimeout() {
        Map<String, Object> params = new TaskOutputTool().toParams();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) params.get("properties");
        Assert.assertFalse(properties.containsKey("timeout"));
        Assert.assertTrue(properties.containsKey("task_id"));
        Assert.assertTrue(properties.containsKey("block"));
        Assert.assertFalse(new TaskOutputTool().getDescription().contains("timeout"));
    }

    @Test
    public void peekWhileRunningReturnsNotReady() {
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        RuntimeBackgroundTask task = registry.registerLocalAgent("peek", "general-purpose", "p");
        TaskOutputTool tool = new TaskOutputTool();
        tool.setAgentContext(AgentContext.builder()
                .requestId("req-to")
                .sessionId("sess-to")
                .backgroundTasks(registry)
                .build());
        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                "task_id", task.getId(),
                "block", false));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
        Assert.assertEquals("not_ready", data.get("retrieval_status"));
        Assert.assertEquals(RuntimeBackgroundTask.STATUS_RUNNING, data.get("status"));
    }

    @Test
    public void blockTrueReturnsAfterComplete() throws Exception {
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        RuntimeBackgroundTask task = registry.registerLocalAgent("block", "general-purpose", "p");
        TaskOutputTool tool = new TaskOutputTool();
        tool.setAgentContext(AgentContext.builder()
                .requestId("req-to-block")
                .sessionId("sess-to-block")
                .backgroundTasks(registry)
                .build());
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> f = pool.submit(() -> {
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                registry.complete(task.getId(), SubAgentResult.builder()
                        .status(SubAgentResult.STATUS_COMPLETED)
                        .agentId("a3")
                        .content("done")
                        .build());
            });
            ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("task_id", task.getId()));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.getLlmData();
            Assert.assertEquals("success", data.get("retrieval_status"));
            Assert.assertEquals(RuntimeBackgroundTask.STATUS_COMPLETED, data.get("status"));
            Assert.assertEquals("done", data.get("output"));
            f.get(2, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }
}

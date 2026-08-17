package org.wwz.ai.test.domain.subagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentResult;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTaskRegistry;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 后台任务注册表（domain 纯单测；AgentDispatch 集成见 app 层环境）。
 */
public class BackgroundAgentTaskTest {

    @Test
    public void registryCompleteAndAwait() {
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        RuntimeBackgroundTask task = registry.registerLocalAgent("explore", "Explore", "find controllers");
        Assert.assertEquals(RuntimeBackgroundTask.STATUS_RUNNING, task.getStatus());

        SubAgentResult result = SubAgentResult.builder()
                .status(SubAgentResult.STATUS_COMPLETED)
                .agentId("ag1")
                .agentType("Explore")
                .content("found 3 controllers")
                .totalToolUseCount(2)
                .totalDurationMs(100L)
                .build();
        registry.complete(task.getId(), result);

        RuntimeBackgroundTask done = registry.get(task.getId()).orElseThrow();
        Assert.assertEquals(RuntimeBackgroundTask.STATUS_COMPLETED, done.getStatus());
        Assert.assertEquals("found 3 controllers", done.getOutput());
        Assert.assertEquals("ag1", done.getAgentId());
    }

    @Test
    public void registryStopCancelsToken() {
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        RuntimeBackgroundTask task = registry.registerLocalAgent("long", "general-purpose", "work");
        Assert.assertNotNull(task.getCancellation());
        Assert.assertFalse(task.getCancellation().isCancelled());

        registry.stop(task.getId());
        Assert.assertEquals(RuntimeBackgroundTask.STATUS_STOPPED, task.getStatus());
        Assert.assertTrue(task.getCancellation().isCancelled());
    }

    @Test
    public void awaitTerminalUnblocksOnComplete() throws Exception {
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        RuntimeBackgroundTask task = registry.registerLocalAgent("wait", "Explore", "p");
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
                        .agentId("a2")
                        .content("ok")
                        .build());
            });
            RuntimeBackgroundTask waited = registry.awaitTerminal(task.getId(), 3000).orElseThrow();
            Assert.assertEquals(RuntimeBackgroundTask.STATUS_COMPLETED, waited.getStatus());
            Assert.assertEquals("ok", waited.getOutput());
            f.get(2, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void failDoesNotOverwriteStopped() {
        RuntimeBackgroundTaskRegistry registry = new RuntimeBackgroundTaskRegistry();
        RuntimeBackgroundTask task = registry.registerLocalAgent("x", "Explore", "p");
        registry.stop(task.getId());
        registry.fail(task.getId(), "should ignore");
        Assert.assertEquals(RuntimeBackgroundTask.STATUS_STOPPED, task.getStatus());
        Assert.assertNull(task.getErrorMsg());
    }

    @Test
    public void taskOutputNameConstant() {
        Assert.assertEquals("TaskOutput", TaskToolNames.TASK_OUTPUT);
    }
}

package org.wwz.ai.test.domain.tasklist;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentResult;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTaskRegistry;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionTaskItem;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionTaskListStore;
import org.wwz.ai.domain.agent.runtime.tasklist.TasklistPersistencePort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存 fake 持久化端口：验证 store/registry 会调用 persist 钩子。
 */
public class TasklistPersistenceHookTest {

    @Test
    public void todoStorePersistsCreateUpdateDelete() {
        InMemoryPort port = new InMemoryPort();
        SessionTaskListStore store = new SessionTaskListStore("sess-1", port);
        SessionTaskItem created = store.create("写单测", "覆盖登录", null, null);
        Assert.assertEquals(1, port.todoUpserts.get());
        Assert.assertTrue(port.todos.containsKey("sess-1:" + created.getId()));

        store.update(created.getId(), null, null, SessionTaskItem.STATUS_COMPLETED, null, null);
        Assert.assertEquals(2, port.todoUpserts.get());

        store.delete(created.getId());
        Assert.assertEquals(1, port.todoDeletes.get());
        Assert.assertFalse(port.todos.containsKey("sess-1:" + created.getId()));
    }

    @Test
    public void todoStoreHydratesFromPort() {
        InMemoryPort port = new InMemoryPort();
        port.todos.put("sess-2:1", SessionTaskItem.builder()
                .id("1")
                .subject("已有任务")
                .description("desc")
                .status(SessionTaskItem.STATUS_PENDING)
                .build());
        port.highWater = 1;

        SessionTaskListStore store = new SessionTaskListStore("sess-2", port);
        Assert.assertEquals(1, store.list().size());
        Assert.assertEquals("已有任务", store.get("1").orElseThrow().getSubject());
        SessionTaskItem next = store.create("新任务", "详情", null, null);
        Assert.assertEquals("2", next.getId());
    }

    @Test
    public void backgroundRegistryPersistsAndHydrates() {
        InMemoryPort port = new InMemoryPort();
        RuntimeBackgroundTaskRegistry reg = new RuntimeBackgroundTaskRegistry("sess-bg", port);
        RuntimeBackgroundTask task = reg.registerLocalAgent("inspect", "general-purpose", "prompt");
        Assert.assertTrue(port.bgUpserts.get() >= 1);

        reg.complete(task.getId(), SubAgentResult.builder()
                .status(SubAgentResult.STATUS_COMPLETED)
                .agentId("a1")
                .content("done")
                .build());
        Assert.assertTrue(port.bgUpserts.get() >= 2);

        RuntimeBackgroundTaskRegistry reloaded = new RuntimeBackgroundTaskRegistry("sess-bg", port);
        RuntimeBackgroundTask loaded = reloaded.get(task.getId()).orElseThrow();
        Assert.assertEquals(RuntimeBackgroundTask.STATUS_COMPLETED, loaded.getStatus());
        Assert.assertEquals("done", loaded.getOutput());
    }

    @Test
    public void hydrateMarksOrphanRunningAsFailed() {
        InMemoryPort port = new InMemoryPort();
        RuntimeBackgroundTask orphan = RuntimeBackgroundTask.builder()
                .id("orphan1")
                .type(RuntimeBackgroundTask.TYPE_LOCAL_AGENT)
                .status(RuntimeBackgroundTask.STATUS_RUNNING)
                .description("left running")
                .startedAtMs(System.currentTimeMillis())
                .build();
        port.background.put("sess-x:orphan1", orphan);

        RuntimeBackgroundTaskRegistry reg = new RuntimeBackgroundTaskRegistry("sess-x", port);
        RuntimeBackgroundTask loaded = reg.get("orphan1").orElseThrow();
        Assert.assertEquals(RuntimeBackgroundTask.STATUS_FAILED, loaded.getStatus());
        Assert.assertTrue(loaded.getErrorMsg().contains("restart"));
    }

    private static final class InMemoryPort implements TasklistPersistencePort {
        final Map<String, SessionTaskItem> todos = new ConcurrentHashMap<>();
        final Map<String, RuntimeBackgroundTask> background = new ConcurrentHashMap<>();
        final AtomicInteger todoUpserts = new AtomicInteger();
        final AtomicInteger todoDeletes = new AtomicInteger();
        final AtomicInteger bgUpserts = new AtomicInteger();
        int highWater;

        @Override
        public List<SessionTaskItem> loadTodos(String sessionId) {
            List<SessionTaskItem> list = new ArrayList<>();
            String prefix = sessionId + ":";
            for (Map.Entry<String, SessionTaskItem> e : todos.entrySet()) {
                if (e.getKey().startsWith(prefix)) {
                    list.add(e.getValue());
                }
            }
            return list;
        }

        @Override
        public int loadTodoHighWaterMark(String sessionId) {
            return highWater;
        }

        @Override
        public void upsertTodo(String sessionId, SessionTaskItem item) {
            todoUpserts.incrementAndGet();
            todos.put(sessionId + ":" + item.getId(), item);
            try {
                highWater = Math.max(highWater, Integer.parseInt(item.getId()));
            } catch (NumberFormatException ignored) {
            }
        }

        @Override
        public void deleteTodo(String sessionId, String taskId) {
            todoDeletes.incrementAndGet();
            todos.remove(sessionId + ":" + taskId);
        }

        @Override
        public void replaceTodos(String sessionId, List<SessionTaskItem> items) {
            String prefix = sessionId + ":";
            todos.keySet().removeIf(k -> k.startsWith(prefix));
            if (items != null) {
                for (SessionTaskItem item : items) {
                    upsertTodo(sessionId, item);
                }
            }
        }

        @Override
        public List<RuntimeBackgroundTask> loadBackgroundTasks(String sessionId) {
            List<RuntimeBackgroundTask> list = new ArrayList<>();
            String prefix = sessionId + ":";
            for (Map.Entry<String, RuntimeBackgroundTask> e : background.entrySet()) {
                if (e.getKey().startsWith(prefix)) {
                    list.add(e.getValue());
                }
            }
            return list;
        }

        @Override
        public Optional<RuntimeBackgroundTask> findBackgroundTask(String sessionId, String taskId) {
            return Optional.ofNullable(background.get(sessionId + ":" + taskId));
        }

        @Override
        public void upsertBackgroundTask(String sessionId, RuntimeBackgroundTask task) {
            bgUpserts.incrementAndGet();
            background.put(sessionId + ":" + task.getId(), task);
        }
    }
}

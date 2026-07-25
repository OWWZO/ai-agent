package org.wwz.ai.domain.agent.runtime.tasklist;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求级后台运行任务注册表（对标 cc-haha AppState.tasks）。
 * 供 TaskStop 查找并停止 running 任务。
 */
public class RuntimeBackgroundTaskRegistry {

    private final Map<String, RuntimeBackgroundTask> tasks = new ConcurrentHashMap<>();

    public RuntimeBackgroundTask register(String type, String description, String command) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        RuntimeBackgroundTask task = RuntimeBackgroundTask.builder()
                .id(id)
                .type(StringUtils.defaultIfBlank(type, RuntimeBackgroundTask.TYPE_GENERIC))
                .status(RuntimeBackgroundTask.STATUS_RUNNING)
                .description(description)
                .command(command)
                .startedAtMs(System.currentTimeMillis())
                .build();
        tasks.put(id, task);
        return task;
    }

    public Optional<RuntimeBackgroundTask> get(String taskId) {
        if (StringUtils.isBlank(taskId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(tasks.get(taskId.trim()));
    }

    public synchronized Optional<RuntimeBackgroundTask> stop(String taskId) {
        RuntimeBackgroundTask task = tasks.get(StringUtils.trimToEmpty(taskId));
        if (task == null) {
            return Optional.empty();
        }
        if (!RuntimeBackgroundTask.STATUS_RUNNING.equals(task.getStatus())) {
            return Optional.of(task);
        }
        task.setStatus(RuntimeBackgroundTask.STATUS_STOPPED);
        task.setEndedAtMs(System.currentTimeMillis());
        return Optional.of(task);
    }

    public List<RuntimeBackgroundTask> listRunning() {
        List<RuntimeBackgroundTask> result = new ArrayList<>();
        for (RuntimeBackgroundTask task : tasks.values()) {
            if (RuntimeBackgroundTask.STATUS_RUNNING.equals(task.getStatus())) {
                result.add(task);
            }
        }
        return result;
    }
}

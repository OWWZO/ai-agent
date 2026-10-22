package org.wwz.ai.domain.agent.runtime.tasklist;

import java.util.List;
import java.util.Optional;

/**
 * 会话 Todo + 后台任务持久化端口（非 Execution Ledger）。
 * 失败由实现方吞并日志；调用方以内存态为准继续服务。
 */
public interface TasklistPersistencePort {

    List<SessionTaskItem> loadTodos(String sessionId);

    int loadTodoHighWaterMark(String sessionId);

    void upsertTodo(String sessionId, SessionTaskItem item);

    void deleteTodo(String sessionId, String taskId);

    /**
     * 整表替换：软删旧行后写入新列表（可为空列表表示清空）。
     */
    void replaceTodos(String sessionId, List<SessionTaskItem> items);

    List<RuntimeBackgroundTask> loadBackgroundTasks(String sessionId);

    Optional<RuntimeBackgroundTask> findBackgroundTask(String sessionId, String taskId);

    void upsertBackgroundTask(String sessionId, RuntimeBackgroundTask task);
}

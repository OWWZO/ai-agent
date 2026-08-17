package org.wwz.ai.infrastructure.tasklist;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionTaskItem;
import org.wwz.ai.domain.agent.runtime.tasklist.TasklistPersistencePort;
import org.wwz.ai.infrastructure.dao.reactor.IBackgroundTaskDao;
import org.wwz.ai.infrastructure.dao.reactor.ISessionTodoDao;
import org.wwz.ai.infrastructure.dao.reactor.po.BackgroundTaskPO;
import org.wwz.ai.infrastructure.dao.reactor.po.SessionTodoPO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Todo / 后台任务 MyBatis 持久化实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyBatisTasklistPersistence implements TasklistPersistencePort {

    private final ISessionTodoDao sessionTodoDao;
    private final IBackgroundTaskDao backgroundTaskDao;

    @Override
    public List<SessionTaskItem> loadTodos(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        List<SessionTodoPO> rows = sessionTodoDao.selectBySessionId(sessionId.trim());
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<SessionTaskItem> items = new ArrayList<>();
        for (SessionTodoPO row : rows) {
            SessionTaskItem item = toTodoItem(row);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public int loadTodoHighWaterMark(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return 0;
        }
        Integer max = sessionTodoDao.selectMaxSeqNo(sessionId.trim());
        return max == null ? 0 : Math.max(0, max);
    }

    @Override
    public void upsertTodo(String sessionId, SessionTaskItem item) {
        if (StringUtils.isBlank(sessionId) || item == null || StringUtils.isBlank(item.getId())) {
            return;
        }
        sessionTodoDao.upsert(toTodoPo(sessionId.trim(), item));
    }

    @Override
    public void deleteTodo(String sessionId, String taskId) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(taskId)) {
            return;
        }
        sessionTodoDao.softDelete(sessionId.trim(), taskId.trim());
    }

    @Override
    public void replaceTodos(String sessionId, List<SessionTaskItem> items) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        String sid = sessionId.trim();
        sessionTodoDao.softDeleteAllBySessionId(sid);
        if (items == null || items.isEmpty()) {
            return;
        }
        for (SessionTaskItem item : items) {
            if (item == null || StringUtils.isBlank(item.getId())) {
                continue;
            }
            sessionTodoDao.upsert(toTodoPo(sid, item));
        }
    }

    @Override
    public List<RuntimeBackgroundTask> loadBackgroundTasks(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        List<BackgroundTaskPO> rows = backgroundTaskDao.selectBySessionId(sessionId.trim());
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<RuntimeBackgroundTask> tasks = new ArrayList<>();
        for (BackgroundTaskPO row : rows) {
            RuntimeBackgroundTask task = toBackgroundTask(row);
            if (task != null) {
                tasks.add(task);
            }
        }
        return tasks;
    }

    @Override
    public Optional<RuntimeBackgroundTask> findBackgroundTask(String sessionId, String taskId) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(taskId)) {
            return Optional.empty();
        }
        BackgroundTaskPO row = backgroundTaskDao.selectBySessionAndTaskId(sessionId.trim(), taskId.trim());
        return Optional.ofNullable(toBackgroundTask(row));
    }

    @Override
    public void upsertBackgroundTask(String sessionId, RuntimeBackgroundTask task) {
        if (StringUtils.isBlank(sessionId) || task == null || StringUtils.isBlank(task.getId())) {
            return;
        }
        try {
            backgroundTaskDao.upsert(toBackgroundPo(sessionId.trim(), task));
        } catch (Exception e) {
            log.error("upsert background task failed sessionId={} taskId={}: {}",
                    sessionId, task.getId(), e.getMessage(), e);
            throw e;
        }
    }

    private static SessionTaskItem toTodoItem(SessionTodoPO row) {
        if (row == null) {
            return null;
        }
        return SessionTaskItem.builder()
                .id(row.getTaskId())
                .subject(row.getSubject())
                .description(row.getDescription())
                .activeForm(row.getActiveForm())
                .owner(row.getOwner())
                .status(row.getStatus())
                .blocks(parseStringList(row.getBlocksJson()))
                .blockedBy(parseStringList(row.getBlockedByJson()))
                .metadata(parseMap(row.getMetadataJson()))
                .build();
    }

    private static SessionTodoPO toTodoPo(String sessionId, SessionTaskItem item) {
        SessionTodoPO po = new SessionTodoPO();
        po.setSessionId(sessionId);
        po.setTaskId(item.getId());
        po.setSubject(item.getSubject());
        po.setDescription(item.getDescription());
        po.setActiveForm(item.getActiveForm());
        po.setOwner(item.getOwner());
        po.setStatus(item.getStatus());
        po.setBlocksJson(toJson(item.getBlocks()));
        po.setBlockedByJson(toJson(item.getBlockedBy()));
        po.setMetadataJson(toJson(item.getMetadata()));
        int seq = 0;
        try {
            seq = Integer.parseInt(item.getId());
        } catch (NumberFormatException ignored) {
            seq = 0;
        }
        po.setSeqNo(seq);
        return po;
    }

    private static RuntimeBackgroundTask toBackgroundTask(BackgroundTaskPO row) {
        if (row == null) {
            return null;
        }
        return RuntimeBackgroundTask.builder()
                .id(row.getTaskId())
                .type(row.getType())
                .status(row.getStatus())
                .description(row.getDescription())
                .command(row.getCommand())
                .agentId(row.getAgentId())
                .agentType(row.getAgentType())
                .prompt(row.getPrompt())
                .output(row.getOutput())
                .errorMsg(row.getErrorMsg())
                .totalToolUseCount(row.getTotalToolUseCount())
                .totalDurationMs(row.getTotalDurationMs())
                .startedAtMs(row.getStartedAtMs() == null ? 0L : row.getStartedAtMs())
                .endedAtMs(row.getEndedAtMs())
                .build();
    }

    private static BackgroundTaskPO toBackgroundPo(String sessionId, RuntimeBackgroundTask task) {
        BackgroundTaskPO po = new BackgroundTaskPO();
        po.setSessionId(sessionId);
        po.setTaskId(task.getId());
        po.setType(task.getType());
        po.setStatus(task.getStatus());
        po.setDescription(task.getDescription());
        po.setCommand(task.getCommand());
        po.setAgentId(task.getAgentId());
        po.setAgentType(task.getAgentType());
        po.setPrompt(task.getPrompt());
        po.setOutput(task.getOutput());
        po.setErrorMsg(task.getErrorMsg());
        po.setTotalToolUseCount(task.getTotalToolUseCount());
        po.setTotalDurationMs(task.getTotalDurationMs());
        po.setStartedAtMs(task.getStartedAtMs());
        po.setEndedAtMs(task.getEndedAtMs());
        return po;
    }

    private static List<String> parseStringList(String json) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            List<String> list = JSON.parseObject(json, new TypeReference<List<String>>() {
            });
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static Map<String, Object> parseMap(String json) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = JSON.parseObject(json, new TypeReference<Map<String, Object>>() {
            });
            return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return "[]";
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return "{}";
        }
        return JSON.toJSONString(value);
    }
}

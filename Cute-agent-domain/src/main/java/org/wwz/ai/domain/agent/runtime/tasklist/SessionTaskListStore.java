package org.wwz.ai.domain.agent.runtime.tasklist;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 会话级 Todo 任务列表。
 * 可选挂载 {@link TasklistPersistencePort} 实现跨 run 持久化。
 */
public class SessionTaskListStore {

    private final String listId;
    private final AtomicInteger highWaterMark = new AtomicInteger(0);
    private final Map<String, SessionTaskItem> tasks = new ConcurrentHashMap<>();
    private final TasklistPersistencePort persistence;

    public SessionTaskListStore(String listId) {
        this(listId, null);
    }

    public SessionTaskListStore(String listId, TasklistPersistencePort persistence) {
        this.listId = StringUtils.defaultIfBlank(listId, "default");
        this.persistence = persistence;
        hydrate();
    }

    public String getListId() {
        return listId;
    }

    private void hydrate() {
        if (persistence == null) {
            return;
        }
        try {
            List<SessionTaskItem> loaded = persistence.loadTodos(listId);
            int max = persistence.loadTodoHighWaterMark(listId);
            if (loaded != null) {
                for (SessionTaskItem item : loaded) {
                    if (item == null || StringUtils.isBlank(item.getId())) {
                        continue;
                    }
                    tasks.put(item.getId(), item);
                    try {
                        max = Math.max(max, Integer.parseInt(item.getId()));
                    } catch (NumberFormatException ignored) {
                        // keep max
                    }
                }
            }
            highWaterMark.set(Math.max(0, max));
        } catch (Exception ignored) {
            // best-effort：hydrate 失败保持空列表
        }
    }

    public synchronized SessionTaskItem create(String subject,
                                               String description,
                                               String activeForm,
                                               Map<String, Object> metadata) {
        if (StringUtils.isBlank(subject)) {
            throw new IllegalArgumentException("subject 不能为空");
        }
        if (StringUtils.isBlank(description)) {
            throw new IllegalArgumentException("description 不能为空");
        }
        String id = String.valueOf(highWaterMark.incrementAndGet());
        SessionTaskItem item = SessionTaskItem.builder()
                .id(id)
                .subject(subject.trim())
                .description(description.trim())
                .activeForm(StringUtils.trimToNull(activeForm))
                .status(SessionTaskItem.STATUS_PENDING)
                .blocks(new ArrayList<>())
                .blockedBy(new ArrayList<>())
                .metadata(metadata == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(metadata))
                .build();
        tasks.put(id, item);
        persistUpsert(item);
        return item;
    }

    public Optional<SessionTaskItem> get(String taskId) {
        if (StringUtils.isBlank(taskId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(tasks.get(taskId.trim()));
    }

    public synchronized Optional<SessionTaskItem> update(String taskId,
                                                         String subject,
                                                         String description,
                                                         String status,
                                                         String activeForm,
                                                         String owner,
                                                         List<String> addBlocks,
                                                         List<String> addBlockedBy) {
        SessionTaskItem item = tasks.get(StringUtils.trimToEmpty(taskId));
        if (item == null) {
            return Optional.empty();
        }
        if (StringUtils.isNotBlank(subject)) {
            item.setSubject(subject.trim());
        }
        if (description != null) {
            item.setDescription(description);
        }
        if (StringUtils.isNotBlank(status)) {
            item.setStatus(normalizeStatus(status));
        }
        if (activeForm != null) {
            item.setActiveForm(StringUtils.trimToNull(activeForm));
        }
        if (owner != null) {
            item.setOwner(StringUtils.trimToNull(owner));
        }
        if (addBlocks != null && !addBlocks.isEmpty()) {
            if (item.getBlocks() == null) {
                item.setBlocks(new ArrayList<>());
            }
            for (String id : addBlocks) {
                if (StringUtils.isNotBlank(id) && !item.getBlocks().contains(id.trim())) {
                    item.getBlocks().add(id.trim());
                }
            }
        }
        if (addBlockedBy != null && !addBlockedBy.isEmpty()) {
            if (item.getBlockedBy() == null) {
                item.setBlockedBy(new ArrayList<>());
            }
            for (String id : addBlockedBy) {
                if (StringUtils.isNotBlank(id) && !item.getBlockedBy().contains(id.trim())) {
                    item.getBlockedBy().add(id.trim());
                }
            }
        }
        persistUpsert(item);
        return Optional.of(item);
    }

    public synchronized Optional<SessionTaskItem> update(String taskId,
                                                         String subject,
                                                         String description,
                                                         String status,
                                                         String activeForm,
                                                         String owner) {
        return update(taskId, subject, description, status, activeForm, owner, null, null);
    }

    public List<SessionTaskItem> list() {
        List<SessionTaskItem> items = new ArrayList<>(tasks.values());
        items.sort(Comparator.comparingInt(item -> {
            try {
                return Integer.parseInt(item.getId());
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }));
        return items;
    }

    public synchronized boolean delete(String taskId) {
        boolean removed = tasks.remove(StringUtils.trimToEmpty(taskId)) != null;
        if (removed) {
            persistDelete(taskId);
        }
        return removed;
    }

    public synchronized List<SessionTaskItem> replaceAll(List<Map<String, Object>> todoMaps) {
        List<SessionTaskItem> oldSnapshot = list();
        tasks.clear();
        highWaterMark.set(0);
        if (todoMaps == null || todoMaps.isEmpty()) {
            persistReplace(List.of());
            return oldSnapshot;
        }
        boolean allDone = true;
        for (Map<String, Object> raw : todoMaps) {
            if (raw == null) {
                continue;
            }
            String status = normalizeStatus(String.valueOf(
                    firstNonNull(raw.get("status"), SessionTaskItem.STATUS_PENDING)));
            if (!SessionTaskItem.STATUS_COMPLETED.equals(status)) {
                allDone = false;
            }
        }
        if (allDone) {
            persistReplace(List.of());
            return oldSnapshot;
        }
        for (Map<String, Object> raw : todoMaps) {
            if (raw == null) {
                continue;
            }
            String content = firstNonBlank(
                    str(raw.get("content")),
                    str(raw.get("subject")),
                    str(raw.get("description")));
            if (StringUtils.isBlank(content)) {
                continue;
            }
            String status = normalizeStatus(String.valueOf(
                    firstNonNull(raw.get("status"), SessionTaskItem.STATUS_PENDING)));
            String activeForm = StringUtils.trimToNull(str(raw.get("activeForm")));
            String description = firstNonBlank(str(raw.get("description")), content);
            String subject = firstNonBlank(str(raw.get("subject")), content);
            SessionTaskItem item = createWithoutPersist(subject, description, activeForm, null);
            item.setStatus(status);
        }
        persistReplace(list());
        return oldSnapshot;
    }

    private SessionTaskItem createWithoutPersist(String subject,
                                                 String description,
                                                 String activeForm,
                                                 Map<String, Object> metadata) {
        String id = String.valueOf(highWaterMark.incrementAndGet());
        SessionTaskItem item = SessionTaskItem.builder()
                .id(id)
                .subject(subject.trim())
                .description(description.trim())
                .activeForm(StringUtils.trimToNull(activeForm))
                .status(SessionTaskItem.STATUS_PENDING)
                .blocks(new ArrayList<>())
                .blockedBy(new ArrayList<>())
                .metadata(metadata == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(metadata))
                .build();
        tasks.put(id, item);
        return item;
    }

    public List<Map<String, Object>> toClientTaskList() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SessionTaskItem item : list()) {
            result.add(item.toDetailMap());
        }
        return result;
    }

    public Map<String, Object> toClientSnapshot() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("listId", listId);
        body.put("tasks", toClientTaskList());
        body.put("total", tasks.size());
        long pending = list().stream().filter(t -> SessionTaskItem.STATUS_PENDING.equals(t.getStatus())).count();
        long inProgress = list().stream().filter(t -> SessionTaskItem.STATUS_IN_PROGRESS.equals(t.getStatus())).count();
        long completed = list().stream().filter(t -> SessionTaskItem.STATUS_COMPLETED.equals(t.getStatus())).count();
        body.put("pending", pending);
        body.put("inProgress", inProgress);
        body.put("completed", completed);
        return body;
    }

    private void persistUpsert(SessionTaskItem item) {
        if (persistence == null || item == null) {
            return;
        }
        try {
            persistence.upsertTodo(listId, item);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private void persistDelete(String taskId) {
        if (persistence == null) {
            return;
        }
        try {
            persistence.deleteTodo(listId, taskId);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private void persistReplace(List<SessionTaskItem> items) {
        if (persistence == null) {
            return;
        }
        try {
            persistence.replaceTodos(listId, items == null ? List.of() : items);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static String normalizeStatus(String status) {
        String normalized = status.trim().toLowerCase().replace('-', '_');
        return switch (normalized) {
            case SessionTaskItem.STATUS_PENDING,
                 SessionTaskItem.STATUS_IN_PROGRESS,
                 SessionTaskItem.STATUS_COMPLETED -> normalized;
            case "inprogress" -> SessionTaskItem.STATUS_IN_PROGRESS;
            case "done" -> SessionTaskItem.STATUS_COMPLETED;
            default -> throw new IllegalArgumentException(
                    "非法 status: " + status + "，允许: pending | in_progress | completed");
        };
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return "";
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }
}

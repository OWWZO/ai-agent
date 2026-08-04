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
 * 会话级 Todo 任务列表存储（对标 cc-haha utils/tasks 内存版）。
 * listId 默认用 sessionId，保证同会话内主/子 Agent 共享（若挂到同一 AgentContext 引用）。
 */
public class SessionTaskListStore {

    private final String listId;
    private final AtomicInteger highWaterMark = new AtomicInteger(0);
    private final Map<String, SessionTaskItem> tasks = new ConcurrentHashMap<>();

    public SessionTaskListStore(String listId) {
        this.listId = StringUtils.defaultIfBlank(listId, "default");
    }

    public String getListId() {
        return listId;
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
        // 自增序号只在 create 的同步区内分配，保证主 Agent 和子 Agent 共用 store 时不会得到重复 id。
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
        // update 是部分字段更新；null 表示保持原值，空字符串仅在显式允许的字段上清空。
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
                // 阻塞关系按任务 id 去重，避免重复事件不断扩大内存列表。
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
        return Optional.of(item);
    }

    /** 兼容旧签名 */
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
        return tasks.remove(StringUtils.trimToEmpty(taskId)) != null;
    }

    /**
     * TodoWrite 整表替换（对标 cc-haha TodoWrite 写 AppState.todos）。
     * 若全部 completed，清空列表（与 cchaha allDone 清空一致）。
     */
    public synchronized List<SessionTaskItem> replaceAll(List<Map<String, Object>> todoMaps) {
        List<SessionTaskItem> oldSnapshot = list();
        tasks.clear();
        highWaterMark.set(0);
        if (todoMaps == null || todoMaps.isEmpty()) {
            return oldSnapshot;
        }
        boolean allDone = true;
        // 先检查整表是否全部完成；完成列表按协议直接清空，不把历史完成项继续暴露为活动 Todo。
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
            // cchaha: allDone ? [] : todos — 全部完成则清空
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
            // 忽略客户端 id，统一使用本地自增序号，避免跨轮/跨来源 id 冲突。
            SessionTaskItem item = create(subject, description, activeForm, null);
            item.setStatus(status);
        }
        return oldSnapshot;
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
        // 列表和统计从同一份当前快照计算，前端无需再按状态二次遍历或猜测总数。
        body.put("total", tasks.size());
        long pending = list().stream().filter(t -> SessionTaskItem.STATUS_PENDING.equals(t.getStatus())).count();
        long inProgress = list().stream().filter(t -> SessionTaskItem.STATUS_IN_PROGRESS.equals(t.getStatus())).count();
        long completed = list().stream().filter(t -> SessionTaskItem.STATUS_COMPLETED.equals(t.getStatus())).count();
        body.put("pending", pending);
        body.put("inProgress", inProgress);
        body.put("completed", completed);
        return body;
    }

    private static String normalizeStatus(String status) {
        String normalized = status.trim().toLowerCase().replace('-', '_');
        // 对外兼容 done/inprogress 别名，内部只保留三种规范状态，保证排序和统计分支稳定。
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

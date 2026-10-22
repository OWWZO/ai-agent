package org.wwz.ai.domain.agent.runtime.tasklist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话 Todo 任务项。
 * 与后台运行任务（TaskStop）是不同概念。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionTaskItem {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";

    private String id;
    private String subject;
    private String description;
    /** spinner 进行时文案 */
    private String activeForm;
    private String owner;
    private String status;
    @Builder.Default
    private List<String> blocks = new ArrayList<>();
    @Builder.Default
    private List<String> blockedBy = new ArrayList<>();
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public Map<String, Object> toSummaryMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("subject", subject);
        map.put("status", status);
        return map;
    }

    public Map<String, Object> toDetailMap() {
        Map<String, Object> map = toSummaryMap();
        map.put("description", description);
        map.put("activeForm", activeForm);
        map.put("owner", owner);
        map.put("blocks", blocks == null ? List.of() : new ArrayList<>(blocks));
        map.put("blockedBy", blockedBy == null ? List.of() : new ArrayList<>(blockedBy));
        map.put("metadata", metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
        return map;
    }
}

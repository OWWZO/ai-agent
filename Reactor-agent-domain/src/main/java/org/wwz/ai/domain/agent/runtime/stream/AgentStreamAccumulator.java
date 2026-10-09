package org.wwz.ai.domain.agent.runtime.stream;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 流事件的运行时聚合状态。
 * <p>负责 task/order/planner/result state，不负责 HTTP 包装或账本持久化。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStreamAccumulator {

    @Builder.Default
    private AtomicInteger messageCount = new AtomicInteger(0);

    @Builder.Default
    private Map<String, Integer> orderMapping = new HashMap<>();

    private Boolean initPlan;
    private String taskId;

    @Builder.Default
    private AtomicInteger taskOrder = new AtomicInteger(1);

    @Builder.Default
    private List<String> streamTaskMessageType = new ArrayList<>(List.of(
            "html", "markdown", "knowledge", "deep_search", "tool_thought",
            "llm_reasoning", "tool_call", "tool_call_delta", "data_analysis"));

    @Builder.Default
    private Map<String, Object> resultMap = new HashMap<>();

    @Builder.Default
    private List<Object> resultList = new ArrayList<>();

    public static final String PLANNER_ROUND_ID_KEY = "plannerRoundId";

    public synchronized Integer getAndIncrOrder(String key) {
        Integer order = orderMapping.get(key);
        if (Objects.isNull(order)) {
            orderMapping.put(key, 1);
            return 1;
        }
        orderMapping.put(key, order + 1);
        return order + 1;
    }

    public synchronized Boolean isInitPlan() {
        if (Objects.isNull(initPlan) || Boolean.FALSE.equals(initPlan)) {
            initPlan = true;
            return true;
        }
        return false;
    }

    public synchronized String getTaskId() {
        if (taskId == null || taskId.isEmpty()) {
            taskId = UUID.randomUUID().toString();
        }
        return taskId;
    }

    public synchronized String renewTaskId() {
        taskOrder.set(1);
        taskId = UUID.randomUUID().toString();
        return taskId;
    }

    @SuppressWarnings("unchecked")
    public synchronized List<Object> getResulMapTask() {
        Object tasks = resultMap.get("tasks");
        return tasks instanceof List<?> ? (List<Object>) tasks : null;
    }

    public synchronized void setResultMapTask(List<Object> task) {
        List<Object> tasks = getResulMapTask();
        if (tasks == null) {
            tasks = new ArrayList<>();
            resultMap.put("tasks", tasks);
        }
        tasks.add(task);
    }

    @SuppressWarnings("unchecked")
    public synchronized void setResultMapSubTask(Object subTask) {
        List<Object> tasks = getResulMapTask();
        if (tasks == null) {
            tasks = new ArrayList<>();
            tasks.add(new ArrayList<>());
            resultMap.put("tasks", tasks);
        }
        List<Object> subTasks = (List<Object>) tasks.get(tasks.size() - 1);
        subTasks.add(subTask);
    }

    public synchronized String getPlannerRoundId() {
        Object plannerRoundId = resultMap.get(PLANNER_ROUND_ID_KEY);
        return plannerRoundId == null ? null : String.valueOf(plannerRoundId);
    }

    public synchronized void setPlannerRoundId(String plannerRoundId) {
        if (plannerRoundId == null || plannerRoundId.isBlank()) {
            resultMap.remove(PLANNER_ROUND_ID_KEY);
            return;
        }
        resultMap.put(PLANNER_ROUND_ID_KEY, plannerRoundId);
    }
}

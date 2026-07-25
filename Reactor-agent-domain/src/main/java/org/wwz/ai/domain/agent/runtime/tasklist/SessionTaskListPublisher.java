package org.wwz.ai.domain.agent.runtime.tasklist;

import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;

import java.util.Map;

/**
 * 任务列表变更后推送 SSE（对标 cchaha TaskListV2 实时刷新）。
 */
public final class SessionTaskListPublisher {

    private SessionTaskListPublisher() {
    }

    public static void publish(AgentContext agentContext) {
        if (agentContext == null || agentContext.getPrinter() == null) {
            return;
        }
        SessionTaskListStore store = agentContext.requireSessionTaskList();
        Map<String, Object> snapshot = store.toClientSnapshot();
        agentContext.getPrinter().send(TaskToolNames.SESSION_TASKS_EVENT, snapshot);
    }
}

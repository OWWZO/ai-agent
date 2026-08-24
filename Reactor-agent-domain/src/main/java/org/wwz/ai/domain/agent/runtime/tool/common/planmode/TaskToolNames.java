package org.wwz.ai.domain.agent.runtime.tool.common.planmode;

/**
 * Task / Plan Mode 工具名常量。
 * Task* = Todo V2；TodoWrite = V1 整表写入（兼容）。
 */
public final class TaskToolNames {

    public static final String TASK_CREATE = "TaskCreate";
    public static final String TASK_GET = "TaskGet";
    public static final String TASK_UPDATE = "TaskUpdate";
    public static final String TASK_LIST = "TaskList";
    public static final String TASK_STOP = "TaskStop";
    public static final String TASK_OUTPUT = "TaskOutput";
    public static final String SEND_MESSAGE = "SendMessage";
    public static final String TODO_WRITE = "TodoWrite";
    public static final String ENTER_PLAN_MODE = "EnterPlanMode";
    public static final String EXIT_PLAN_MODE = "ExitPlanMode";

    /** SSE 消息类型：会话任务列表快照 */
    public static final String SESSION_TASKS_EVENT = "session_tasks";

    private TaskToolNames() {
    }
}

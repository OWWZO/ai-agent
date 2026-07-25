package org.wwz.ai.domain.agent.runtime.tasklist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 后台运行任务（对标 cc-haha AppState.tasks / TaskStop 对象）。
 * 与 SessionTaskItem（Todo 列表）分离。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeBackgroundTask {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_STOPPED = "stopped";
    public static final String STATUS_FAILED = "failed";

    public static final String TYPE_LOCAL_AGENT = "local_agent";
    public static final String TYPE_LOCAL_SHELL = "local_shell";
    public static final String TYPE_GENERIC = "generic";

    private String id;
    private String type;
    private String status;
    private String description;
    private String command;
    private long startedAtMs;
    private Long endedAtMs;
}

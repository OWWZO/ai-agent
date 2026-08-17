package org.wwz.ai.domain.agent.runtime.tasklist;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;

import java.util.concurrent.Future;

/**
 * 后台运行任务（对标 cc-haha AppState.tasks / TaskStop / TaskOutput 对象）。
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

    /** 子 Agent id（local_agent） */
    private String agentId;
    private String agentType;
    private String prompt;

    /** 终态输出 / 错误 */
    private String output;
    private String errorMsg;
    private Integer totalToolUseCount;
    private Long totalDurationMs;

    /** 任务级取消令牌（TaskStop / 父 run cancel） */
    @ToString.Exclude
    @JSONField(serialize = false)
    private RunCancellation cancellation;

    /** 后台执行 Future，供 interrupt */
    @ToString.Exclude
    @JSONField(serialize = false)
    private Future<?> future;
}

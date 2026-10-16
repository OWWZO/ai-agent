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
 * 后台运行任务。
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
    /**
     * 执行体随进程消亡、永远等不到终态回调的孤儿（进程重启 / OOM / 滚动发布）。
     * <p>
     * 与 {@link #STATUS_FAILED} 区分：failed 是任务自己跑失败了，lost 是「没人知道结果」。
     * 对齐参照仓库把 lost 做成一等终态的做法。
     */
    public static final String STATUS_LOST = "lost";

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
    /** 子 Agent 对应的真实子会话 ID（{@code sub_{agentId}}）；前端据此打开只读详情 */
    private String childSessionId;
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

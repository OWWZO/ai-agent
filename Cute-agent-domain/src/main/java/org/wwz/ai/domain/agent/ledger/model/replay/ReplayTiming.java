package org.wwz.ai.domain.agent.ledger.model.replay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 单个回放事件的时间信息。durationMs 表示一次 invocation 的耗时，不代表步骤组墙钟耗时。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayTiming implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String SOURCE_LEDGER = "ledger";
    public static final String SOURCE_RUNTIME = "runtime";
    public static final String SOURCE_PAYLOAD = "payload";
    public static final String SOURCE_INFERRED = "inferred";

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String source;

    /** 运行时计时起点。 */
    public static ReplayTiming startRuntime() {
        return ReplayTiming.builder()
                .startedAt(LocalDateTime.now())
                .source(SOURCE_RUNTIME)
                .build();
    }

    /**
     * 收口运行时计时：已收口或未开始则原样返回，避免重复覆盖既有耗时。
     */
    public static ReplayTiming finishRuntime(ReplayTiming timing) {
        if (timing == null || timing.getFinishedAt() != null || timing.getStartedAt() == null) {
            return timing;
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        timing.setFinishedAt(finishedAt);
        timing.setDurationMs(Math.max(0L, Duration.between(timing.getStartedAt(), finishedAt).toMillis()));
        timing.setSource(SOURCE_RUNTIME);
        return timing;
    }

    /** 复制一份独立计时，避免下游改动污染原对象。 */
    public static ReplayTiming copyOf(ReplayTiming timing) {
        if (timing == null) {
            return null;
        }
        return ReplayTiming.builder()
                .startedAt(timing.getStartedAt())
                .finishedAt(timing.getFinishedAt())
                .durationMs(timing.getDurationMs())
                .source(timing.getSource())
                .build();
    }
}

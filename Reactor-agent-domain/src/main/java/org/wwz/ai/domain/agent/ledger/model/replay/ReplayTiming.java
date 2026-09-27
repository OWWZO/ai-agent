package org.wwz.ai.domain.agent.ledger.model.replay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
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
}

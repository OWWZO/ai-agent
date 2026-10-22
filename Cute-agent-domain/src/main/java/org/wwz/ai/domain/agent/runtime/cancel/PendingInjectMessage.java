package org.wwz.ai.domain.agent.runtime.cancel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 运行中注入的用户/协调消息（控制面 inbox，非新开 run）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingInjectMessage {

    public static final String SOURCE_USER = "user";
    public static final String SOURCE_COORDINATOR = "coordinator";

    private String text;
    private String source;
    private long createdAtMs;
}

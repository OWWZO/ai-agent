package org.wwz.ai.domain.agent.runtime.stream;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * runtime 投影结果。
 * <p>字段使用运行时语义；Case mapper 负责将其转换为对外增量 frame。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStreamResult {

    @Builder.Default
    private String status = "running";
    @Builder.Default
    private String contentDelta = "";
    @Builder.Default
    private String content = "";
    private boolean complete;
    private long elapsedMillis;
    private long tokenCount;
    private Map<String, Object> eventData;
    @Builder.Default
    private String contentType = "markdown";
    private String traceId;
    private String requestId;
    private boolean encrypted;
    private String query;
    private List<String> messages;
    @Builder.Default
    private String frameType = "result";
    private String errorMessage;
    private long sequence;
    private Long retryAfterMs;
}

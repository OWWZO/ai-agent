package org.wwz.ai.application.agent.stream;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Case 层流 frame。字段名称保持现有浏览器增量协议，Trigger 只负责把它序列化为 HTTP/SSE。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionStreamFrame {

    private String status;
    private String response;
    private String responseAll;
    private boolean finished;
    private long useTimes;
    private long useTokens;
    private Map<String, Object> resultMap;
    private String responseType;
    private String traceId;
    private String reqId;
    private boolean encrypted;
    private String query;
    private List<String> messages;
    private String packageType;
    private String errorMsg;
    private long eventSeq;
    private Long retryMs;
}

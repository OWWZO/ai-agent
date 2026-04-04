package org.wwz.ai.trigger.http.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageRespVO {
    private String requestId;
    private String sessionId;
    private Integer sortOrder;
    private String query;
    private Integer agentType;
    private Integer status;
    private Integer forceStop;
    // Chat模式
    private String response;
    // 深度思考
    private String thought;
    private String planJson;
    // 深度模式共用 (原始JSON字符串，前端直接parse)
    private String tasksJson;
    private String multiAgentJson;
    private String conclusionJson;
    private String planListJson;
    private String renderSnapshotJson;
    private String metricsJson;
    // 文件
    private String filesJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
}

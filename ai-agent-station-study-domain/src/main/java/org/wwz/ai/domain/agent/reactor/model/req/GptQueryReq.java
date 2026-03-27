package org.wwz.ai.domain.agent.reactor.model.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GptQueryReq {
    private String query;
    private String sessionId;
    private String requestId;
    private Integer deepThink;
    /**
     * 前端传入交付物格式：html(网页模式）,docs(文档模式）， table(表格模式）
     */
    private String outputStyle;
    private String traceId;
    private String user;
}

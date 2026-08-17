package org.wwz.ai.domain.agent.reactor.model.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;

import java.util.List;

/**
 * 浏览器 GptQuery 请求契约，作为运行时 AgentRequest 的上游输入。
 */
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
    private String aiAgentId;
    /**
     * 本轮模型引用（modelId 或上游 modelName）；空则后端默认。
     */
    private String model;
    /** 是否开启深度思考（本轮覆盖） */
    private Boolean thinking;
    /** 思考档位 low|medium|high；thinking=true 且空时后端可用 medium */
    private String thinkingEffort;
    /**
     * 当前轮上传附件元数据，供 ReAct / PlanSolve 链路桥接到会话上下文。
     */
    private List<FileInformation> sessionFiles;
}

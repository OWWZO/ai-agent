package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 多模态 Agent 请求模型，承载文本、图片/文件引用及运行参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiModalAgentRequest {
    private String requestId;
    private String question;
    private String query;
    private Boolean stream;
    private Boolean contentStream;
    private Map<String, Object> streamMode;
}

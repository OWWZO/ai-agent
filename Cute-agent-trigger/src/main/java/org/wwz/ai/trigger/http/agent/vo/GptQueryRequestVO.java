package org.wwz.ai.trigger.http.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 主对话 HTTP 请求。字段保持现有 /web/api/v1/gpt/queryAgentStreamIncr JSON 契约。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GptQueryRequestVO {

    private String query;
    private String sessionId;
    private String requestId;
    private Integer deepThink;
    private String outputStyle;
    private String traceId;
    private String user;
    private String model;
    private Boolean thinking;
    private String thinkingEffort;
    private List<FileReferenceVO> sessionFiles;
    private Boolean forcePlanMode;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileReferenceVO {
        private String fileName;
        private String fileDesc;
        private String ossUrl;
        private String domainUrl;
        private Integer fileSize;
        private String fileType;
        private String resourceKey;
        private String mimeType;
        private String originFileName;
        private String originFileUrl;
        private String originOssUrl;
        private String originDomainUrl;
    }
}

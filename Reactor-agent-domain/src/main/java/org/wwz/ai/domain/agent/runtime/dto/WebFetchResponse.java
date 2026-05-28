package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * web_fetch 工具响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebFetchResponse {
    private Integer code;
    private DataPayload data;
    private List<CodeInterpreterResponse.FileInfo> fileInfo;
    private String requestId;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPayload {
        private String title;
        private String finalUrl;
        private String content;
        private String contentFormat;
        private Integer wordCount;
        private Boolean truncated;
        private String contentSource;
        @Builder.Default
        private Map<String, Object> metadata = Map.of();
    }

    public List<CodeInterpreterResponse.FileInfo> safeFileInfo() {
        return fileInfo == null ? new ArrayList<>() : fileInfo;
    }
}

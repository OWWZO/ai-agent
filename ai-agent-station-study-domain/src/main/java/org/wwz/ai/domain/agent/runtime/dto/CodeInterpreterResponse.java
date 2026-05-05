package org.wwz.ai.domain.agent.runtime.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeInterpreterResponse {
    private String requestsId;
    private String resultType;
    private String content;
    private String code;
    private String codeOutput;
    private List<FileInfo> fileInfo;
    private String explain;
    private Integer step;
    private String data;
    private Boolean isFinal;
    private String toolCallId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileInfo {
        private String fileName;
        private String ossUrl;
        private String domainUrl;
        private Integer fileSize;
    }
}

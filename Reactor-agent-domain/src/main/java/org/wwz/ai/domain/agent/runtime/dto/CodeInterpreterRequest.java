package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeInterpreterRequest {
    private String requestId;
    private String query;
    private String task;
    private List<String> fileNames;
    private List<FileInfo> originFileNames;
    private String fileName;
    private String fileDescription;
    private String fileType;
    private Boolean stream;
    private Boolean contentStream;
    private Map<String, Object> streamMode;
    private String templateType;

    @Data
    @AllArgsConstructor
    public static class FileInfo {
        public String fileName;
        public String originFileName;
        public String originOssUrl;
    }
}

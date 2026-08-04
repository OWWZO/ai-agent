package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Web 搜索工具响应模型，承载搜索条目、摘要和来源信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchrResponse {
    private Integer code;
    private List<SreachDoc> data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SreachDoc {
        private String source_url;
        private String page_content;
        private String name;
    }
}

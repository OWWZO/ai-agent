package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DeepSearch 工具响应模型，承载搜索阶段结果、章节总结和最终摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepSearchrResponse {
    private String requestId;
    private String query;
    private String answer;
    private SearchResult searchResult;
    private Boolean isFinal;
    private Boolean searchFinish; // 搜索结果是否结束
    private String messageType; // extend、search、chapter_summary、report
    private String toolCallId;
    private String chapterId;
    private String chapterTitle;
    private String chapterContent;
    private Integer chapterOrder;
    private String chapterSummary;
    private Boolean chapterStreaming;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResult {
        private List<String> query;
        private List<List<SearchDoc>> docs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchDoc {
        private String doc_type;
        private String content;
        private String title;
        private String link;
    }
}

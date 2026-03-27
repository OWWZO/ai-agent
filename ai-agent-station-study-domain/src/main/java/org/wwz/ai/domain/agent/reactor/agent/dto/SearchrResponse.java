package org.wwz.ai.domain.agent.reactor.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

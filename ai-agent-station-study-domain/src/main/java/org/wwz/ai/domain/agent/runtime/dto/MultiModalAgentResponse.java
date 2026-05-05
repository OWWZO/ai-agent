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
public class MultiModalAgentResponse {
    private String id;
    private List<Choice> choices;
    private Long created;
    private String model;
    private String object;
    private Usage usage;

    /**
     * 兼容非 OpenAI 标准流式返回。
     */
    private String data;
    private Boolean isFinal;
    private String toolCallId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        private Delta delta;
        private String finishReason;
        private Integer index;
        private Object logprobs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Delta {
        private String content;
        private Object functionCall;
        private String refusal;
        private String role;
        private Object toolCalls;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}

package org.wwz.ai.domain.agent.reactor.service.support;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionTurnMemory;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化会话记忆生成器。
 */
public interface SessionMemorySummaryGenerator {

    String generate(GenerationRequest request) throws Exception;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class GenerationRequest {
        private String requestId;
        private String sessionId;
        private Integer agentType;
        private String existingSummary;
        @Builder.Default
        private List<SessionTurnMemory> turnsToCompact = new ArrayList<>();
        @Builder.Default
        private List<JSONObject> artifactRefs = new ArrayList<>();
        private Integer maxLength;
        private Integer boundarySortOrder;
    }
}

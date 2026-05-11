package org.wwz.ai.domain.agent.reactor.data.dto;


import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class VectorSaveReq {
    private String collectionName;
    private List<VectorData> dataList;

    @Data
    public static class VectorData {
        private String embeddingText;
        private String uuid;
        private Map<String, Object> payloads;
    }
}

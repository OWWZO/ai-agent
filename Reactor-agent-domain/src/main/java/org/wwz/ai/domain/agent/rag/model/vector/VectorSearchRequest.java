package org.wwz.ai.domain.agent.rag.model.vector;

import java.util.List;
import java.util.Map;

/**
 * 向量检索请求。
 */
public final class VectorSearchRequest {

    private final String collectionName;
    private final List<Float> vector;
    private final int limit;
    private final Float scoreThreshold;
    private final Map<String, Object> filters;
    private final List<String> payloads;
    private final Long timeoutMillis;

    public VectorSearchRequest(String collectionName,
                               List<Float> vector,
                               int limit,
                               Float scoreThreshold,
                               Map<String, Object> filters,
                                List<String> payloads,
                                Long timeoutMillis) {
        this.collectionName = collectionName;
        this.vector = List.copyOf(vector == null ? List.of() : vector);
        this.limit = limit;
        this.scoreThreshold = scoreThreshold;
        this.filters = Map.copyOf(filters == null ? Map.of() : filters);
        this.payloads = List.copyOf(payloads == null ? List.of() : payloads);
        this.timeoutMillis = timeoutMillis;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public List<Float> getVector() {
        return vector;
    }

    public int getLimit() {
        return limit;
    }

    public Float getScoreThreshold() {
        return scoreThreshold;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    public List<String> getPayloads() {
        return payloads;
    }

    public Long getTimeoutMillis() {
        return timeoutMillis;
    }
}

package org.wwz.ai.domain.agent.rag.model.vector;

import java.util.List;
import java.util.Map;

/**
 * Domain-level vector recall input. It contains no transport DTO fields.
 */
public final class VectorRecallRequest {

    private final String query;
    private final String collectionName;
    private final int limit;
    private final Float scoreThreshold;
    private final Map<String, Object> filters;
    private final List<String> payloads;
    private final Long timeoutMillis;

    public VectorRecallRequest(String query,
                               String collectionName,
                               int limit,
                               Float scoreThreshold,
                               Map<String, Object> filters,
                               List<String> payloads,
                               Long timeoutMillis) {
        this.query = query;
        this.collectionName = collectionName;
        this.limit = limit;
        this.scoreThreshold = scoreThreshold;
        this.filters = Map.copyOf(filters == null ? Map.of() : filters);
        this.payloads = List.copyOf(payloads == null ? List.of() : payloads);
        this.timeoutMillis = timeoutMillis;
    }

    public String getQuery() {
        return query;
    }

    public String getCollectionName() {
        return collectionName;
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

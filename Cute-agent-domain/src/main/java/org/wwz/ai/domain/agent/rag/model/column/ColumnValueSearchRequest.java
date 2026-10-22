package org.wwz.ai.domain.agent.rag.model.column;

import java.util.List;

/**
 * 列值文本召回请求。
 */
public final class ColumnValueSearchRequest {

    private final String query;
    private final List<String> modelCodes;
    private final int limit;

    public ColumnValueSearchRequest(String query, List<String> modelCodes, int limit) {
        this.query = query;
        this.modelCodes = List.copyOf(modelCodes == null ? List.of() : modelCodes);
        this.limit = limit;
    }

    public String getQuery() {
        return query;
    }

    public List<String> getModelCodes() {
        return modelCodes;
    }

    public int getLimit() {
        return limit;
    }
}

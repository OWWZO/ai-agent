package org.wwz.ai.domain.agent.rag.model.vector;

import java.util.List;

/**
 * 向量召回结果。
 */
public final class VectorSearchResult {

    private final List<VectorSearchHit> hits;

    public VectorSearchResult(List<VectorSearchHit> hits) {
        this.hits = List.copyOf(hits == null ? List.of() : hits);
    }

    public static VectorSearchResult empty() {
        return new VectorSearchResult(List.of());
    }

    public List<VectorSearchHit> getHits() {
        return hits;
    }
}

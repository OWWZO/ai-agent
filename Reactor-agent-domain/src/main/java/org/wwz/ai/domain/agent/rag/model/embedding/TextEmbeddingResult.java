package org.wwz.ai.domain.agent.rag.model.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本向量化结果。
 *
 * <p>不可用结果使用空向量表示，调用方可以据此执行 RAG 降级。</p>
 */
public final class TextEmbeddingResult {

    private final List<List<Float>> vectors;
    private final boolean available;

    public TextEmbeddingResult(List<List<Float>> vectors, boolean available) {
        if (vectors == null || vectors.isEmpty()) {
            this.vectors = List.of();
        } else {
            List<List<Float>> copiedVectors = new ArrayList<>(vectors.size());
            for (List<Float> vector : vectors) {
                copiedVectors.add(List.copyOf(vector == null ? List.of() : vector));
            }
            this.vectors = List.copyOf(copiedVectors);
        }
        this.available = available;
    }

    public static TextEmbeddingResult available(List<List<Float>> vectors) {
        return new TextEmbeddingResult(vectors, true);
    }

    public static TextEmbeddingResult unavailable() {
        return new TextEmbeddingResult(List.of(), false);
    }

    public List<List<Float>> getVectors() {
        return vectors;
    }

    public boolean isAvailable() {
        return available;
    }
}

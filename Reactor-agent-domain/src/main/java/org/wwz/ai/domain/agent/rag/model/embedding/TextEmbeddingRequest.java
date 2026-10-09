package org.wwz.ai.domain.agent.rag.model.embedding;

import java.util.List;

/**
 * 文本向量化请求。
 *
 * <p>只描述 RAG 需要的输入语义，不携带远端 embedding 服务的请求 DTO。</p>
 */
public final class TextEmbeddingRequest {

    private final List<String> texts;
    private final boolean normalize;

    public TextEmbeddingRequest(List<String> texts, boolean normalize) {
        this.texts = List.copyOf(texts == null ? List.of() : texts);
        this.normalize = normalize;
    }

    public List<String> getTexts() {
        return texts;
    }

    public boolean isNormalize() {
        return normalize;
    }
}

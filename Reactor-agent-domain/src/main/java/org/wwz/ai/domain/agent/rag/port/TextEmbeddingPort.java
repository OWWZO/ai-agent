package org.wwz.ai.domain.agent.rag.port;

import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingRequest;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingResult;

/**
 * 文本 embedding 能力端口。
 */
public interface TextEmbeddingPort {

    TextEmbeddingResult embed(TextEmbeddingRequest request);
}

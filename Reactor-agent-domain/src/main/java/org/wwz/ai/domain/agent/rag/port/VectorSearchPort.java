package org.wwz.ai.domain.agent.rag.port;

import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchRequest;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchResult;

/**
 * 向量检索端口。
 */
public interface VectorSearchPort {

    VectorSearchResult search(VectorSearchRequest request) throws Exception;
}

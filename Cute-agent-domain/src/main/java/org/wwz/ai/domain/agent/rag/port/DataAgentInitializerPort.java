package org.wwz.ai.domain.agent.rag.port;

/**
 * Optional index preparation required by DataAgent before model metadata is refreshed.
 */
public interface DataAgentInitializerPort {

    void initializeVectorIndex(boolean forceRefresh, int embeddingDimension) throws Exception;

    void initializeColumnValueIndex(boolean forceRefresh) throws Exception;
}

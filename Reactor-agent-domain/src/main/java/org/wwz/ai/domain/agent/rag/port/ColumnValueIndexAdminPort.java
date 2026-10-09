package org.wwz.ai.domain.agent.rag.port;

import org.wwz.ai.domain.agent.rag.model.column.ColumnValueDocument;

import java.util.List;

/**
 * 列值索引管理端口。
 */
public interface ColumnValueIndexAdminPort {

    boolean isAvailable();

    void initializeIndex();

    void recreateIndex();

    boolean indexExists(String indexName);

    boolean createIndex(String indexName, String definition);

    boolean deleteIndex(String indexName);

    boolean bulkInsert(String indexName, List<ColumnValueDocument> documents);
}

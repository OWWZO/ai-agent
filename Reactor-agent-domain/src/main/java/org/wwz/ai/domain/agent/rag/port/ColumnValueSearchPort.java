package org.wwz.ai.domain.agent.rag.port;

import org.wwz.ai.domain.agent.rag.model.column.ColumnValueSearchRequest;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueSearchResult;

/**
 * 列值文本检索端口。
 */
public interface ColumnValueSearchPort {

    ColumnValueSearchResult search(ColumnValueSearchRequest request) throws Exception;
}

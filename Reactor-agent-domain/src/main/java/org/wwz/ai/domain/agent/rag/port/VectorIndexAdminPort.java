package org.wwz.ai.domain.agent.rag.port;

import org.wwz.ai.domain.agent.rag.model.vector.VectorFilter;
import org.wwz.ai.domain.agent.rag.model.vector.VectorPoint;

import java.util.List;

/**
 * 向量集合与点数据管理端口。
 */
public interface VectorIndexAdminPort {

    boolean collectionExists(String collectionName) throws Exception;

    void createCollection(String collectionName, int dimension) throws Exception;

    void recreateCollection(String collectionName, int dimension) throws Exception;

    void upsert(String collectionName, List<VectorPoint> points) throws Exception;

    void deleteByIds(String collectionName, List<String> ids) throws Exception;

    void deleteByFilter(String collectionName, VectorFilter filter) throws Exception;
}

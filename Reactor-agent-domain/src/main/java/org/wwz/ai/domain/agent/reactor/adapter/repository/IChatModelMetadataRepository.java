package org.wwz.ai.domain.agent.reactor.adapter.repository;

import org.wwz.ai.domain.agent.ledger.entity.ChatModelInfo;
import org.wwz.ai.domain.agent.ledger.entity.ChatModelSchema;

import java.util.List;

/**
 * 问数模型元数据仓储端口。
 * 负责隔离模型与字段元数据的持久化细节。
 */
public interface IChatModelMetadataRepository {

    List<ChatModelInfo> listDistinctModels();

    void saveModelInfo(ChatModelInfo modelInfo);

    void deleteModelInfoByCode(String modelCode);

    List<ChatModelSchema> listDistinctSchemas();

    void saveModelSchemas(List<ChatModelSchema> schemaList);

    void deleteModelSchemasByCode(String modelCode);
}

package org.wwz.ai.domain.agent.rag.port;

import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelInfo;
import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelSchema;

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

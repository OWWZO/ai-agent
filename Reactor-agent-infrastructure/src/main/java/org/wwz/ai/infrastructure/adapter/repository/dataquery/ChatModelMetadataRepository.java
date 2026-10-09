package org.wwz.ai.infrastructure.adapter.repository.dataquery;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelInfo;
import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelSchema;
import org.wwz.ai.domain.agent.rag.port.IChatModelMetadataRepository;
import org.wwz.ai.infrastructure.dao.reactor.chatmodel.ChatModelInfoMapper;
import org.wwz.ai.infrastructure.dao.reactor.chatmodel.ChatModelSchemaMapper;
import org.wwz.ai.infrastructure.dao.po.reactor.chatmodel.ChatModelInfoPO;
import org.wwz.ai.infrastructure.dao.po.reactor.chatmodel.ChatModelSchemaPO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问数模型元数据仓储实现。
 * 问数模型元数据仓储适配器，负责 PO 与 RAG Domain Model 的映射。
 */
@Repository
@RequiredArgsConstructor
public class ChatModelMetadataRepository implements IChatModelMetadataRepository {

    private final ChatModelInfoMapper chatModelInfoMapper;
    private final ChatModelSchemaMapper chatModelSchemaMapper;

    @Override
    public List<ChatModelInfo> listDistinctModels() {
        List<ChatModelInfoPO> modelList = chatModelInfoMapper.selectList(
                Wrappers.<ChatModelInfoPO>lambdaQuery()
                        .orderByDesc(ChatModelInfoPO::getId)
        );
        Map<String, ChatModelInfoPO> modelMap = new LinkedHashMap<>();
        for (ChatModelInfoPO modelInfo : modelList) {
            modelMap.putIfAbsent(modelInfo.getCode(), modelInfo);
        }
        List<ChatModelInfo> result = new ArrayList<>();
        for (ChatModelInfoPO modelInfo : modelMap.values()) {
            result.add(toDomain(modelInfo));
        }
        return result;
    }

    @Override
    public void saveModelInfo(ChatModelInfo modelInfo) {
        chatModelInfoMapper.insert(toPO(modelInfo));
    }

    @Override
    public void deleteModelInfoByCode(String modelCode) {
        chatModelInfoMapper.deletePhysicalByCode(modelCode);
    }

    @Override
    public List<ChatModelSchema> listDistinctSchemas() {
        List<ChatModelSchemaPO> schemaList = chatModelSchemaMapper.selectList(
                Wrappers.<ChatModelSchemaPO>lambdaQuery()
                        .orderByDesc(ChatModelSchemaPO::getId)
        );
        Map<String, ChatModelSchemaPO> schemaMap = new LinkedHashMap<>();
        for (ChatModelSchemaPO schema : schemaList) {
            String key = schema.getModelCode() + "::" + schema.getColumnId();
            schemaMap.putIfAbsent(key, schema);
        }
        List<ChatModelSchema> result = new ArrayList<>();
        for (ChatModelSchemaPO schema : schemaMap.values()) {
            result.add(toDomain(schema));
        }
        return result;
    }

    @Override
    public void saveModelSchemas(List<ChatModelSchema> schemaList) {
        if (CollectionUtils.isEmpty(schemaList)) {
            return;
        }
        // 单模型字段量可控，Phase 2A 先保持显式逐条写入，避免 domain 再暴露批量持久化框架细节。
        for (ChatModelSchema schema : schemaList) {
            chatModelSchemaMapper.insert(toPO(schema));
        }
    }

    @Override
    public void deleteModelSchemasByCode(String modelCode) {
        chatModelSchemaMapper.deletePhysicalByModelCode(modelCode);
    }

    private ChatModelInfo toDomain(ChatModelInfoPO po) {
        ChatModelInfo model = new ChatModelInfo();
        model.setId(po.getId());
        model.setCode(po.getCode());
        model.setType(po.getType());
        model.setContent(po.getContent());
        model.setName(po.getName());
        model.setUsePrompt(po.getUsePrompt());
        model.setBusinessPrompt(po.getBusinessPrompt());
        model.setYn(po.getYn());
        return model;
    }

    private ChatModelInfoPO toPO(ChatModelInfo model) {
        return ChatModelInfoPO.builder()
                .id(model.getId())
                .code(model.getCode())
                .type(model.getType())
                .content(model.getContent())
                .name(model.getName())
                .usePrompt(model.getUsePrompt())
                .businessPrompt(model.getBusinessPrompt())
                .yn(model.getYn())
                .build();
    }

    private ChatModelSchema toDomain(ChatModelSchemaPO po) {
        ChatModelSchema schema = new ChatModelSchema();
        schema.setId(po.getId());
        schema.setModelCode(po.getModelCode());
        schema.setColumnId(po.getColumnId());
        schema.setColumnName(po.getColumnName());
        schema.setColumnComment(po.getColumnComment());
        schema.setFewShot(po.getFewShot());
        schema.setDataType(po.getDataType());
        schema.setSynonyms(po.getSynonyms());
        schema.setVectorUuid(po.getVectorUuid());
        schema.setDefaultRecall(po.getDefaultRecall() == null ? 0 : po.getDefaultRecall());
        schema.setAnalyzeSuggest(po.getAnalyzeSuggest() == null ? 0 : po.getAnalyzeSuggest());
        schema.setYn(po.getYn());
        return schema;
    }

    private ChatModelSchemaPO toPO(ChatModelSchema schema) {
        return ChatModelSchemaPO.builder()
                .id(schema.getId())
                .modelCode(schema.getModelCode())
                .columnId(schema.getColumnId())
                .columnName(schema.getColumnName())
                .columnComment(schema.getColumnComment())
                .fewShot(schema.getFewShot())
                .dataType(schema.getDataType())
                .synonyms(schema.getSynonyms())
                .vectorUuid(schema.getVectorUuid())
                .defaultRecall(schema.getDefaultRecall())
                .analyzeSuggest(schema.getAnalyzeSuggest())
                .yn(schema.getYn())
                .build();
    }
}

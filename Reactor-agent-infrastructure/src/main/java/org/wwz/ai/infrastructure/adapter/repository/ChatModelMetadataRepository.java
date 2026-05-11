package org.wwz.ai.infrastructure.adapter.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.reactor.adapter.repository.IChatModelMetadataRepository;
import org.wwz.ai.domain.agent.ledger.entity.ChatModelInfo;
import org.wwz.ai.domain.agent.ledger.entity.ChatModelSchema;
import org.wwz.ai.infrastructure.dao.reactor.ChatModelInfoMapper;
import org.wwz.ai.infrastructure.dao.reactor.ChatModelSchemaMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问数模型元数据仓储实现。
 * Phase 2A 先在 infrastructure 收口 MyBatis-Plus 细节，避免 domain 继续暴露 BaseMapper。
 */
@Repository
@RequiredArgsConstructor
public class ChatModelMetadataRepository implements IChatModelMetadataRepository {

    private final ChatModelInfoMapper chatModelInfoMapper;
    private final ChatModelSchemaMapper chatModelSchemaMapper;

    @Override
    public List<ChatModelInfo> listDistinctModels() {
        List<ChatModelInfo> modelList = chatModelInfoMapper.selectList(
                Wrappers.<ChatModelInfo>lambdaQuery()
                        .orderByDesc(ChatModelInfo::getId)
        );
        Map<String, ChatModelInfo> modelMap = new LinkedHashMap<>();
        for (ChatModelInfo modelInfo : modelList) {
            modelMap.putIfAbsent(modelInfo.getCode(), modelInfo);
        }
        return new ArrayList<>(modelMap.values());
    }

    @Override
    public void saveModelInfo(ChatModelInfo modelInfo) {
        chatModelInfoMapper.insert(modelInfo);
    }

    @Override
    public void deleteModelInfoByCode(String modelCode) {
        chatModelInfoMapper.deletePhysicalByCode(modelCode);
    }

    @Override
    public List<ChatModelSchema> listDistinctSchemas() {
        List<ChatModelSchema> schemaList = chatModelSchemaMapper.selectList(
                Wrappers.<ChatModelSchema>lambdaQuery()
                        .orderByDesc(ChatModelSchema::getId)
        );
        Map<String, ChatModelSchema> schemaMap = new LinkedHashMap<>();
        for (ChatModelSchema schema : schemaList) {
            String key = schema.getModelCode() + "::" + schema.getColumnId();
            schemaMap.putIfAbsent(key, schema);
        }
        return new ArrayList<>(schemaMap.values());
    }

    @Override
    public void saveModelSchemas(List<ChatModelSchema> schemaList) {
        if (CollectionUtils.isEmpty(schemaList)) {
            return;
        }
        // 单模型字段量可控，Phase 2A 先保持显式逐条写入，避免 domain 再暴露批量持久化框架细节。
        for (ChatModelSchema schema : schemaList) {
            chatModelSchemaMapper.insert(schema);
        }
    }

    @Override
    public void deleteModelSchemasByCode(String modelCode) {
        chatModelSchemaMapper.deletePhysicalByModelCode(modelCode);
    }
}

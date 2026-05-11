package org.wwz.ai.domain.agent.reactor.service;


import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import lombok.RequiredArgsConstructor;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentModelConfig;
import org.wwz.ai.domain.agent.reactor.data.TableColumn;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatSchemaDto;
import org.wwz.ai.domain.agent.reactor.adapter.repository.IChatModelMetadataRepository;
import org.wwz.ai.domain.agent.ledger.entity.ChatModelSchema;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatModelSchemaService {

    private final IChatModelMetadataRepository chatModelMetadataRepository;

    public static String getColumnUuids() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String uuid = UUID.randomUUID().toString();
            list.add(uuid);
        }
        return String.join(",", list);
    }

    private Map<String,String> parseModelColumnAlias(DataAgentModelConfig modelConfig){
        if(StringUtils.isBlank(modelConfig.getColumnAliasMap())){
            return new HashMap<>();
        }
        return JSONObject.parseObject(modelConfig.getColumnAliasMap(), new TypeReference<>() {
        });
    }

    public List<ChatModelSchema> saveModelSchema(String modelCode, DataAgentModelConfig modelConfig, List<TableColumn> columnList, Map<String, Set<String>> fewShotMap) {
        Set<String> ignoreFields = getIgnoreFields(modelConfig.getIgnoreFields());
        Set<String> defaultRecallFields = getDefaultRecallFields(modelConfig.getDefaultRecallFields());
        Set<String> analyzeSuggestFields = getAnalyzeSuggestFields(modelConfig.getAnalyzeSuggestFields());
        Set<String> analyzeForbidFields = getAnalyzeForbidFields(modelConfig.getAnalyzeForbidFields());
        Set<String> syncValueFields = getSyncValueFields(modelConfig.getSyncValueFields());

        Map<String, String> aliasMap = parseModelColumnAlias(modelConfig);

        List<ChatModelSchema> saveList = new ArrayList<>();
        for (TableColumn column : columnList) {
            String columnId = column.getName();
            if (ignoreFields.contains(columnId)) {
                continue;
            }
            ChatModelSchema schema = new ChatModelSchema();
            schema.setModelCode(modelCode);
            schema.setColumnId(columnId);
            schema.setColumnName(column.getComment());
            schema.setColumnComment(column.getComment());
            schema.setDataType(column.getDataType());
            schema.setVectorUuid(getColumnUuids());
            schema.setSynonyms(aliasMap.getOrDefault(columnId.toLowerCase(),aliasMap.get(columnId.toUpperCase())));
            if (defaultRecallFields.contains(columnId.toUpperCase()) || defaultRecallFields.contains(columnId.toLowerCase())) {
                schema.setDefaultRecall(1);
            }
            if (analyzeSuggestFields.contains(columnId.toUpperCase()) || analyzeSuggestFields.contains(columnId.toLowerCase())) {
                schema.setAnalyzeSuggest(1);
            }
            if (analyzeForbidFields.contains(columnId.toUpperCase()) || analyzeForbidFields.contains(columnId.toLowerCase())) {
                schema.setAnalyzeSuggest(-1);
            }
            Set<String> fewShotSet = fewShotMap.getOrDefault(columnId.toLowerCase(),fewShotMap.get(columnId.toUpperCase()));
            if (CollectionUtils.isEmpty(fewShotSet)) {
                fewShotSet = fewShotMap.get(column.getComment());
            }
            if (CollectionUtils.isNotEmpty(fewShotSet)) {
                schema.setFewShot(String.join(",", fewShotSet));
                if (fewShotSet.size() < 10) {
                    syncValueFields.remove(columnId.toLowerCase());
                    syncValueFields.remove(columnId.toUpperCase());
                }
            }
            saveList.add(schema);
        }
        modelConfig.setSyncValueFields(String.join(",", syncValueFields));
        chatModelMetadataRepository.saveModelSchemas(saveList);
        return saveList;
    }

    public List<ChatModelSchema> queryDefaultRecallFields() {
        return listDistinctSchemas().stream()
                .filter(schema -> schema.getDefaultRecall() == 1)
                .collect(Collectors.toList());
    }

    public List<ChatSchemaDto> queryAllSchemaDto() {
        List<ChatModelSchema> list = listDistinctSchemas();
        List<ChatSchemaDto> dtoList = new ArrayList<>();
        for (ChatModelSchema schema : list) {
            ChatSchemaDto dto = new ChatSchemaDto();
            BeanUtils.copyProperties(schema, dto);
            dtoList.add(dto);
        }
        return dtoList;
    }

    /**
     * 按模型编码清理历史 schema，避免应用每次启动重复初始化后越积越多。
     */
    public void cleanModelSchema(String modelCode) {
        chatModelMetadataRepository.deleteModelSchemasByCode(modelCode);
    }

    /**
     * 按 modelCode + columnId 去重，只保留最新一条有效 schema。
     */
    public List<ChatModelSchema> listDistinctSchemas() {
        return chatModelMetadataRepository.listDistinctSchemas();
    }

    public Set<String> getIgnoreFields(String ignoreFields) {
        if (StringUtils.isBlank(ignoreFields)) {
            return Collections.emptySet();
        }
        return Arrays.stream(ignoreFields.split(",")).collect(Collectors.toSet());
    }

    public Set<String> getDefaultRecallFields(String defaultRecallFields) {
        if (StringUtils.isBlank(defaultRecallFields)) {
            return Collections.emptySet();
        }
        return Arrays.stream(defaultRecallFields.split(",")).collect(Collectors.toSet());
    }

    public Set<String> getAnalyzeSuggestFields(String analyzeSuggestFields) {
        if (StringUtils.isBlank(analyzeSuggestFields)) {
            return Collections.emptySet();
        }
        return Arrays.stream(analyzeSuggestFields.split(",")).collect(Collectors.toSet());
    }

    public Set<String> getAnalyzeForbidFields(String analyzeForbidFields) {
        if (StringUtils.isBlank(analyzeForbidFields)) {
            return Collections.emptySet();
        }
        return Arrays.stream(analyzeForbidFields.split(",")).collect(Collectors.toSet());
    }

    public Set<String> getSyncValueFields(String syncValueFields) {
        if (StringUtils.isBlank(syncValueFields)) {
            return Collections.emptySet();
        }
        return Arrays.stream(syncValueFields.split(",")).collect(Collectors.toSet());
    }
}

package org.wwz.ai.domain.agent.rag.service;


import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.wwz.ai.domain.agent.rag.DataQueryException;
import org.wwz.ai.domain.agent.rag.model.config.DataQueryModelConfig;
import org.wwz.ai.domain.agent.rag.model.config.DataQuerySettings;
import org.wwz.ai.domain.agent.rag.model.query.SqlExecutionResult;
import org.wwz.ai.domain.agent.rag.model.schema.DataQuerySchema;
import org.wwz.ai.domain.agent.rag.model.schema.DataQueryModelDescriptor;
import org.wwz.ai.domain.agent.rag.model.schema.DataQueryTableColumn;
import org.wwz.ai.domain.agent.rag.model.schema.StandardColumnType;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSchemaPayload;
import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelInfo;
import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelSchema;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingRequest;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingResult;
import org.wwz.ai.domain.agent.rag.model.vector.VectorFilter;
import org.wwz.ai.domain.agent.rag.model.vector.VectorPoint;
import org.wwz.ai.domain.agent.rag.port.ColumnValueIndexAdminPort;
import org.wwz.ai.domain.agent.rag.port.ColumnValueSyncPort;
import org.wwz.ai.domain.agent.rag.port.DataQueryExecutionPort;
import org.wwz.ai.domain.agent.rag.port.DataQueryMetadataPort;
import org.wwz.ai.domain.agent.rag.port.IChatModelMetadataRepository;
import org.wwz.ai.domain.agent.rag.port.TextEmbeddingPort;
import org.wwz.ai.domain.agent.rag.port.VectorIndexAdminPort;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 问数模型元数据领域服务。
 * <p>
 * 负责模型配置初始化、表结构同步、schema 向量化和数据预览；持久化通过领域仓储端口完成，
 * 不在 domain 中直接依赖 Mapper 或 DAO。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatModelInfoService {

    private final IChatModelMetadataRepository chatModelMetadataRepository;
    private final DataQuerySettings dataQuerySettings;
    private final DataQueryMetadataPort dataQueryMetadataPort;
    private final DataQueryExecutionPort dataQueryExecutionPort;
    private final ChatModelSchemaService chatModelSchemaService;
    private final TextEmbeddingPort textEmbeddingPort;
    private final VectorIndexAdminPort vectorIndexAdminPort;
    private final ColumnValueIndexAdminPort columnValueIndexAdminPort;
    private final ColumnValueSyncPort columnValueSyncPort;

    public void initModelInfo(DataQuerySettings dataQuerySettings) throws Exception {
        // 初始化顺序先建立模型和 schema 元数据，再同步检索索引，保证向量数据有稳定的 modelCode/columnId。
        initModelInfo(dataQuerySettings, false);
    }

    public void refreshModelInfo(DataQuerySettings dataQuerySettings) throws Exception {
        initModelInfo(dataQuerySettings, true);
    }

    private void initModelInfo(DataQuerySettings dataQuerySettings, boolean forceRefresh) throws Exception {
        List<DataQueryModelConfig> tableList = dataQuerySettings.getModelList();
        if (CollectionUtils.isEmpty(tableList)) {
            log.warn("dataAgent.tableList is empty");
            return;
        }
        if (!forceRefresh && hasActiveModelMetadata(tableList)) {
            log.info("检测到问数模型元数据已存在，跳过本次初始化");
            return;
        }
        if (forceRefresh) {
            // 刷新模式先删除不再配置的模型，随后逐模型重建，避免历史 schema 继续参与召回。
            cleanStaleModelMetadata(tableList.stream().map(DataQueryModelConfig::getId).collect(Collectors.toSet()));
        }
        for (DataQueryModelConfig modelConfig : tableList) {
            List<DataQueryTableColumn> tableSchema = getModelSchema(modelConfig);
            Map<String, Set<String>> fewShotMap = queryModelFewShot(modelConfig, tableSchema);
            saveModelInfo(modelConfig, tableSchema, fewShotMap);
        }
    }

    /**
     * 检查当前是否已存在完整有效的模型元数据。
     */
    private boolean hasActiveModelMetadata(List<DataQueryModelConfig> tableList) {
        List<ChatModelInfo> modelList = listDistinctModels();
        if (CollectionUtils.isEmpty(modelList)) {
            return false;
        }
        Map<String, Long> schemaCountMap = chatModelSchemaService.listDistinctSchemas().stream()
                .collect(Collectors.groupingBy(ChatModelSchema::getModelCode, Collectors.counting()));
        Map<String, ChatModelInfo> modelMap = modelList.stream()
                .collect(Collectors.toMap(ChatModelInfo::getCode, model -> model, (left, right) -> left));
        for (DataQueryModelConfig modelConfig : tableList) {
            ChatModelInfo modelInfo = modelMap.get(modelConfig.getId());
            if (modelInfo == null) {
                return false;
            }
            if (schemaCountMap.getOrDefault(modelConfig.getId(), 0L) <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按模型编码去重，只保留最新一条有效模型定义。
     */
    public List<ChatModelInfo> listDistinctModels() {
        return chatModelMetadataRepository.listDistinctModels();
    }

    /**
     * 清理同一模型编码下的历史元数据，避免重复初始化。
     */
    public void cleanModelMetadata(String modelCode) {
        chatModelMetadataRepository.deleteModelInfoByCode(modelCode);
        chatModelSchemaService.cleanModelSchema(modelCode);
    }

    public void cleanStaleModelMetadata(Set<String> activeModelCodes) {
        List<ChatModelInfo> modelList = listDistinctModels();
        for (ChatModelInfo modelInfo : modelList) {
            if (!activeModelCodes.contains(modelInfo.getCode())) {
                cleanModelMetadata(modelInfo.getCode());
            }
        }
    }


    private Map<String, Set<String>> queryModelFewShot(DataQueryModelConfig modelConfig, List<DataQueryTableColumn> tableSchema) {
        String fewShotSql = getFewShotSql(modelConfig, tableSchema);
        SqlExecutionResult queryResult = dataQueryExecutionPort.query(fewShotSql);
        List<Map<String, Object>> dataList = queryResult.getDataList();
        if (CollectionUtils.isEmpty(dataList)) {
            return new HashMap<>();
        }
        Map<String, Set<String>> columnValueMap = new HashMap<>();
        for (Map<String, Object> data : dataList) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value == null || StringUtils.isBlank(value.toString())) {
                    continue;
                }
                // 将值转换为字符串
                String stringValue = value.toString();
                Set<String> values = columnValueMap.computeIfAbsent(key, k -> new HashSet<>());
                if (values.size() > 11) {
                    continue;
                }
                //截取枚举最大300字符
                stringValue = stringValue.substring(0, Math.min(300, stringValue.length()));
                values.add(stringValue);
            }
        }
        return columnValueMap;
    }

    private String getFewShotSql(DataQueryModelConfig modelConfig, List<DataQueryTableColumn> tableSchema) {
        if ("table".equalsIgnoreCase(modelConfig.getType())) {
            return "SELECT * FROM " + modelConfig.getContent() + " LIMIT 10000";
        } else if ("sql".equalsIgnoreCase(modelConfig.getType())) {
            return modelConfig.getContent() + " LIMIT 10000";
        } else {
            throw new DataQueryException("不支持的模型类型：" + modelConfig.getType());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ChatModelInfo saveModelInfo(DataQueryModelConfig modelConfig, List<DataQueryTableColumn> tableSchema, Map<String, Set<String>> fewShotMap) throws Exception {
        ChatModelInfo modelInfo = new ChatModelInfo();
        String modelCode = modelConfig.getId();
        // 事务覆盖模型/字段元数据；向量和 ES 同步紧随其后，失败时抛出异常让调用方感知初始化未完整结束。
        cleanModelMetadata(modelCode);
        modelInfo.setCode(modelCode);
        modelInfo.setName(modelConfig.getName());
        modelInfo.setContent(modelConfig.getContent());
        modelInfo.setType(modelConfig.getType());
        modelInfo.setUsePrompt(modelConfig.getRemark());
        modelInfo.setBusinessPrompt(modelConfig.getBusinessPrompt());
        chatModelMetadataRepository.saveModelInfo(modelInfo);
        log.info("model info save success:{}", modelCode);
        List<ChatModelSchema> chatModelSchemas = chatModelSchemaService.saveModelSchema(modelCode, modelConfig, tableSchema, fewShotMap);
        log.info("model schema save success {},size:{}", modelCode, chatModelSchemas.size());
        if (Boolean.TRUE.equals(dataQuerySettings.getQdrantConfig().getEnable())) {
            vectorIndexAdminPort.deleteByFilter(
                    DataQuerySettings.SCHEMA_COLLECTION_NAME,
                    new VectorFilter(Map.of("modelCode", modelCode))
            );
            log.info("model schema clean success:{}", modelCode);
            int vectorSize = syncVectorInfo(chatModelSchemas);
            log.info("model schema vector sync success {},vector size:{}", modelCode, vectorSize);
        }
        if (Boolean.TRUE.equals(dataQuerySettings.getEsConfig().getEnable())
                && columnValueIndexAdminPort.isAvailable()
                && StringUtils.isNotBlank(modelConfig.getSyncValueFields())) {
            String[] split = modelConfig.getSyncValueFields().toUpperCase().split(",");
            List<String> syncColumn = Arrays.asList(split);
            List<ChatModelSchema> syncValueList = chatModelSchemas.stream().filter(f -> syncColumn.contains(f.getColumnId().toUpperCase())).collect(Collectors.toList());
            columnValueSyncPort.syncColumnValues(modelInfo, syncValueList);
        }
        return modelInfo;
    }

    private int syncVectorInfo(List<ChatModelSchema> chatModelSchemas) throws Exception {
        List<VectorWriteData> vectorDataList = convertToVectorData(chatModelSchemas);
        int batchSize = 20;
        int total = 0;
        for (int i = 0; i < vectorDataList.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, vectorDataList.size());
            List<VectorWriteData> batch = vectorDataList.subList(i, endIndex);
            List<String> texts = batch.stream().map(VectorWriteData::text).toList();
            TextEmbeddingResult embedding = textEmbeddingPort.embed(new TextEmbeddingRequest(texts, true));
            if (embedding == null || !embedding.isAvailable()
                    || embedding.getVectors().size() != batch.size()) {
                throw new IllegalStateException("schema embedding result size mismatch");
            }
            List<VectorPoint> points = new ArrayList<>(batch.size());
            for (int index = 0; index < batch.size(); index++) {
                VectorWriteData item = batch.get(index);
                points.add(new VectorPoint(item.uuid(), embedding.getVectors().get(index), item.payload()));
            }
            vectorIndexAdminPort.upsert(DataQuerySettings.SCHEMA_COLLECTION_NAME, points);
            total += batch.size();
        }
        return total;
    }

    private List<VectorWriteData> convertToVectorData(List<ChatModelSchema> schemaList) {
        List<VectorWriteData> allVectors = new ArrayList<>();
        // 一列会拆成列名、同义词、注释和 few-shot 四类语义向量，numeric 列跳过 few-shot 以减少无效召回。
        for (ChatModelSchema schema : schemaList) {
            String[] uuids = schema.getVectorUuid().split(",");
            addVectorSaveData(allVectors, schema, schema.getColumnName(), uuids[0]);
            addVectorSaveData(allVectors, schema, schema.getSynonyms(), uuids[1]);
            addVectorSaveData(allVectors, schema, schema.getColumnComment(), uuids[2]);
            if (!StandardColumnType.DECIMAL.name().equalsIgnoreCase(schema.getDataType())) {
                //数值类型fewShot不参与向量化
                addVectorSaveData(allVectors, schema, schema.getFewShot(), uuids[3]);
            }
        }
        return allVectors;
    }


    private void addVectorSaveData(List<VectorWriteData> allVectors, ChatModelSchema schema, String vectorText, String uuid) {
        if (StringUtils.isBlank(vectorText)) {
            return;
        }
        VectorSchemaPayload newSchema = new VectorSchemaPayload();
        BeanUtils.copyProperties(schema, newSchema);
        Map<String, Object> payload = JSONObject.parseObject(
                JSONObject.toJSONString(newSchema), new TypeReference<>() { });
        payload.values().removeIf(Objects::isNull);
        allVectors.add(new VectorWriteData(vectorText, uuid, payload));
    }

    private record VectorWriteData(String text, String uuid, Map<String, Object> payload) {
        private VectorWriteData {
            payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }


    public List<DataQueryTableColumn> getModelSchema(DataQueryModelConfig modelConfig) {
        if ("table".equalsIgnoreCase(modelConfig.getType())) {
            return getTableSchema(modelConfig);
        } else if ("sql".equalsIgnoreCase(modelConfig.getType())) {
            return getSqlSchema(modelConfig);
        } else {
            throw new DataQueryException("不支持的模型类型：" + modelConfig.getType());
        }
    }

    public List<DataQueryTableColumn> getSqlSchema(DataQueryModelConfig modelConfig) {
        return dataQueryMetadataPort.getTableColumnsOfSql(modelConfig.getContent(), 1);
    }

    public List<DataQueryTableColumn> getTableSchema(DataQueryModelConfig modelConfig) {
        return dataQueryMetadataPort.queryColumns(modelConfig.getContent(), dataQuerySettings.getDbConfig().getSchema());
    }

    public List<DataQueryModelDescriptor> queryAllModelsWithSchema() {
        List<ChatModelInfo> modelList = listDistinctModels();
        List<ChatModelSchema> schemaList = chatModelSchemaService.listDistinctSchemas();
        List<DataQuerySchema> schemaDtoList = new ArrayList<>();
        for (ChatModelSchema schema : schemaList) {
            DataQuerySchema dto = new DataQuerySchema();
            BeanUtils.copyProperties(schema, dto);
            schemaDtoList.add(dto);
        }
        Map<String, List<DataQuerySchema>> schemaMap = schemaDtoList.stream().collect(Collectors.groupingBy(DataQuerySchema::getModelCode));
        List<DataQueryModelDescriptor> dtoList = new ArrayList<>();
        for (ChatModelInfo modelInfo : modelList) {
            DataQueryModelDescriptor dto = new DataQueryModelDescriptor();
            dto.setModelCode(modelInfo.getCode());
            dto.setModelName(modelInfo.getName());
            dto.setBusinessPrompt(modelInfo.getBusinessPrompt());
            dto.setUsePrompt(modelInfo.getUsePrompt());
            dto.setType(modelInfo.getType());
            dto.setContent(modelInfo.getContent());
            dto.setSchemaList(schemaMap.get(modelInfo.getCode()));
            dtoList.add(dto);
        }
        return dtoList;
    }

    public SqlExecutionResult previewData(String modelCode) {
        ChatModelInfo modelInfo = listDistinctModels().stream()
                .filter(item -> StringUtils.equals(item.getCode(), modelCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("模型不存在:" + modelCode));
        String sql = "";
        if ("table".equalsIgnoreCase(modelInfo.getType())) {
            sql += "SELECT * FROM " + modelInfo.getContent();
        } else if ("sql".equalsIgnoreCase(modelInfo.getType())) {
            sql += modelInfo.getContent();
        }
        sql += " LIMIT 100";
        return dataQueryExecutionPort.query(sql);
    }

}

package org.wwz.ai.domain.agent.reactor.service;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.DataQueryExecutionPort;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConstants;
import org.wwz.ai.domain.agent.reactor.config.data.DbConfig;
import org.wwz.ai.domain.agent.reactor.data.QueryResult;
import org.wwz.ai.domain.agent.ledger.entity.ChatModelInfo;
import org.wwz.ai.domain.agent.ledger.entity.ChatModelSchema;
import org.wwz.ai.domain.agent.reactor.util.ESUtil;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ColumnValueSyncService {

    @Autowired(required = false)
    RestHighLevelClient dataAgentEsClient;
    @Autowired
    DataAgentConfig dataAgentConfig;
    @Autowired
    DataQueryExecutionPort dataQueryExecutionPort;

    public void initColumnValueIndex() {
        requireEsClient();
        if (ESUtil.isExistsIndex(dataAgentEsClient, DataAgentConstants.COLUMN_VALUE_ES_INDEX)) {
            log.info("es index 已存在，无需创建");
            return;
        }
        log.info("es index 不存在，开始创建");
        boolean created = ESUtil.createIndex(dataAgentEsClient, DataAgentConstants.COLUMN_VALUE_ES_INDEX, DataAgentConstants.COLUMN_VALUE_ES_MAPPING);
        if (!created) {
            throw new IllegalStateException("创建 ES 列值索引失败: " + DataAgentConstants.COLUMN_VALUE_ES_INDEX);
        }
    }

    public void recreateColumnValueIndex() {
        requireEsClient();
        if (ESUtil.isExistsIndex(dataAgentEsClient, DataAgentConstants.COLUMN_VALUE_ES_INDEX)) {
            boolean deleted = ESUtil.deleteIndex(dataAgentEsClient, DataAgentConstants.COLUMN_VALUE_ES_INDEX);
            if (!deleted) {
                throw new IllegalStateException("删除 ES 列值索引失败: " + DataAgentConstants.COLUMN_VALUE_ES_INDEX);
            }
        }
        boolean created = ESUtil.createIndex(dataAgentEsClient, DataAgentConstants.COLUMN_VALUE_ES_INDEX, DataAgentConstants.COLUMN_VALUE_ES_MAPPING);
        if (!created) {
            throw new IllegalStateException("重建 ES 列值索引失败: " + DataAgentConstants.COLUMN_VALUE_ES_INDEX);
        }
    }

    public String getTableName(ChatModelInfo modelInfo) {
        if ("table".equalsIgnoreCase(modelInfo.getType())) {
            return modelInfo.getContent();
        } else if ("sql".equalsIgnoreCase(modelInfo.getType())) {
            return "(" + modelInfo.getContent() + ") t";
        } else {
            throw new RuntimeException("不支持的模型类型" + modelInfo.getType());
        }
    }

    public void syncColumnValueBatch(ChatModelInfo modelInfo, List<ChatModelSchema> chatModelSchemas) throws SQLException {
        if (CollectionUtils.isEmpty(chatModelSchemas)) {
            log.info("没有需要同步的字段值");
            return;
        }
        for (ChatModelSchema chatModelSchema : chatModelSchemas) {
            syncColumnValue(modelInfo, chatModelSchema);
        }
    }


    public void syncColumnValue(ChatModelInfo modelInfo, ChatModelSchema column) throws SQLException {
        if (dataAgentEsClient == null) {
            log.warn("ES 客户端不可用，跳过字段值同步：{}", column.getColumnId());
            return;
        }
        String tableName = getTableName(modelInfo);
        String valueSql = String.format("select %s as `value` FROM %s WHERE %s IS NOT NULL GROUP BY %s Limit  10000", column.getColumnId(), tableName, column.getColumnId(), column.getColumnId());
        DbConfig dbConfig = dataAgentConfig.getDbConfig();
        QueryResult queryResult = dataQueryExecutionPort.query(dbConfig, valueSql);
        List<Map<String, Object>> dataList = queryResult.getDataList();
        List<Map<String, Object>> syncList = new ArrayList<>(dataList.size());
        try {
            log.info("开始同步字段值信息：{}", column.getColumnId());
            LocalDateTime now = LocalDateTime.now();
            String dateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(now);
            for (Map<String, Object> map : dataList) {
                Object o = map.getOrDefault("value", map.get("VALUE"));
                if (o == null) {
                    continue;
                }
                String valueStr = String.valueOf(o);
                if (StringUtils.isBlank(valueStr)) {
                    continue;
                }
                Map<String, Object> saveMap = new HashMap<>();
                saveMap.put("value", valueStr);
                saveMap.put("modelCode", modelInfo.getCode());
                saveMap.put("columnId", column.getColumnId());
                saveMap.put("createTime", dateStr);
                saveMap.put("valueId", String.format("%s%s%s", modelInfo.getCode(), column.getColumnId(), o));
                saveMap.put("columnName", column.getColumnName());
                saveMap.put("columnComment", column.getColumnComment());
                saveMap.put("dataType", column.getDataType());
                saveMap.put("synonyms", column.getSynonyms());
                syncList.add(saveMap);
            }
            ESUtil.bulkInsert(dataAgentEsClient, DataAgentConstants.COLUMN_VALUE_ES_INDEX, syncList, "valueId");
            log.info("字段值信息写入ES完成：{},size:{}", column.getColumnId(), syncList.size());
        } catch (Exception ex) {
            log.error("同步字段值信息失败：{}", ex.getMessage(), ex);
        }
    }

    private void requireEsClient() {
        if (dataAgentEsClient == null) {
            throw new IllegalStateException("ES 客户端未启用或未完成装配");
        }
    }

}

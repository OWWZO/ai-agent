package org.wwz.ai.infrastructure.dataquery.elasticsearch;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelInfo;
import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelSchema;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueDocument;
import org.wwz.ai.domain.agent.rag.port.DataQueryExecutionPort;
import org.wwz.ai.domain.agent.rag.port.ColumnValueIndexAdminPort;
import org.wwz.ai.domain.agent.rag.port.ColumnValueSyncPort;
import org.wwz.ai.domain.agent.rag.model.query.SqlExecutionResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Infrastructure implementation for querying distinct column values and writing ES documents.
 */
@Component
@RequiredArgsConstructor
public class ElasticsearchColumnValueSyncAdapter implements ColumnValueSyncPort {

    private final DataQueryExecutionPort dataQueryExecutionPort;
    private final ColumnValueIndexAdminPort columnValueIndexAdminPort;

    @Override
    public void syncColumnValues(ChatModelInfo modelInfo, List<ChatModelSchema> schemas) {
        if (modelInfo == null || schemas == null || schemas.isEmpty()) {
            return;
        }
        for (ChatModelSchema schema : schemas) {
            syncColumnValue(modelInfo, schema);
        }
    }

    private void syncColumnValue(ChatModelInfo modelInfo, ChatModelSchema column) {
        String tableName = getTableName(modelInfo);
        String valueSql = String.format(
                "select %s as `value` FROM %s WHERE %s IS NOT NULL GROUP BY %s Limit  10000",
                column.getColumnId(), tableName, column.getColumnId(), column.getColumnId());
        try {
            SqlExecutionResult queryResult = dataQueryExecutionPort.query(valueSql);
            List<Map<String, Object>> dataList = queryResult == null || queryResult.getDataList() == null
                    ? List.of() : queryResult.getDataList();
            List<ColumnValueDocument> documents = new ArrayList<>(dataList.size());
            String dateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now());
            for (Map<String, Object> map : dataList) {
                Object value = map.getOrDefault("value", map.get("VALUE"));
                if (value == null || String.valueOf(value).isBlank()) {
                    continue;
                }
                String valueStr = String.valueOf(value);
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("value", valueStr);
                fields.put("modelCode", modelInfo.getCode());
                fields.put("columnId", column.getColumnId());
                fields.put("createTime", dateStr);
                fields.put("valueId", String.format("%s%s%s", modelInfo.getCode(), column.getColumnId(), value));
                putIfPresent(fields, "columnName", column.getColumnName());
                putIfPresent(fields, "columnComment", column.getColumnComment());
                putIfPresent(fields, "dataType", column.getDataType());
                putIfPresent(fields, "synonyms", column.getSynonyms());
                documents.add(new ColumnValueDocument(String.valueOf(fields.get("valueId")), fields));
            }
            columnValueIndexAdminPort.bulkInsert(ElasticsearchDataQueryDefaults.COLUMN_VALUE_INDEX_NAME, documents);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to synchronize column values", e);
        }
    }

    private String getTableName(ChatModelInfo modelInfo) {
        if ("table".equalsIgnoreCase(modelInfo.getType())) {
            return modelInfo.getContent();
        }
        if ("sql".equalsIgnoreCase(modelInfo.getType())) {
            return "(" + modelInfo.getContent() + ") t";
        }
        throw new IllegalArgumentException("Unsupported model type: " + modelInfo.getType());
    }

    private void putIfPresent(Map<String, Object> fields, String name, Object value) {
        if (value != null) {
            fields.put(name, value);
        }
    }
}

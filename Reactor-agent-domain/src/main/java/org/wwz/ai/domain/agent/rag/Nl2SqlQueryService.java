package org.wwz.ai.domain.agent.rag;

import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.SqlKind;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamListener;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamRequest;
import org.wwz.ai.domain.agent.rag.model.query.DataQueryColumn;
import org.wwz.ai.domain.agent.rag.model.query.DataQueryFilter;
import org.wwz.ai.domain.agent.rag.model.query.DataQueryResult;
import org.wwz.ai.domain.agent.rag.model.query.DataQueryStreamEvent;
import org.wwz.ai.domain.agent.rag.model.query.Nl2SqlQuery;
import org.wwz.ai.domain.agent.rag.model.query.Nl2SqlResult;
import org.wwz.ai.domain.agent.rag.model.query.SqlExecutionResult;
import org.wwz.ai.domain.agent.rag.model.config.DataQuerySettings;
import org.wwz.ai.domain.agent.rag.model.schema.DataQueryModelDescriptor;
import org.wwz.ai.domain.agent.rag.model.schema.DataQuerySchema;
import org.wwz.ai.domain.agent.rag.model.schema.StandardColumnType;
import org.wwz.ai.domain.agent.rag.model.sql.ComparisonType;
import org.wwz.ai.domain.agent.rag.model.sql.DataOrderBy;
import org.wwz.ai.domain.agent.rag.model.sql.ModelColumn;
import org.wwz.ai.domain.agent.rag.model.sql.SqlModel;
import org.wwz.ai.domain.agent.rag.model.sql.WhereCondition;
import org.wwz.ai.domain.agent.rag.port.DataQueryExecutionPort;
import org.wwz.ai.domain.agent.rag.sql.SqlParserUtils;
import org.wwz.ai.domain.agent.reactor.model.enums.EventTypeEnum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** NL2SQL parsing, query validation, execution, and result projection. */
@Slf4j
@Service
@RequiredArgsConstructor
public class Nl2SqlQueryService {

    public static final String NL2SQL_URL = "/v1/tool/nl2sql";

    private final DataQuerySettings settings;
    private final DataQueryExecutionPort dataQueryExecutionPort;
    private final RemoteHttpPort remoteHttpPort;
    private final RemoteStreamPort remoteStreamPort;

    public List<DataQueryResult> runNL2SQLSync(Nl2SqlQuery request) throws Exception {
        request.setStream(false);
        String jsonResult = remoteHttpPort.execute(RemoteHttpRequest.builder()
                .method("POST")
                .url(settings.getAgentUrl() + NL2SQL_URL)
                .headers(Map.of("Content-Type", "application/json"))
                .body(JSONObject.toJSONString(request))
                .build());
        log.info("{},{} nl2sql result without sse:{}", request.getTraceId(), request.getRequestId(), jsonResult);
        Nl2SqlResult result = JSONObject.parseObject(jsonResult, Nl2SqlResult.class);
        return nl2sqlQueryData(request, result);
    }

    public List<DataQueryResult> runNL2SQLSse(Nl2SqlQuery request, AgentMessageStream stream) throws Exception {
        Nl2SqlSseListener listener = new Nl2SqlSseListener(stream, request.getRequestId(), request.getTraceId());
        remoteStreamPort.openStream(RemoteStreamRequest.builder()
                .method("POST")
                .url(settings.getAgentUrl() + NL2SQL_URL)
                .headers(Map.of("Accept", "text/event-stream", "Content-Type", "application/json"))
                .body(JSONObject.toJSONString(request))
                .build(), listener);
        listener.getCountDownLatch().await();
        log.info("{} sse event count:{}", request.getRequestId(), listener.getEventCount());
        if (!listener.isSuccess()) {
            throw new RuntimeException("sse listener failed " + listener.getErrorMessage());
        }
        return nl2sqlQueryData(request, listener.getNl2SQLResult());
    }

    public String replaceFirstMatchedOrThrow(String input, List<String> codeList) {
        if (input == null || CollectionUtils.isEmpty(codeList)) {
            throw new IllegalArgumentException("模型编码列表为空，无法替换 nl2sql 结果中的模型占位符");
        }
        List<Pattern> patterns = codeList.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(code -> Pattern.compile("(?i)(?<!`)\\b" + Pattern.quote(code) + "\\b(?!`)"))
                .toList();
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(input);
            if (matcher.find()) {
                return matcher.replaceFirst("`$0`");
            }
        }
        return input;
    }

    private List<DataQueryResult> nl2sqlQueryData(Nl2SqlQuery request, Nl2SqlResult result) throws Exception {
        if (result == null || result.getCode() == null) {
            throw new RuntimeException("nl2sql result is null");
        }
        if (result.getCode() != 200) {
            throw new RuntimeException("nl2sql server return error:" + result.getErrorMessage());
        }
        if (CollectionUtils.isEmpty(result.getData())) {
            throw new RuntimeException("nl2sql返回为空");
        }

        result.setRootQuery(request.getQuery());
        for (Nl2SqlResult.GeneratedQuery generatedQuery : result.getData()) {
            generatedQuery.setNl2sql(replaceFirstMatchedOrThrow(generatedQuery.getNl2sql(), request.getModelCodeList()));
        }
        return queryData(request, result);
    }

    public String getTableName(DataQueryModelDescriptor model) {
        if ("table".equalsIgnoreCase(model.getType())) {
            return model.getContent();
        }
        if ("sql".equalsIgnoreCase(model.getType())) {
            return "(" + model.getContent() + ") t";
        }
        throw new RuntimeException("不支持的模型类型" + model.getType());
    }

    public List<DataQueryResult> queryData(Nl2SqlQuery request, Nl2SqlResult result) throws Exception {
        Map<String, DataQueryModelDescriptor> modelsByCode = request.getSchemaInfo().stream()
                .collect(Collectors.toMap(DataQueryModelDescriptor::getModelCode, value -> value));
        List<DataQueryResult> dataList = new ArrayList<>();

        for (Nl2SqlResult.GeneratedQuery generatedQuery : result.getData()) {
            SqlModel sqlModel = SqlParserUtils.parseSelectSql(generatedQuery.getNl2sql(), request.getDbType());
            String modelCode = sqlModel.getFromTable().getTableName();
            DataQueryModelDescriptor model = modelsByCode.get(modelCode);
            if (model == null) {
                throw new RuntimeException("modelCode:" + modelCode + "不存在");
            }

            Map<String, DataQuerySchema> columnsById = model.getSchemaList().stream()
                    .collect(Collectors.toMap(DataQuerySchema::getColumnId, value -> value));
            List<DataQueryColumn> columns = parseColumns(sqlModel, columnsById);
            List<DataQueryFilter> filters = parseFilters(sqlModel, columnsById);

            String realSql = generatedQuery.getNl2sql();
            String tableName = getTableName(model);
            for (String code : modelsByCode.keySet()) {
                realSql = realSql.replaceAll(code + "|`" + code + "`", tableName);
            }

            log.info("{},{} 执行sql:{}", request.getTraceId(), request.getRequestId(), realSql);
            SqlExecutionResult queryResult = dataQueryExecutionPort.query(realSql);
            log.info("{},{} 查询sql结果大小：{}", request.getTraceId(), request.getRequestId(), queryResult.getDataSize());

            DataQueryResult queryData = new DataQueryResult();
            queryData.setColumnList(columns);
            queryData.setFilters(filters);
            queryData.setQuestion(generatedQuery.getQuery());
            queryData.setNl2sqlResult(realSql);
            queryData.setDataList(queryResult.getDataList());
            dataList.add(queryData);
        }
        parseChartConfig(dataList);
        return dataList;
    }

    private List<DataQueryColumn> parseColumns(SqlModel sqlModel, Map<String, DataQuerySchema> columnMap) {
        List<DataQueryColumn> columns = new ArrayList<>();
        if (sqlModel.getColumnList().size() == 1 && sqlModel.getColumnList().get(0).isStar()) {
            return parseStarColumn(columnMap);
        }
        List<DataOrderBy> orderByList = sqlModel.getOrderByList();
        if (orderByList == null) {
            orderByList = new ArrayList<>();
        }

        for (ModelColumn column : sqlModel.getColumnList()) {
            DataQueryColumn result = new DataQueryColumn();
            result.setCol(column.getColumnName());
            if (StringUtils.isBlank(column.getColumnAlias())) {
                result.setGuid(StringUtils.lowerCase(column.getColumnName()));
            } else {
                result.setGuid(StringUtils.lowerCase(column.getColumnAlias()));
                result.setName(column.getColumnAlias());
            }
            result.setColType(column.getColumnKind());
            if (SqlKind.IDENTIFIER.name().equalsIgnoreCase(column.getColumnKind())) {
                DataQuerySchema schema = columnMap.get(column.getColumnName());
                if (schema != null) {
                    result.setName(schema.getColumnName());
                    result.setDataType(schema.getDataType());
                }
            } else {
                if (column.isAggregator()) {
                    result.setAgg(column.getFunctionName());
                    result.setDataType(StandardColumnType.DECIMAL.name());
                }
                if (CollectionUtils.isNotEmpty(column.getFunctionArgList())) {
                    String argument = column.getFunctionArgList().get(0);
                    DataQuerySchema schema = columnMap.getOrDefault(StringUtils.lowerCase(argument),
                            columnMap.get(StringUtils.upperCase(argument)));
                    if (schema != null) {
                        if (StringUtils.isBlank(result.getName())) {
                            result.setName(schema.getColumnName());
                        }
                        result.setDataType(schema.getDataType());
                    }
                }
            }
            if (StringUtils.isBlank(result.getDataType()) && isNumberKind(column.getColumnKind())) {
                result.setDataType(StandardColumnType.DECIMAL.name());
            }
            if (StringUtils.isBlank(result.getName())) {
                result.setName(result.getGuid());
            }
            orderByList.stream()
                    .filter(order -> StringUtils.equalsIgnoreCase(order.getColumnName(), result.getGuid())
                            || StringUtils.equalsIgnoreCase(order.getColumnName(), result.getName()))
                    .findAny()
                    .ifPresent(order -> result.setOrder(order.getOrderType().name()));
            columns.add(result);
        }
        return columns;
    }

    private boolean isNumberKind(String kindName) {
        try {
            return SqlKind.BINARY_ARITHMETIC.contains(SqlKind.valueOf(kindName));
        } catch (Exception e) {
            return false;
        }
    }

    private List<DataQueryColumn> parseStarColumn(Map<String, DataQuerySchema> columnMap) {
        List<DataQueryColumn> columns = new ArrayList<>();
        for (Map.Entry<String, DataQuerySchema> entry : columnMap.entrySet()) {
            DataQueryColumn column = new DataQueryColumn();
            String columnId = StringUtils.lowerCase(entry.getKey());
            DataQuerySchema schema = entry.getValue();
            column.setCol(columnId);
            column.setGuid(columnId);
            column.setColType(schema.getDataType());
            column.setName(schema.getColumnName());
            columns.add(column);
        }
        return columns;
    }

    private List<DataQueryFilter> parseFilters(SqlModel sqlModel, Map<String, DataQuerySchema> columnMap) {
        List<DataQueryFilter> filters = new ArrayList<>();
        List<WhereCondition> conditions = sqlModel.getWhereConditionList();
        if (CollectionUtils.isNotEmpty(conditions)) {
            for (WhereCondition condition : conditions) {
                if (SqlParserUtils.OR.equalsIgnoreCase(condition.getOperator())) {
                    DataQueryFilter filter = new DataQueryFilter();
                    filter.setSubFilters(new ArrayList<>());
                    filter.setOperator(SqlParserUtils.OR);
                    for (WhereCondition subCondition : condition.getConditionList()) {
                        filter.getSubFilters().add(parseOneFilter(subCondition, columnMap));
                    }
                    filters.add(filter);
                } else {
                    filters.add(parseOneFilter(condition, columnMap));
                }
            }
        }
        return filters;
    }

    private DataQueryFilter parseOneFilter(WhereCondition condition, Map<String, DataQuerySchema> columnMap) {
        DataQueryFilter filter = new DataQueryFilter();
        filter.setCol(condition.getIdentifier());
        filter.setOpt(condition.getComparisonType());
        filter.setOptName(ComparisonType.of(condition.getComparisonType()).getComparisonName());
        filter.setVal(CollectionUtils.isEmpty(condition.getValueList())
                ? condition.getValue() : String.join(",", condition.getValueList()));
        DataQuerySchema schema = columnMap.getOrDefault(StringUtils.lowerCase(filter.getCol()),
                columnMap.get(StringUtils.upperCase(filter.getCol())));
        filter.setName(schema == null ? filter.getCol() : schema.getColumnName());
        return filter;
    }

    public void parseChartConfig(List<DataQueryResult> dataList) {
        for (DataQueryResult data : dataList) {
            List<Map<String, Object>> rows = data.getDataList();
            if (CollectionUtils.isNotEmpty(rows)) {
                data.setDataList(rows.stream().map(this::convertKeysToLowerCase).collect(Collectors.toList()));
            }
            if (CollectionUtils.isEmpty(data.getColumnList())) {
                continue;
            }
            Map<Boolean, List<String>> partitioned = data.getColumnList().stream()
                    .collect(Collectors.partitioningBy(
                            column -> StringUtils.isNotBlank(column.getAgg())
                                    || StandardColumnType.DECIMAL.name().equalsIgnoreCase(column.getDataType()),
                            Collectors.mapping(DataQueryColumn::getGuid, Collectors.toList())));
            data.setDimCols(partitioned.get(false));
            data.setMeasureCols(partitioned.get(true));
        }
    }

    private Map<String, Object> convertKeysToLowerCase(Map<String, Object> originalMap) {
        if (originalMap == null) {
            return null;
        }
        Map<String, Object> lowerCaseMap = new HashMap<>();
        originalMap.forEach((key, value) -> lowerCaseMap.put(key == null ? null : key.toLowerCase(), value));
        return lowerCaseMap;
    }

    public static class Nl2SqlSseListener implements RemoteStreamListener {
        public static final String STATUS_THINK = "nl2sql_think";
        public static final String STATUS_DATA = "data";
        public static final String STATUS_STREAM_FINISHED = "finished_stream";

        @Getter
        private Nl2SqlResult nl2SQLResult;
        @Getter
        private int eventCount;
        @Getter
        private final CountDownLatch countDownLatch = new CountDownLatch(1);
        @Getter
        private boolean success = true;
        @Getter
        private String errorMessage;
        @Getter
        private final String requestId;
        @Getter
        private final String traceId;

        private final AgentMessageStream stream;

        public Nl2SqlSseListener(AgentMessageStream stream, String requestId, String traceId) {
            this.stream = stream;
            this.requestId = requestId;
            this.traceId = traceId;
        }

        @Override
        public void onOpen() {
            log.info("SSE nl2sql连接建立");
        }

        @Override
        public void onLine(String data) {
            try {
                if (data.startsWith("data:")) {
                    data = data.substring(5).trim();
                }
                log.debug("{},{} SSE nl2sql消息:{}", traceId, requestId, data);
                eventCount++;
                if ("[DONE]".equalsIgnoreCase(data) || "heartbeat".equalsIgnoreCase(data)
                        || StringUtils.isBlank(data)) {
                    return;
                }

                Nl2SqlResult eventResult = parseEventResult(data);
                if (eventResult == null) {
                    return;
                }
                if (STATUS_THINK.equalsIgnoreCase(eventResult.getStatus())) {
                    stream.send(new DataQueryStreamEvent(EventTypeEnum.THINK.name(), eventResult.getNl2sqlThink()));
                }
                if (STATUS_STREAM_FINISHED.equalsIgnoreCase(eventResult.getStatus())) {
                    stream.send(new DataQueryStreamEvent(STATUS_STREAM_FINISHED, STATUS_STREAM_FINISHED));
                }
                if (STATUS_DATA.equalsIgnoreCase(eventResult.getStatus())) {
                    log.info("{},{} SSE数据结果：{}", traceId, requestId, data);
                    nl2SQLResult = eventResult;
                }
            } catch (Exception e) {
                log.error("{},{} nl2sql消息解析错误:{}", traceId, requestId, e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onClosed() {
            log.info("{},{} SSE 连接关闭", traceId, requestId);
            countDownLatch.countDown();
        }

        @Override
        public void onFailure(Throwable throwable, Integer statusCode, String responseBody) {
            errorMessage = " nl2sql listener failed" + traceId + "," + requestId;
            success = false;
            if (throwable != null) {
                errorMessage += throwable.getMessage();
            }
            if (statusCode != null) {
                errorMessage += ", statusCode=" + statusCode;
            }
            log.error(errorMessage, throwable);
            countDownLatch.countDown();
        }

        private Nl2SqlResult parseEventResult(String data) {
            try {
                return JSONObject.parseObject(data, Nl2SqlResult.class);
            } catch (Exception e) {
                log.error("{},{} nl2sql 解析失败 {}", traceId, requestId, e.getMessage(), e);
                return null;
            }
        }
    }
}

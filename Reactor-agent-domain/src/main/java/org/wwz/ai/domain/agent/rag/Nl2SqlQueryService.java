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
import org.wwz.ai.domain.agent.adapter.port.DataQueryExecutionPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamListener;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamRequest;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.config.data.DbConfig;
import org.wwz.ai.domain.agent.reactor.data.QueryResult;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatModelInfoDto;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatQueryColumn;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatQueryData;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatQueryFilter;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatSchemaDto;
import org.wwz.ai.domain.agent.reactor.data.dto.NL2SQLReq;
import org.wwz.ai.domain.agent.reactor.data.dto.NL2SQLResult;
import org.wwz.ai.domain.agent.reactor.data.model.ComparisonType;
import org.wwz.ai.domain.agent.reactor.data.model.DataOrderBy;
import org.wwz.ai.domain.agent.reactor.data.model.ModelColumn;
import org.wwz.ai.domain.agent.reactor.data.model.SqlModel;
import org.wwz.ai.domain.agent.reactor.data.model.StandardColumnType;
import org.wwz.ai.domain.agent.reactor.data.model.WhereCondition;
import org.wwz.ai.domain.agent.reactor.data.sql.SqlParserUtils;
import org.wwz.ai.domain.agent.reactor.model.response.ChatDataMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * NL2SQL 稳定查询服务。
 * 负责自然语言转 SQL、远端流式监听与数据库查询结果整形。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Nl2SqlQueryService {

    public static final String NL2SQL_URL = "/v1/tool/nl2sql";

    private final DataAgentConfig dataAgentConfig;
    private final DataQueryExecutionPort dataQueryExecutionPort;
    private final RemoteHttpPort remoteHttpPort;
    private final RemoteStreamPort remoteStreamPort;

    public List<ChatQueryData> runNL2SQLSync(NL2SQLReq request) throws Exception {
        AtomicReference<Throwable> err = new AtomicReference<>();
        request.setStream(false);
        String jsonResult = remoteHttpPort.execute(RemoteHttpRequest.builder()
                .method("POST")
                .url(dataAgentConfig.getAgentUrl() + NL2SQL_URL)
                .headers(Map.of("Content-Type", "application/json"))
                .body(JSONObject.toJSONString(request))
                .build());
        log.info("{},{} nl2sql result without sse:{}", request.getTraceId(), request.getRequestId(), jsonResult);
        NL2SQLResult nl2SQLResult = JSONObject.parseObject(jsonResult, NL2SQLResult.class);
        if (err.get() != null) {
            throw new RuntimeException("sse nl2sql failed:" + err.get().getMessage());
        }
        return nl2sqlQueryData(request, nl2SQLResult);
    }

    public List<ChatQueryData> runNL2SQLSse(NL2SQLReq request, AgentMessageStream stream) throws Exception {
        AtomicReference<Throwable> err = new AtomicReference<>();
        Nl2SqlSseListener sqlSseListener = new Nl2SqlSseListener(stream, request.getRequestId(), request.getTraceId());
        remoteStreamPort.openStream(RemoteStreamRequest.builder()
                .method("POST")
                .url(dataAgentConfig.getAgentUrl() + NL2SQL_URL)
                .headers(Map.of(
                        "Accept", "text/event-stream",
                        "Content-Type", "application/json"
                ))
                .body(JSONObject.toJSONString(request))
                .build(), sqlSseListener);
        sqlSseListener.getCountDownLatch().await();
        log.info("{} sse event count:{}", request.getRequestId(), sqlSseListener.getEventCount());
        if (!sqlSseListener.isSuccess()) {
            throw new RuntimeException("sse listener failed " + sqlSseListener.getErrorMessage());
        }
        NL2SQLResult nl2SQLResult = sqlSseListener.getNl2SQLResult();
        if (err.get() != null) {
            throw new RuntimeException("sse nl2sql failed:" + err.get().getMessage());
        }
        return nl2sqlQueryData(request, nl2SQLResult);
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

    private List<ChatQueryData> nl2sqlQueryData(NL2SQLReq request, NL2SQLResult nl2SQLResult) throws Exception {
        if (nl2SQLResult == null || nl2SQLResult.getCode() == null) {
            throw new RuntimeException("nl2sql result is null");
        }
        if (nl2SQLResult.getCode() != 200) {
            throw new RuntimeException("nl2sql server return error:" + nl2SQLResult.getErr_msg());
        }
        if (CollectionUtils.isEmpty(nl2SQLResult.getData())) {
            throw new RuntimeException("nl2sql返回为空");
        }

        nl2SQLResult.setRootQuery(request.getQuery());
        for (NL2SQLResult.NL2SQLData nl2SQLData : nl2SQLResult.getData()) {
            String prettySql = replaceFirstMatchedOrThrow(nl2SQLData.getNl2sql(), request.getModelCodeList());
            nl2SQLData.setNl2sql(prettySql);
        }
        return queryData(request, nl2SQLResult);
    }

    public String getTableName(ChatModelInfoDto modelInfo) {
        if ("table".equalsIgnoreCase(modelInfo.getType())) {
            return modelInfo.getContent();
        }
        if ("sql".equalsIgnoreCase(modelInfo.getType())) {
            return "(" + modelInfo.getContent() + ") t";
        }
        throw new RuntimeException("不支持的模型类型" + modelInfo.getType());
    }

    public List<ChatQueryData> queryData(NL2SQLReq request, NL2SQLResult nl2SQLResult) throws Exception {
        List<NL2SQLResult.NL2SQLData> data = nl2SQLResult.getData();
        List<ChatQueryData> dataList = new ArrayList<>();

        List<ChatModelInfoDto> schemaInfo = request.getSchemaInfo();
        Map<String, ChatModelInfoDto> modelMap = schemaInfo.stream()
                .collect(Collectors.toMap(ChatModelInfoDto::getModelCode, value -> value));

        for (NL2SQLResult.NL2SQLData nl2SQLData : data) {
            SqlModel sqlModel = SqlParserUtils.parseSelectSql(
                    nl2SQLData.getNl2sql(),
                    dataAgentConfig.getDbConfig().getType()
            );

            String modelCode = sqlModel.getFromTable().getTableName();
            ChatModelInfoDto modelInfo = modelMap.get(modelCode);
            if (modelInfo == null) {
                throw new RuntimeException("modelCode:" + modelCode + "不存在");
            }

            Map<String, ChatSchemaDto> columnMap = modelInfo.getSchemaList().stream()
                    .collect(Collectors.toMap(ChatSchemaDto::getColumnId, value -> value));
            List<ChatQueryColumn> chatQueryColumns = parseColumns(sqlModel, columnMap);
            List<ChatQueryFilter> chatQueryFilters = parseFilters(sqlModel, columnMap);

            String tableName = getTableName(modelInfo);
            String realSql = nl2SQLData.getNl2sql();
            for (String key : modelMap.keySet()) {
                realSql = realSql.replaceAll(key + "|`" + key + "`", tableName);
            }

            log.info("{},{} 执行sql:{}", request.getTraceId(), request.getRequestId(), realSql);
            DbConfig dbConfig = dataAgentConfig.getDbConfig();
            QueryResult queryResult = dataQueryExecutionPort.query(dbConfig, realSql);
            log.info("{},{} 查询sql结果大小：{}", request.getTraceId(), request.getRequestId(), queryResult.getDataSize());

            ChatQueryData queryData = new ChatQueryData();
            queryData.setColumnList(chatQueryColumns);
            queryData.setFilters(chatQueryFilters);
            queryData.setQuestion(nl2SQLData.getQuery());
            queryData.setNl2sqlResult(realSql);
            queryData.setDataList(queryResult.getDataList());
            dataList.add(queryData);
        }

        parseChartConfig(dataList);
        return dataList;
    }

    private List<ChatQueryColumn> parseColumns(SqlModel sqlModel, Map<String, ChatSchemaDto> columnMap) {
        List<ChatQueryColumn> colList = new ArrayList<>();
        if (sqlModel.getColumnList().size() == 1 && sqlModel.getColumnList().get(0).isStar()) {
            return parseStarColumn(columnMap);
        }

        List<DataOrderBy> orderByList = sqlModel.getOrderByList();
        if (orderByList == null) {
            orderByList = new ArrayList<>();
        }

        for (ModelColumn column : sqlModel.getColumnList()) {
            ChatQueryColumn col = new ChatQueryColumn();
            col.setCol(column.getColumnName());
            if (StringUtils.isBlank(column.getColumnAlias())) {
                col.setGuid(StringUtils.lowerCase(column.getColumnName()));
            } else {
                col.setGuid(StringUtils.lowerCase(column.getColumnAlias()));
                col.setName(column.getColumnAlias());
            }
            col.setColType(column.getColumnKind());

            if (SqlKind.IDENTIFIER.name().equalsIgnoreCase(column.getColumnKind())) {
                ChatSchemaDto chatSchemaDto = columnMap.get(column.getColumnName());
                if (chatSchemaDto != null) {
                    col.setName(chatSchemaDto.getColumnName());
                    col.setDataType(chatSchemaDto.getDataType());
                }
            } else {
                if (column.isAggregator()) {
                    col.setAgg(column.getFunctionName());
                    col.setDataType(StandardColumnType.DECIMAL.name());
                }

                if (CollectionUtils.isNotEmpty(column.getFunctionArgList())) {
                    String arg = column.getFunctionArgList().get(0);
                    ChatSchemaDto chatSchemaDto = columnMap.getOrDefault(
                            StringUtils.lowerCase(arg),
                            columnMap.get(StringUtils.upperCase(arg))
                    );
                    if (chatSchemaDto != null) {
                        if (StringUtils.isBlank(col.getName())) {
                            col.setName(chatSchemaDto.getColumnName());
                        }
                        col.setDataType(chatSchemaDto.getDataType());
                    }
                }
            }

            if (StringUtils.isBlank(col.getDataType()) && isNumberKind(column.getColumnKind())) {
                col.setDataType(StandardColumnType.DECIMAL.name());
            }
            if (StringUtils.isBlank(col.getName())) {
                col.setName(col.getGuid());
            }

            orderByList.stream()
                    .filter(order -> StringUtils.equalsIgnoreCase(order.getColumnName(), col.getGuid())
                            || StringUtils.equalsIgnoreCase(order.getColumnName(), col.getName()))
                    .findAny()
                    .ifPresent(order -> col.setOrder(order.getOrderType().name()));

            colList.add(col);
        }
        return colList;
    }

    private boolean isNumberKind(String kindName) {
        try {
            SqlKind sqlKind = SqlKind.valueOf(kindName);
            return SqlKind.BINARY_ARITHMETIC.contains(sqlKind);
        } catch (Exception e) {
            return false;
        }
    }

    private List<ChatQueryColumn> parseStarColumn(Map<String, ChatSchemaDto> columnMap) {
        List<ChatQueryColumn> colList = new ArrayList<>();
        for (Map.Entry<String, ChatSchemaDto> entry : columnMap.entrySet()) {
            ChatQueryColumn col = new ChatQueryColumn();
            String columnId = StringUtils.lowerCase(entry.getKey());
            ChatSchemaDto value = entry.getValue();
            col.setCol(columnId);
            col.setGuid(columnId);
            col.setColType(value.getDataType());
            col.setName(value.getColumnName());
            colList.add(col);
        }
        return colList;
    }

    private List<ChatQueryFilter> parseFilters(SqlModel sqlModel, Map<String, ChatSchemaDto> columnMap) {
        List<ChatQueryFilter> filters = new ArrayList<>();
        List<WhereCondition> modelFilters = sqlModel.getWhereConditionList();
        if (CollectionUtils.isNotEmpty(modelFilters)) {
            for (WhereCondition condition : modelFilters) {
                if (SqlParserUtils.OR.equalsIgnoreCase(condition.getOperator())) {
                    ChatQueryFilter filter = new ChatQueryFilter();
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

    private ChatQueryFilter parseOneFilter(WhereCondition condition, Map<String, ChatSchemaDto> columnMap) {
        List<String> valueList = condition.getValueList();
        ChatQueryFilter filter = new ChatQueryFilter();
        filter.setCol(condition.getIdentifier());
        filter.setOpt(condition.getComparisonType());
        filter.setOptName(ComparisonType.of(condition.getComparisonType()).getComparisonName());
        filter.setVal(CollectionUtils.isEmpty(valueList) ? condition.getValue() : String.join(",", valueList));
        ChatSchemaDto chatSchemaDto = columnMap.getOrDefault(
                StringUtils.lowerCase(filter.getCol()),
                columnMap.get(StringUtils.upperCase(filter.getCol()))
        );
        filter.setName(chatSchemaDto != null ? chatSchemaDto.getColumnName() : filter.getCol());
        return filter;
    }

    public void parseChartConfig(List<ChatQueryData> dataList) {
        for (ChatQueryData data : dataList) {
            List<Map<String, Object>> resultDataList = data.getDataList();
            if (CollectionUtils.isNotEmpty(resultDataList)) {
                data.setDataList(resultDataList.stream()
                        .map(this::convertKeysToLowerCase)
                        .collect(Collectors.toList()));
            }
            if (CollectionUtils.isEmpty(data.getColumnList())) {
                continue;
            }
            Map<Boolean, List<String>> partitionedCols = data.getColumnList().stream()
                    .collect(Collectors.partitioningBy(
                            col -> StringUtils.isNotBlank(col.getAgg())
                                    || StandardColumnType.DECIMAL.name().equalsIgnoreCase(col.getDataType()),
                            Collectors.mapping(ChatQueryColumn::getGuid, Collectors.toList())
                    ));

            data.setDimCols(partitionedCols.get(false));
            data.setMeasureCols(partitionedCols.get(true));
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
        private NL2SQLResult nl2SQLResult;
        @Getter
        private int eventCount = 0;
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
                if ("[DONE]".equalsIgnoreCase(data) || "heartbeat".equalsIgnoreCase(data)) {
                    return;
                }
                if (StringUtils.isBlank(data)) {
                    return;
                }

                NL2SQLResult eventResult = parseEventResult(data);
                if (eventResult == null) {
                    return;
                }
                if (STATUS_THINK.equalsIgnoreCase(eventResult.getStatus())) {
                    stream.send(ChatDataMessage.ofThink(eventResult.getNl2sql_think()));
                }
                if (STATUS_STREAM_FINISHED.equalsIgnoreCase(eventResult.getStatus())) {
                    stream.send(ChatDataMessage.ofStatus(STATUS_STREAM_FINISHED, STATUS_STREAM_FINISHED));
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

        private NL2SQLResult parseEventResult(String data) {
            try {
                return JSONObject.parseObject(data, NL2SQLResult.class);
            } catch (Exception e) {
                log.error("{},{} nl2sql 解析失败 {}", traceId, requestId, e.getMessage(), e);
                return null;
            }
        }
    }
}

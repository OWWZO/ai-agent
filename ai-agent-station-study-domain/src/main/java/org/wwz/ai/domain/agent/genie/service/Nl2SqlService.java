package org.wwz.ai.domain.agent.genie.service;


import com.alibaba.fastjson.JSONObject;
import org.wwz.ai.domain.agent.genie.agent.util.OkHttpUtil;
import org.wwz.ai.domain.agent.genie.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.genie.config.data.DbConfig;
import org.wwz.ai.domain.agent.genie.data.QueryResult;
import org.wwz.ai.domain.agent.genie.data.dto.*;
import org.wwz.ai.domain.agent.genie.data.model.*;
import org.wwz.ai.domain.agent.genie.data.provider.jdbc.JdbcDataProvider;
import org.wwz.ai.domain.agent.genie.data.provider.jdbc.JdbcQueryRequest;
import org.wwz.ai.domain.agent.genie.data.sql.SqlParserUtils;
import org.wwz.ai.domain.agent.genie.model.response.ChatDataMessage;
import org.wwz.ai.domain.agent.genie.util.JdbcUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.apache.calcite.sql.SqlKind;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class Nl2SqlService {
    public static final String NL2SQL_URL = "/v1/tool/nl2sql";

    @Autowired
    DataAgentConfig dataAgentConfig;
    @Autowired
    JdbcDataProvider jdbcDataProvider;

    public List<ChatQueryData> runNL2SQLSync(NL2SQLReq request) throws Exception {
        AtomicReference<Throwable> err = new AtomicReference<>();
        request.setStream(false);
        String jsonResult = OkHttpUtil.postJsonBody(dataAgentConfig.getAgentUrl() + NL2SQL_URL, null, JSONObject.toJSONString(request));
        log.info("{},{} nl2sql result without sse:{}", request.getTraceId(), request.getRequestId(), jsonResult);
        NL2SQLResult nl2SQLResult = JSONObject.parseObject(jsonResult, NL2SQLResult.class);
        if (err.get() != null) {
            throw new RuntimeException("sse nl2sql failed:" + err.get().getMessage());
        }
        return nl2sqlQueryData(request, nl2SQLResult);
    }

    public List<ChatQueryData> runNL2SQLSse(NL2SQLReq request, SseEmitter emitter) throws Exception {
        AtomicReference<Throwable> err = new AtomicReference<>();
        Nl2SqlSseListener sqlSseListener = new Nl2SqlSseListener(emitter, request.getRequestId(), request.getTraceId());
        OkHttpUtil.requestSse(dataAgentConfig.getAgentUrl() + NL2SQL_URL, null, JSONObject.toJSONString(request), sqlSseListener);
        sqlSseListener.getCountDownLatch().await();
        int eventCount = sqlSseListener.getEventCount();
        log.info("{} sse event count:{}", request.getRequestId(), eventCount);
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
        if (input == null || codeList == null || codeList.isEmpty()) {
            throw new IllegalArgumentException("nl2sql返回对象为空");
        }

        List<Pattern> patterns = codeList.stream()
                .distinct()
                .map(code -> Pattern.compile("(?i)(?<!`)\\b" + Pattern.quote(code) + "\\b(?!`)"))
                .toList();

        Matcher matcher;
        for (Pattern pattern : patterns) {
            matcher = pattern.matcher(input);
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
        } else if ("sql".equalsIgnoreCase(modelInfo.getType())) {
            return "(" + modelInfo.getContent() + ") t";
        } else {
            throw new RuntimeException("不支持的模型类型" + modelInfo.getType());
        }
    }

    public List<ChatQueryData> queryData(NL2SQLReq request, NL2SQLResult nl2SQLResult) throws Exception {
        List<NL2SQLResult.NL2SQLData> data = nl2SQLResult.getData();
        List<ChatQueryData> dataList = new ArrayList<>();
        List<ChatModelInfoDto> schemaInfo = request.getSchemaInfo();
        Map<String, ChatModelInfoDto> modelMap = schemaInfo.stream().collect(Collectors.toMap(ChatModelInfoDto::getModelCode, v -> v));
        for (NL2SQLResult.NL2SQLData nl2SQLData : data) {
            SqlModel sqlModel = SqlParserUtils.parseSelectSql(nl2SQLData.getNl2sql(), dataAgentConfig.getDbConfig().getType());
            String modelCode = sqlModel.getFromTable().getTableName();
            ChatModelInfoDto modelInfo = modelMap.get(modelCode);
            if (modelInfo == null) {
                throw new RuntimeException("modelCode:" + modelCode + "不存在");
            }
            Map<String, ChatSchemaDto> columnMap = modelInfo.getSchemaList().stream().collect(Collectors.toMap(ChatSchemaDto::getColumnId, t -> t));
            List<ChatQueryColumn> chatQueryColumns = parseColumns(sqlModel, columnMap);
            List<ChatQueryFilter> chatQueryFilters = parseFilters(sqlModel, columnMap);
            String tableName = getTableName(modelInfo);
            String realSql = nl2SQLData.getNl2sql();
            for (String key : modelMap.keySet()) {
                realSql = realSql.replaceAll(key + "|`" + key + "`", tableName);
            }
            log.info("{},{} 执行sql:{}", request.getTraceId(), request.getRequestId(), realSql);
            JdbcQueryRequest jdbcQueryRequest = new JdbcQueryRequest();
            DbConfig dbConfig = dataAgentConfig.getDbConfig();
            jdbcQueryRequest.setJdbcConnectionConfig(JdbcUtils.parseJdbcConnectionConfig(dbConfig));
            jdbcQueryRequest.setSql(realSql);
            QueryResult queryResult = jdbcDataProvider.queryData(jdbcQueryRequest);
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
                    ChatSchemaDto chatSchemaDto = columnMap.getOrDefault(StringUtils.lowerCase(arg), columnMap.get(StringUtils.upperCase(arg)));
                    if (chatSchemaDto != null) {
                        if(StringUtils.isBlank(col.getName())){
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

            Optional<DataOrderBy> orderOption = orderByList.stream().filter(f -> StringUtils.equalsIgnoreCase(f.getColumnName(), col.getGuid()) || StringUtils.equalsIgnoreCase(f.getColumnName(), col.getName())).findAny();
            orderOption.ifPresent(dataOrderBy -> col.setOrder(dataOrderBy.getOrderType().name()));
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
        ChatSchemaDto chatSchemaDto = columnMap.getOrDefault(StringUtils.lowerCase(filter.getCol()), columnMap.get(StringUtils.upperCase(filter.getCol())));
        if (chatSchemaDto != null) {
            filter.setName(chatSchemaDto.getColumnName());
        } else {
            filter.setName(filter.getCol());
        }
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
                            col -> StringUtils.isNotBlank(col.getAgg()) || StandardColumnType.DECIMAL.name().equalsIgnoreCase(col.getDataType()),
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
        originalMap.forEach((key, value) -> {
            String lowerKey = key != null ? key.toLowerCase() : null;
            lowerCaseMap.put(lowerKey, value);
        });

        return lowerCaseMap;
    }

    public static class Nl2SqlSseListener extends EventSourceListener {

        public static final String STATUS_THINK = "nl2sql_think";
        public static final String STATUS_DATA = "data";
        public static final String STATUS_STREAM_FINISHED = "finished_stream";

        @Getter
        private NL2SQLResult nl2SQLResult;
        @Getter
        private int eventCount = 0;

        @Getter
        private final CountDownLatch countDownLatch = new CountDownLatch(1);
        private final SseEmitter emitter;
        @Getter
        private boolean success = true;
        @Getter
        private String errorMessage;
        @Getter
        private String requestId;
        @Getter
        private String traceId;

        public Nl2SqlSseListener(SseEmitter emitter, String requestId, String traceId) {
            this.emitter = emitter;
            this.requestId = requestId;
            this.traceId = traceId;
        }


        @Override
        public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
            log.info("SSE nl2sql连接建立");
        }


        private NL2SQLResult eventResultParse(String data) {
            try {
                return JSONObject.parseObject(data, NL2SQLResult.class);
            } catch (Exception e) {
                log.error("{},{} nl2sql 解析失败 {}", traceId, requestId, e.getMessage(), e);
                return null;
            }
        }

        @Override
        public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
            try {
                log.debug("{},{} SSE nl2sql消息:{}", traceId, requestId, data);
                eventCount++;
                if ("[DONE]".equalsIgnoreCase(data)) {
                    return;
                }
                if ("heartbeat".equalsIgnoreCase(data)) {
                    return;
                }
                if (StringUtils.isNotBlank(data)) {
                    NL2SQLResult eventResult = eventResultParse(data);
                    if (eventResult == null) {
                        return;
                    }
                    if (STATUS_THINK.equalsIgnoreCase(eventResult.getStatus())) {
                        emitter.send(ChatDataMessage.ofThink(eventResult.getNl2sql_think()));
                    }
                    if (STATUS_STREAM_FINISHED.equalsIgnoreCase(eventResult.getStatus())) {
                        emitter.send(ChatDataMessage.ofStatus(STATUS_STREAM_FINISHED, STATUS_STREAM_FINISHED));
                    }
                    if (STATUS_DATA.equalsIgnoreCase(eventResult.getStatus())) {
                        log.info("{},{} SSE数据结果：{}", traceId, requestId, data);
                        nl2SQLResult = eventResult;
                    }
                }
            } catch (Exception e) {
                log.error("{},{} nl2sql消息解析错误:{}", traceId, requestId, e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onClosed(@NotNull EventSource eventSource) {
            log.info("{},{} SSE 连接关闭", traceId, requestId);
            countDownLatch.countDown();
        }

        @Override
        public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
            errorMessage = " nl2sql listener failed" + traceId + "," + requestId;
            success = false;
            if (t != null) {
                errorMessage += t.getMessage();
            }
            log.error(errorMessage, t);
            countDownLatch.countDown();
        }

    }
}

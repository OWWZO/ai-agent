package org.wwz.ai.domain.agent.reactor.service;


import com.alibaba.fastjson.JSONObject;
import org.wwz.ai.domain.agent.reactor.agent.util.OkHttpUtil;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.config.data.DbConfig;
import org.wwz.ai.domain.agent.reactor.data.QueryResult;
import org.wwz.ai.domain.agent.reactor.data.dto.*;
import org.wwz.ai.domain.agent.reactor.data.model.*;
import org.wwz.ai.domain.agent.reactor.data.provider.jdbc.JdbcDataProvider;
import org.wwz.ai.domain.agent.reactor.data.provider.jdbc.JdbcQueryRequest;
import org.wwz.ai.domain.agent.reactor.data.sql.SqlParserUtils;
import org.wwz.ai.domain.agent.reactor.model.response.ChatDataMessage;
import org.wwz.ai.domain.agent.reactor.util.JdbcUtils;
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
    DataAgentConfig dataAgentConfig;// 配置类：存服务地址等参数
    @Autowired
    JdbcDataProvider jdbcDataProvider;// JDBC工具：执行SQL用

    /**
     * 同步执行NL2SQL（自然语言查询数据）
     * @param request 包含用户问句、数据源等信息
     * @return 查询结果列表
     */
    public List<ChatQueryData> runNL2SQLSync(NL2SQLReq request) throws Exception {
        AtomicReference<Throwable> err = new AtomicReference<>();
        request.setStream(false);
        // 调AI服务：自然语言 → SQL
        String jsonResult = OkHttpUtil.postJsonBody(dataAgentConfig.getAgentUrl() + NL2SQL_URL, null, JSONObject.toJSONString(request));
        log.info("{},{} nl2sql result without sse:{}", request.getTraceId(), request.getRequestId(), jsonResult);
        // JSON转对象，提取SQL等信息
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


    //处理sql变得符合数据库语法，不会因为关键字、大小写、重复包裹而出错。
    public String replaceFirstMatchedOrThrow(String input, List<String> codeList) {
        if (input == null || codeList == null || codeList.isEmpty()) {
            throw new IllegalArgumentException("模型编码列表为空，无法替换 nl2sql 结果中的模型占位符");
        }

        //将编码列表转为正则模式：去重、忽略大小写、防重复包裹
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
        // 记录原始查询问题（用于后续追踪/展示）
        nl2SQLResult.setRootQuery(request.getQuery());
        //遍历每条生成的SQL，替换模型代码（数据权限控制）
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

    //把AI生成的SQL解析、修正、执行，最后返回数据
    public List<ChatQueryData> queryData(NL2SQLReq request, NL2SQLResult nl2SQLResult) throws Exception {
        // 获取AI生成的多条SQL及其结果
        List<NL2SQLResult.NL2SQLData> data = nl2SQLResult.getData();
        // 最终返回的数据列表
        List<ChatQueryData> dataList = new ArrayList<>();

        // 获取表结构元数据，转成Map方便通过modelCode快速查找
        List<ChatModelInfoDto> schemaInfo = request.getSchemaInfo();
        Map<String, ChatModelInfoDto> modelMap = schemaInfo.stream()
            .collect(Collectors.toMap(ChatModelInfoDto::getModelCode, v -> v));

        // 遍历每条SQL进行执行
        for (NL2SQLResult.NL2SQLData nl2SQLData : data) {
            // 解析SQL，提取表名、字段、条件等
            SqlModel sqlModel = SqlParserUtils.parseSelectSql(
                nl2SQLData.getNl2sql(),
                dataAgentConfig.getDbConfig().getType()
            );

            // 获取SQL中的主表名（逻辑名）
            String modelCode = sqlModel.getFromTable().getTableName();
            // 查找对应的表结构信息
            ChatModelInfoDto modelInfo = modelMap.get(modelCode);
            if (modelInfo == null) {
                throw new RuntimeException("modelCode:" + modelCode + "不存在");
            }

            // 字段元数据转Map，方便通过columnId查找
            Map<String, ChatSchemaDto> columnMap = modelInfo.getSchemaList().stream()
                .collect(Collectors.toMap(ChatSchemaDto::getColumnId, t -> t));

            // 解析SQL中的查询字段 -> 前端展示用
            List<ChatQueryColumn> chatQueryColumns = parseColumns(sqlModel, columnMap);
            // 解析SQL中的过滤条件 -> 前端展示用
            List<ChatQueryFilter> chatQueryFilters = parseFilters(sqlModel, columnMap);

            // 获取真实的数据库表名
            String tableName = getTableName(modelInfo);
            // SQL替换：将逻辑modelCode替换为真实表名（支持 `key` 和 key 两种格式）
            String realSql = nl2SQLData.getNl2sql();
            for (String key : modelMap.keySet()) {
                realSql = realSql.replaceAll(key + "|`" + key + "`", tableName);
            }

            // 打印日志：traceId用于全链路追踪
            log.info("{},{} 执行sql:{}", request.getTraceId(), request.getRequestId(), realSql);

            // 构建JDBC请求
            JdbcQueryRequest jdbcQueryRequest = new JdbcQueryRequest();
            DbConfig dbConfig = dataAgentConfig.getDbConfig();
            jdbcQueryRequest.setJdbcConnectionConfig(JdbcUtils.parseJdbcConnectionConfig(dbConfig));
            jdbcQueryRequest.setSql(realSql);

            // 执行SQL查询
            QueryResult queryResult = jdbcDataProvider.queryData(jdbcQueryRequest);
            log.info("{},{} 查询sql结果大小：{}", request.getTraceId(), request.getRequestId(), queryResult.getDataSize());

            // 封装返回结果
            ChatQueryData queryData = new ChatQueryData();
            queryData.setColumnList(chatQueryColumns);      // 列信息
            queryData.setFilters(chatQueryFilters);          // 过滤条件
            queryData.setQuestion(nl2SQLData.getQuery());    // 原始问题
            queryData.setNl2sqlResult(realSql);              // 实际执行的SQL
            queryData.setDataList(queryResult.getDataList()); // 查询数据
            dataList.add(queryData);
        }

        // 智能推荐图表配置（根据数据特征判断用柱状图/折线图等）
        parseChartConfig(dataList);

        return dataList;
    }

    private List<ChatQueryColumn> parseColumns(SqlModel sqlModel, Map<String, ChatSchemaDto> columnMap) {
        List<ChatQueryColumn> colList = new ArrayList<>();

        // 处理 SELECT * 场景：直接返回所有列
        if (sqlModel.getColumnList().size() == 1 && sqlModel.getColumnList().get(0).isStar()) {
            return parseStarColumn(columnMap);
        }

        // 获取排序字段列表（防null）
        List<DataOrderBy> orderByList = sqlModel.getOrderByList();
        if (orderByList == null) {
            orderByList = new ArrayList<>();
        }

        // 遍历每列，构建查询列对象
        for (ModelColumn column : sqlModel.getColumnList()) {
            ChatQueryColumn col = new ChatQueryColumn();

            // 基础信息
            col.setCol(column.getColumnName());                          // 原始列名/表达式
            if (StringUtils.isBlank(column.getColumnAlias())) {
                col.setGuid(StringUtils.lowerCase(column.getColumnName()));  // 无别名：guid=列名小写
            } else {
                col.setGuid(StringUtils.lowerCase(column.getColumnAlias())); // 有别名：guid=别名小写
                col.setName(column.getColumnAlias());                        // 显示名=别名
            }
            col.setColType(column.getColumnKind());                      // 列类型（IDENTIFIER/函数等）

            // 场景1：普通字段 - 从元数据取名称和类型
            if (SqlKind.IDENTIFIER.name().equalsIgnoreCase(column.getColumnKind())) {
                ChatSchemaDto chatSchemaDto = columnMap.get(column.getColumnName());
                if (chatSchemaDto != null) {
                    col.setName(chatSchemaDto.getColumnName());          // 真实字段名
                    col.setDataType(chatSchemaDto.getDataType());        // 数据库类型
                }
            }
            // 场景2：函数/表达式
            else {
                // 聚合函数：标记聚合类型，默认DECIMAL
                if (column.isAggregator()) {
                    col.setAgg(column.getFunctionName());                // SUM/COUNT/AVG等
                    col.setDataType(StandardColumnType.DECIMAL.name());
                }

                // 从函数参数关联原始字段信息
                if (CollectionUtils.isNotEmpty(column.getFunctionArgList())) {
                    String arg = column.getFunctionArgList().get(0);     // 取第一个参数
                    // 兼容大小写匹配
                    ChatSchemaDto chatSchemaDto = columnMap.getOrDefault(
                        StringUtils.lowerCase(arg),
                        columnMap.get(StringUtils.upperCase(arg))
                    );
                    if (chatSchemaDto != null) {
                        if (StringUtils.isBlank(col.getName())) {
                            col.setName(chatSchemaDto.getColumnName());  // 补充显示名
                        }
                        col.setDataType(chatSchemaDto.getDataType());    // 补充数据类型
                    }
                }
            }

            // 兜底：数值类型默认DECIMAL
            if (StringUtils.isBlank(col.getDataType()) && isNumberKind(column.getColumnKind())) {
                col.setDataType(StandardColumnType.DECIMAL.name());
            }

            // 兜底：无显示名则用guid
            if (StringUtils.isBlank(col.getName())) {
                col.setName(col.getGuid());
            }

            // 关联排序信息
            Optional<DataOrderBy> orderOption = orderByList.stream()
                .filter(f -> StringUtils.equalsIgnoreCase(f.getColumnName(), col.getGuid())
                    || StringUtils.equalsIgnoreCase(f.getColumnName(), col.getName()))
                .findAny();
            orderOption.ifPresent(dataOrderBy -> col.setOrder(dataOrderBy.getOrderType().name())); // ASC/DESC

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

package org.wwz.ai.trigger.http.dataagent.mapper;

import org.springframework.stereotype.Component;
import org.wwz.ai.application.agent.dataquery.result.DataAgentStreamResult;
import org.wwz.ai.application.agent.dataquery.result.DataQueryColumnResult;
import org.wwz.ai.application.agent.dataquery.result.DataQueryFilterResult;
import org.wwz.ai.application.agent.dataquery.result.DataQueryModelResult;
import org.wwz.ai.application.agent.dataquery.result.DataQueryResult;
import org.wwz.ai.application.agent.dataquery.result.DataQuerySchemaResult;
import org.wwz.ai.application.agent.dataquery.result.Nl2SqlQueryResult;
import org.wwz.ai.application.agent.dataquery.result.SqlQueryResult;
import org.wwz.ai.trigger.http.dataagent.vo.DataAgentApiResponseVO;
import org.wwz.ai.trigger.http.dataagent.vo.DataAgentChatMessageResponseVO;
import org.wwz.ai.trigger.http.dataagent.vo.DataQueryColumnResponseVO;
import org.wwz.ai.trigger.http.dataagent.vo.DataQueryFilterResponseVO;
import org.wwz.ai.trigger.http.dataagent.vo.DataQueryModelResponseVO;
import org.wwz.ai.trigger.http.dataagent.vo.DataQueryResultResponseVO;
import org.wwz.ai.trigger.http.dataagent.vo.DataQuerySchemaResponseVO;
import org.wwz.ai.trigger.http.dataagent.vo.Nl2SqlQueryResponseVO;
import org.wwz.ai.trigger.http.dataagent.vo.SqlExecutionResultResponseVO;

import java.util.List;

@Component
public class DataAgentResponseMapper {
    public Nl2SqlQueryResponseVO toResponse(Nl2SqlQueryResult result) {
        Nl2SqlQueryResponseVO response = new Nl2SqlQueryResponseVO();
        response.setRequestId(result.requestId());
        response.setQuery(result.query());
        response.setModelCodeList(result.modelCodeList());
        response.setSchemaInfo(mapModels(result.schemaInfo()));
        response.setCurrentDateInfo(result.currentDateInfo());
        response.setTraceId(result.traceId());
        response.setRecallType(result.recallType());
        response.setStream(result.stream());
        response.setUserInfo(result.userInfo());
        response.setDbType(result.dbType());
        response.setUseVector(result.useVector());
        response.setUseElastic(result.useElastic());
        return response;
    }

    public DataQueryResultResponseVO toResponse(DataQueryResult result) {
        DataQueryResultResponseVO response = new DataQueryResultResponseVO();
        response.setQuestion(result.question());
        response.setDataList(result.dataList());
        response.setColumnList(mapColumns(result.columnList()));
        response.setFilters(mapFilters(result.filters()));
        response.setModelCode(result.modelCode());
        response.setModelName(result.modelName());
        response.setLoadSucceed(result.loadSucceed());
        response.setErrorMessage(result.errorMessage());
        response.setQuerySqlList(result.querySqlList());
        response.setLimit(result.limit());
        response.setDimCols(result.dimCols());
        response.setMeasureCols(result.measureCols());
        response.setNl2sqlResult(result.nl2sqlResult());
        response.setId(result.id());
        return response;
    }

    public SqlExecutionResultResponseVO toResponse(SqlQueryResult result) {
        SqlExecutionResultResponseVO response = new SqlExecutionResultResponseVO();
        response.setQuerySql(result.querySql());
        response.setDataSize(result.dataSize());
        response.setColumnList(result.columnList());
        response.setColumnEnList(result.columnEnList());
        response.setDataList(result.dataList());
        response.setSuccess(result.success());
        response.setErrorMessage(result.errorMessage());
        response.setFromCache(result.fromCache());
        response.setQueryStartTime(result.queryStartTime());
        response.setQueryEndTime(result.queryEndTime());
        response.setCreateConnectionTime(result.createConnectionTime());
        response.setWrapAuthTime(result.wrapAuthTime());
        response.setStartInServer(result.startInServer());
        response.setEndInServer(result.endInServer());
        return response;
    }

    public DataQueryModelResponseVO toResponse(DataQueryModelResult result) {
        return mapModel(result);
    }

    public DataAgentChatMessageResponseVO toResponse(DataAgentStreamResult result) {
        return new DataAgentChatMessageResponseVO(result.eventType(), result.data());
    }

    public <T> DataAgentApiResponseVO<T> apiResponse(T data) {
        return new DataAgentApiResponseVO<>(200, data);
    }

    private List<DataQueryModelResponseVO> mapModels(List<DataQueryModelResult> models) {
        return models.stream().map(this::mapModel).toList();
    }

    private DataQueryModelResponseVO mapModel(DataQueryModelResult model) {
        DataQueryModelResponseVO response = new DataQueryModelResponseVO();
        response.setModelCode(model.modelCode());
        response.setModelName(model.modelName());
        response.setUsePrompt(model.usePrompt());
        response.setBusinessPrompt(model.businessPrompt());
        response.setType(model.type());
        response.setContent(model.content());
        response.setSchemaList(mapSchemas(model.schemaList()));
        return response;
    }

    private List<DataQuerySchemaResponseVO> mapSchemas(List<DataQuerySchemaResult> schemas) {
        return schemas == null ? null : schemas.stream().map(this::mapSchema).toList();
    }

    private DataQuerySchemaResponseVO mapSchema(DataQuerySchemaResult schema) {
        DataQuerySchemaResponseVO response = new DataQuerySchemaResponseVO();
        response.setModelCode(schema.modelCode());
        response.setColumnId(schema.columnId());
        response.setColumnName(schema.columnName());
        response.setColumnComment(schema.columnComment());
        response.setFewShot(schema.fewShot());
        response.setDataType(schema.dataType());
        response.setSynonyms(schema.synonyms());
        response.setVectorUuid(schema.vectorUuid());
        response.setDefaultRecall(schema.defaultRecall());
        response.setAnalyzeSuggest(schema.analyzeSuggest());
        return response;
    }

    private List<DataQueryColumnResponseVO> mapColumns(List<DataQueryColumnResult> columns) {
        return columns == null ? null : columns.stream().map(column -> {
            DataQueryColumnResponseVO response = new DataQueryColumnResponseVO();
            response.setCol(column.col());
            response.setAgg(column.agg());
            response.setOrder(column.order());
            response.setGuid(column.guid());
            response.setName(column.name());
            response.setDataType(column.dataType());
            response.setColType(column.colType());
            return response;
        }).toList();
    }

    private List<DataQueryFilterResponseVO> mapFilters(List<DataQueryFilterResult> filters) {
        return filters == null ? null : filters.stream().map(this::mapFilter).toList();
    }

    private DataQueryFilterResponseVO mapFilter(DataQueryFilterResult filter) {
        DataQueryFilterResponseVO response = new DataQueryFilterResponseVO();
        response.setCol(filter.col());
        response.setOpt(filter.opt());
        response.setVal(filter.val());
        response.setOptName(filter.optName());
        response.setName(filter.name());
        response.setDataType(filter.dataType());
        response.setOperator(filter.operator());
        response.setSubFilters(mapFilters(filter.subFilters()));
        return response;
    }
}

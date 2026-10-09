package org.wwz.ai.application.agent.dataquery.mapper;

import org.springframework.stereotype.Component;
import org.wwz.ai.application.agent.dataquery.command.ColumnSchemaRecallCommand;
import org.wwz.ai.application.agent.dataquery.command.ColumnValueRecallCommand;
import org.wwz.ai.application.agent.dataquery.command.DataAgentChatCommand;
import org.wwz.ai.application.agent.dataquery.result.DataAgentStreamResult;
import org.wwz.ai.application.agent.dataquery.result.DataQueryColumnResult;
import org.wwz.ai.application.agent.dataquery.result.DataQueryFilterResult;
import org.wwz.ai.application.agent.dataquery.result.DataQueryModelResult;
import org.wwz.ai.application.agent.dataquery.result.DataQueryResult;
import org.wwz.ai.application.agent.dataquery.result.DataQuerySchemaResult;
import org.wwz.ai.application.agent.dataquery.result.Nl2SqlQueryResult;
import org.wwz.ai.application.agent.dataquery.result.SqlQueryResult;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueSearchRequest;
import org.wwz.ai.domain.agent.rag.model.query.ColumnSchemaRecallQuery;
import org.wwz.ai.domain.agent.rag.model.query.DataAgentChatQuery;
import org.wwz.ai.domain.agent.rag.model.query.DataQueryColumn;
import org.wwz.ai.domain.agent.rag.model.query.DataQueryFilter;
import org.wwz.ai.domain.agent.rag.model.query.DataQueryStreamEvent;
import org.wwz.ai.domain.agent.rag.model.query.Nl2SqlQuery;
import org.wwz.ai.domain.agent.rag.model.query.SqlExecutionResult;
import org.wwz.ai.domain.agent.rag.model.schema.DataQueryModelDescriptor;
import org.wwz.ai.domain.agent.rag.model.schema.DataQuerySchema;

import java.util.List;

@Component
public class DataQueryMapper {

    public DataAgentChatQuery toDomain(DataAgentChatCommand command) {
        return new DataAgentChatQuery(command.content(), command.traceId());
    }

    public ColumnSchemaRecallQuery toDomain(ColumnSchemaRecallCommand command) {
        return new ColumnSchemaRecallQuery(command.query(), command.limit(), command.scoreThreshold(),
                command.timeout(), command.modelCodeList());
    }

    public ColumnValueSearchRequest toDomain(ColumnValueRecallCommand command) {
        return new ColumnValueSearchRequest(command.query(), command.modelCodeList(), command.limit());
    }

    public Nl2SqlQueryResult toResult(Nl2SqlQuery query) {
        return new Nl2SqlQueryResult(query.getRequestId(), query.getQuery(), query.getModelCodeList(),
                mapModels(query.getSchemaInfo()), query.getCurrentDateInfo(), query.getTraceId(), query.getRecallType(),
                query.getStream(), query.getUserInfo(), query.getDbType(), query.isUseVector(), query.isUseElastic());
    }

    public List<DataQueryModelResult> mapModels(List<DataQueryModelDescriptor> models) {
        return models.stream().map(this::mapModel).toList();
    }

    public DataQueryModelResult mapModel(DataQueryModelDescriptor model) {
        return new DataQueryModelResult(model.getModelCode(), model.getModelName(), model.getUsePrompt(),
                model.getBusinessPrompt(), model.getType(), model.getContent(), mapSchemas(model.getSchemaList()));
    }

    public List<DataQuerySchemaResult> mapSchemas(List<DataQuerySchema> schemas) {
        return schemas == null ? null : schemas.stream().map(this::mapSchema).toList();
    }

    private DataQuerySchemaResult mapSchema(DataQuerySchema schema) {
        return new DataQuerySchemaResult(schema.getModelCode(), schema.getColumnId(), schema.getColumnName(),
                schema.getColumnComment(), schema.getFewShot(), schema.getDataType(), schema.getSynonyms(),
                schema.getVectorUuid(), schema.getDefaultRecall(), schema.getAnalyzeSuggest());
    }

    public List<DataQueryResult> mapDataResults(List<org.wwz.ai.domain.agent.rag.model.query.DataQueryResult> results) {
        return results.stream().map(this::mapDataResult).toList();
    }

    public DataQueryResult mapDataResult(org.wwz.ai.domain.agent.rag.model.query.DataQueryResult result) {
        return new DataQueryResult(result.getQuestion(), result.getDataList(), mapColumns(result.getColumnList()),
                mapFilters(result.getFilters()), result.getModelCode(), result.getModelName(), result.getLoadSucceed(),
                result.getErrorMessage(), result.getQuerySqlList(), result.getLimit(), result.getDimCols(),
                result.getMeasureCols(), result.getNl2sqlResult(), result.getId());
    }

    private List<DataQueryColumnResult> mapColumns(List<DataQueryColumn> columns) {
        return columns == null ? null : columns.stream().map(column -> new DataQueryColumnResult(
                column.getCol(), column.getAgg(), column.getOrder(), column.getGuid(), column.getName(),
                column.getDataType(), column.getColType())).toList();
    }

    private List<DataQueryFilterResult> mapFilters(List<DataQueryFilter> filters) {
        return filters == null ? null : filters.stream().map(this::mapFilter).toList();
    }

    private DataQueryFilterResult mapFilter(DataQueryFilter filter) {
        return new DataQueryFilterResult(filter.getCol(), filter.getOpt(), filter.getVal(), filter.getOptName(),
                filter.getName(), filter.getDataType(), filter.getOperator(), mapFilters(filter.getSubFilters()));
    }

    public SqlQueryResult mapSqlResult(SqlExecutionResult result) {
        return new SqlQueryResult(result.getQuerySql(), result.getDataSize(), result.getColumnList(),
                result.getColumnEnList(), result.getDataList(), result.getSuccess(), result.getErrorMessage(),
                result.isFromCache(), result.getQueryStartTime(), result.getQueryEndTime(),
                result.getCreateConnectionTime(), result.getWrapAuthTime(), result.getStartInServer(),
                result.getEndInServer());
    }

    public DataAgentStreamResult mapStreamEvent(DataQueryStreamEvent event) {
        return new DataAgentStreamResult(event.eventType(), event.data());
    }
}

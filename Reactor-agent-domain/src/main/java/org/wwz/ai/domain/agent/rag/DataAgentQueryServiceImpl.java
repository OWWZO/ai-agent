package org.wwz.ai.domain.agent.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueSearchRequest;
import org.wwz.ai.domain.agent.rag.model.config.DataQuerySettings;
import org.wwz.ai.domain.agent.rag.model.query.ColumnSchemaRecallQuery;
import org.wwz.ai.domain.agent.rag.model.query.DataAgentChatQuery;
import org.wwz.ai.domain.agent.rag.model.query.DataQueryResult;
import org.wwz.ai.domain.agent.rag.model.query.DataQueryStreamEvent;
import org.wwz.ai.domain.agent.rag.model.query.Nl2SqlQuery;
import org.wwz.ai.domain.agent.rag.model.query.SqlExecutionResult;
import org.wwz.ai.domain.agent.rag.model.schema.DataQueryModelDescriptor;
import org.wwz.ai.domain.agent.rag.model.schema.DataQuerySchema;
import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelInfo;
import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelSchema;
import org.wwz.ai.domain.agent.rag.port.DataQueryExecutionPort;
import org.wwz.ai.domain.agent.rag.service.ChatModelInfoService;
import org.wwz.ai.domain.agent.rag.service.ChatModelSchemaService;
import org.wwz.ai.domain.agent.rag.service.SchemaRecallService;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.domain.agent.reactor.model.enums.EventTypeEnum;
import org.wwz.ai.types.agent.config.AgentExecutorNames;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/** Data Query domain orchestration for schema recall, NL2SQL, and result execution. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataAgentQueryServiceImpl implements DataAgentQueryService {

    private final DataQuerySettings settings;
    private final TableRagService tableRagService;
    private final ChatModelInfoService chatModelInfoService;
    private final ChatModelSchemaService chatModelSchemaService;
    private final Nl2SqlQueryService nl2SqlQueryService;
    private final DataQueryExecutionPort dataQueryExecutionPort;
    private final SchemaRecallService schemaRecallService;

    @Resource(name = AgentExecutorNames.TOOL_EXECUTOR)
    private Executor toolExecutor;

    @Override
    public Nl2SqlQuery queryAllSchema() {
        Nl2SqlQuery query = buildBaseQuery("");
        List<DataQuerySchema> schemas = chatModelSchemaService.queryAllSchemas();
        Map<String, List<DataQuerySchema>> schemaMap = schemas.stream()
                .collect(Collectors.groupingBy(DataQuerySchema::getModelCode, Collectors.toList()));
        for (DataQueryModelDescriptor model : query.getSchemaInfo()) {
            model.setSchemaList(schemaMap.get(model.getModelCode()));
        }
        return query;
    }

    @Override
    public List<Map<String, Object>> recallSchemaColumns(ColumnSchemaRecallQuery query) {
        if (!Boolean.TRUE.equals(settings.getQdrantConfig().getEnable())) {
            return new ArrayList<>();
        }
        return schemaRecallService.vectorRecall(query);
    }

    @Override
    public List<Map<String, Object>> recallColumnValues(ColumnValueSearchRequest query) {
        if (!Boolean.TRUE.equals(settings.getEsConfig().getEnable())) {
            return new ArrayList<>();
        }
        return schemaRecallService.esValueRecall(query);
    }

    @Override
    public void chatQuery(DataAgentChatQuery request, AgentMessageStream stream) throws Exception {
        Nl2SqlQuery nl2SqlQuery = prepareNl2SqlQuery(request.content(), null);
        stream.send(new DataQueryStreamEvent(EventTypeEnum.DEBUG.name(), nl2SqlQuery.getRequestId()));
        AgentExecutorSupport.execute(toolExecutor, "dataAgentChatQuery", () -> {
            try {
                List<DataQueryResult> result = nl2SqlQueryService.runNL2SQLSse(nl2SqlQuery, stream);
                stream.send(new DataQueryStreamEvent(EventTypeEnum.CHART_DATA.name(), result));
            } catch (Exception e) {
                log.error("{},{} 智能问数异常：{}", nl2SqlQuery.getTraceId(), nl2SqlQuery.getRequestId(), e.getMessage(), e);
                try {
                    stream.send(new DataQueryStreamEvent(EventTypeEnum.ERROR.name(), e.getMessage()));
                } catch (Exception sendException) {
                    log.warn("{},{} sse 发送异常：{}", nl2SqlQuery.getTraceId(), nl2SqlQuery.getRequestId(),
                            sendException.getMessage(), sendException);
                }
            } finally {
                try {
                    stream.send(new DataQueryStreamEvent(EventTypeEnum.READY.name(), ""));
                } catch (Exception sendException) {
                    log.warn("{},{} sse 发送异常：{}", nl2SqlQuery.getTraceId(), nl2SqlQuery.getRequestId(),
                            sendException.getMessage(), sendException);
                }
                stream.complete();
            }
        });
    }

    @Override
    public List<DataQueryResult> apiChatQuery(DataAgentChatQuery request) {
        long start = System.currentTimeMillis();
        Nl2SqlQuery nl2SqlQuery = prepareNl2SqlQuery(request.content(), request.traceId());
        log.info("{},api chat query request: {}", nl2SqlQuery.getRequestId(), request);
        try {
            return nl2SqlQueryService.runNL2SQLSync(nl2SqlQuery);
        } catch (Exception e) {
            log.error("{},{} api chat query error : {}", nl2SqlQuery.getTraceId(),
                    nl2SqlQuery.getRequestId(), e.getMessage(), e);
            return new ArrayList<>();
        } finally {
            log.info("{},{} query:{},数据分析取数耗时:{}", nl2SqlQuery.getTraceId(), nl2SqlQuery.getRequestId(),
                    request.content(), System.currentTimeMillis() - start);
        }
    }

    @Override
    public SqlExecutionResult testQuery(DataAgentChatQuery request) {
        return dataQueryExecutionPort.query(request.content());
    }

    @Override
    public Nl2SqlQuery buildNl2SqlQuery(String query) {
        return prepareNl2SqlQuery(query, null);
    }

    @Override
    public List<DataQueryModelDescriptor> queryAllModelsWithSchema() {
        return chatModelInfoService.queryAllModelsWithSchema();
    }

    @Override
    public SqlExecutionResult previewData(String modelCode) {
        return chatModelInfoService.previewData(modelCode);
    }

    void enrichNl2Sql(Nl2SqlQuery query) throws IOException {
        List<DataQuerySchema> recalled = recallModelSchema(query);
        Map<String, List<DataQuerySchema>> modelSchemaMap = recalled.stream()
                .filter(schema -> StringUtils.isNotBlank(schema.getColumnId()))
                .collect(Collectors.groupingBy(DataQuerySchema::getModelCode, Collectors.toList()));
        for (DataQueryModelDescriptor model : query.getSchemaInfo()) {
            model.setSchemaList(modelSchemaMap.get(model.getModelCode()));
        }
    }

    private Nl2SqlQuery prepareNl2SqlQuery(String query, String traceId) {
        try {
            Nl2SqlQuery nl2SqlQuery = buildBaseQuery(query);
            nl2SqlQuery.setRequestId(UUID.randomUUID().toString());
            nl2SqlQuery.setTraceId(StringUtils.isNotBlank(traceId) ? traceId : nl2SqlQuery.getRequestId());
            nl2SqlQuery.setDbType(settings.getDbConfig().getType());
            enrichNl2Sql(nl2SqlQuery);
            return nl2SqlQuery;
        } catch (IOException e) {
            throw new IllegalStateException("构建 NL2SQL 请求失败", e);
        }
    }

    private List<DataQuerySchema> recallModelSchema(Nl2SqlQuery query) throws IOException {
        List<DataQuerySchema> recalled = null;
        try {
            recalled = tableRagService.tableRag(query);
        } catch (Exception e) {
            log.warn("{},{} tableRag 异常：{}", query.getTraceId(), query.getRequestId(), e.getMessage(), e);
        }

        if (CollectionUtils.isEmpty(recalled)) {
            log.warn("{},{} 召回schema为空，读取数据库", query.getTraceId(), query.getRequestId());
            return chatModelSchemaService.listDistinctSchemas().stream()
                    .map(this::toDataQuerySchema)
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        List<ChatModelSchema> defaults = chatModelSchemaService.queryDefaultRecallFields();
        mergeSchema(recalled, defaults);
        return recalled;
    }

    private void mergeSchema(List<DataQuerySchema> schemas, List<ChatModelSchema> defaults) {
        if (CollectionUtils.isEmpty(defaults)) {
            return;
        }
        Map<String, Set<String>> existing = schemas.stream()
                .collect(Collectors.groupingBy(DataQuerySchema::getModelCode,
                        Collectors.mapping(DataQuerySchema::getColumnId, Collectors.toSet())));
        List<DataQuerySchema> missing = defaults.stream()
                .filter(schema -> !existing.getOrDefault(schema.getModelCode(), Collections.emptySet())
                        .contains(schema.getColumnId()))
                .map(this::toDataQuerySchema)
                .toList();
        schemas.addAll(missing);
    }

    private Nl2SqlQuery buildBaseQuery(String query) {
        Nl2SqlQuery nl2SqlQuery = new Nl2SqlQuery();
        nl2SqlQuery.setQuery(query);
        nl2SqlQuery.setUseElastic(settings.getEsConfig().getEnable());
        nl2SqlQuery.setUseVector(settings.getQdrantConfig().getEnable());

        String week = LocalDate.now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        nl2SqlQuery.setCurrentDateInfo(String.format(nl2SqlQuery.getCurrentDateInfo(), LocalDate.now(), week));

        List<ChatModelInfo> modelList = chatModelInfoService.listDistinctModels();
        if (CollectionUtils.isEmpty(modelList)) {
            throw new IllegalStateException("问数模型为空，请检查 chat_model_info 表是否存在 yn=1 的有效数据，以及 MyBatis-Plus 逻辑删除配置是否正确");
        }

        List<String> modelCodes = new ArrayList<>();
        List<DataQueryModelDescriptor> models = new ArrayList<>();
        nl2SqlQuery.setModelCodeList(modelCodes);
        nl2SqlQuery.setSchemaInfo(models);
        for (ChatModelInfo modelInfo : modelList) {
            DataQueryModelDescriptor model = new DataQueryModelDescriptor();
            model.setModelCode(modelInfo.getCode());
            model.setModelName(modelInfo.getName());
            model.setBusinessPrompt(modelInfo.getBusinessPrompt());
            model.setUsePrompt(modelInfo.getUsePrompt());
            model.setType(modelInfo.getType());
            model.setContent(modelInfo.getContent());
            modelCodes.add(modelInfo.getCode());
            models.add(model);
        }
        return nl2SqlQuery;
    }

    private DataQuerySchema toDataQuerySchema(ChatModelSchema schema) {
        DataQuerySchema result = new DataQuerySchema();
        BeanUtils.copyProperties(schema, result);
        return result;
    }
}

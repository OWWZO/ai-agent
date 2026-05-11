package org.wwz.ai.domain.agent.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;
import org.wwz.ai.domain.agent.adapter.port.DataQueryExecutionPort;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.config.data.DbConfig;
import org.wwz.ai.domain.agent.reactor.data.QueryResult;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatModelInfoDto;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatQueryData;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatSchemaDto;
import org.wwz.ai.domain.agent.reactor.data.dto.ColumnEsRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.ColumnVectorRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.NL2SQLReq;
import org.wwz.ai.domain.agent.ledger.entity.ChatModelInfo;
import org.wwz.ai.domain.agent.ledger.entity.ChatModelSchema;
import org.wwz.ai.domain.agent.reactor.model.enums.EventTypeEnum;
import org.wwz.ai.domain.agent.reactor.model.req.DataAgentChatReq;
import org.wwz.ai.domain.agent.reactor.model.response.ChatDataMessage;
import org.wwz.ai.domain.agent.reactor.service.ChatModelInfoService;
import org.wwz.ai.domain.agent.reactor.service.ChatModelSchemaService;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.types.agent.config.AgentExecutorNames;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.sql.SQLException;
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

/**
 * 数据问答稳定领域实现。
 * 通过 rag 子域语义收口 legacy dataagent 主链路，避免 case 继续直连旧 bridge。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataAgentQueryServiceImpl implements DataAgentQueryService {

    private final DataAgentConfig dataAgentConfig;
    private final TableRagService tableRagService;
    private final ChatModelInfoService chatModelInfoService;
    private final ChatModelSchemaService chatModelSchemaService;
    private final Nl2SqlQueryService nl2SqlQueryService;
    private final DataQueryExecutionPort dataQueryExecutionPort;
    private final SchemaRecallService schemaRecallService;

    @Resource(name = AgentExecutorNames.TOOL_EXECUTOR)
    private Executor toolExecutor;

    @Override
    public NL2SQLReq queryAllSchemaNl2SqlReq() {
        NL2SQLReq baseNl2SqlReq = buildBaseNl2SqlReq("");
        List<ChatSchemaDto> chatSchemaDtos = chatModelSchemaService.queryAllSchemaDto();
        Map<String, List<ChatSchemaDto>> schemaMap = chatSchemaDtos.stream()
                .collect(Collectors.groupingBy(ChatSchemaDto::getModelCode, Collectors.toList()));
        for (ChatModelInfoDto modelInfoDto : baseNl2SqlReq.getSchemaInfo()) {
            modelInfoDto.setSchemaList(schemaMap.get(modelInfoDto.getModelCode()));
        }
        return baseNl2SqlReq;
    }

    @Override
    public List<Map<String, Object>> vectorRecall(ColumnVectorRecallReq req) {
        return schemaRecallService.vectorRecall(req);
    }

    @Override
    public List<Map<String, Object>> esRecall(ColumnEsRecallReq req) throws IOException {
        return schemaRecallService.esValueRecall(req);
    }

    @Override
    public void chatQuery(DataAgentChatReq req, AgentMessageStream stream) throws Exception {
        NL2SQLReq nl2SqlReq = prepareNl2SqlReq(req.getContent(), null);
        stream.send(ChatDataMessage.ofStatus(EventTypeEnum.DEBUG.name(), nl2SqlReq.getRequestId()));
        AgentExecutorSupport.execute(toolExecutor, "dataAgentChatQuery", () -> {
            try {
                List<ChatQueryData> result = nl2SqlQueryService.runNL2SQLSse(nl2SqlReq, stream);
                stream.send(ChatDataMessage.ofData(result));
            } catch (Exception e) {
                log.error("{},{} 智能问数异常：{}", nl2SqlReq.getTraceId(), nl2SqlReq.getRequestId(), e.getMessage(), e);
                try {
                    stream.send(ChatDataMessage.ofError(e.getMessage()));
                } catch (Exception sendException) {
                    log.warn("{},{} sse 发送异常：{}", nl2SqlReq.getTraceId(), nl2SqlReq.getRequestId(),
                            sendException.getMessage(), sendException);
                }
            } finally {
                try {
                    stream.send(ChatDataMessage.ofReady(""));
                } catch (Exception sendException) {
                    log.warn("{},{} sse 发送异常：{}", nl2SqlReq.getTraceId(), nl2SqlReq.getRequestId(),
                            sendException.getMessage(), sendException);
                }
                stream.complete();
            }
        });
    }

    @Override
    public List<ChatQueryData> apiChatQuery(DataAgentChatReq req) {
        long start = System.currentTimeMillis();
        NL2SQLReq nl2SqlReq = prepareNl2SqlReq(req.getContent(), req.getTraceId());
        log.info("{},api chat query request: {}", nl2SqlReq.getRequestId(), req);
        try {
            return nl2SqlQueryService.runNL2SQLSync(nl2SqlReq);
        } catch (Exception e) {
            log.error("{},{} api chat query error : {}", nl2SqlReq.getTraceId(),
                    nl2SqlReq.getRequestId(), e.getMessage(), e);
            return new ArrayList<>();
        } finally {
            log.info("{},{} query:{},数据分析取数耗时:{}",
                    nl2SqlReq.getTraceId(), nl2SqlReq.getRequestId(), req.getContent(),
                    System.currentTimeMillis() - start);
        }
    }

    @Override
    public Object testQuery(DataAgentChatReq req) throws SQLException {
        DbConfig dbConfig = dataAgentConfig.getDbConfig();
        return dataQueryExecutionPort.query(dbConfig, req.getContent());
    }

    @Override
    public NL2SQLReq getNl2SqlReq(String query) throws Exception {
        return prepareNl2SqlReq(query, null);
    }

    @Override
    public List<?> queryAllModelsWithSchema() {
        return chatModelInfoService.queryAllModelsWithSchema();
    }

    @Override
    public QueryResult previewData(String modelCode) throws Exception {
        return chatModelInfoService.previewData(modelCode);
    }

    void enrichNl2Sql(NL2SQLReq baseNl2SqlReq) throws IOException {
        List<ChatSchemaDto> chatSchemaDtoList = recallModelSchema(baseNl2SqlReq);
        Map<String, List<ChatSchemaDto>> modelSchemaMap = chatSchemaDtoList.stream()
                .filter(schema -> StringUtils.isNotBlank(schema.getColumnId()))
                .collect(Collectors.groupingBy(ChatSchemaDto::getModelCode, Collectors.toList()));
        for (ChatModelInfoDto dto : baseNl2SqlReq.getSchemaInfo()) {
            dto.setSchemaList(modelSchemaMap.get(dto.getModelCode()));
        }
    }

    private NL2SQLReq prepareNl2SqlReq(String query, String traceId) {
        try {
            NL2SQLReq nl2SqlReq = buildBaseNl2SqlReq(query);
            nl2SqlReq.setRequestId(UUID.randomUUID().toString());
            nl2SqlReq.setTraceId(StringUtils.isNotBlank(traceId) ? traceId : nl2SqlReq.getRequestId());
            nl2SqlReq.setDbType(dataAgentConfig.getDbConfig().getType());
            enrichNl2Sql(nl2SqlReq);
            return nl2SqlReq;
        } catch (IOException e) {
            throw new IllegalStateException("构建 NL2SQL 请求失败", e);
        }
    }

    private List<ChatSchemaDto> recallModelSchema(NL2SQLReq baseNl2SqlReq) throws IOException {
        List<ChatSchemaDto> recallSchema = null;
        try {
            recallSchema = tableRagService.tableRag(baseNl2SqlReq);
        } catch (Exception e) {
            log.warn("{},{} tableRag 异常：{}", baseNl2SqlReq.getTraceId(),
                    baseNl2SqlReq.getRequestId(), e.getMessage(), e);
        }

        if (CollectionUtils.isEmpty(recallSchema)) {
            log.warn("{},{} 召回schema为空，读取数据库", baseNl2SqlReq.getTraceId(), baseNl2SqlReq.getRequestId());
            List<ChatModelSchema> list = chatModelSchemaService.listDistinctSchemas();
            List<ChatSchemaDto> dtoList = new ArrayList<>();
            for (ChatModelSchema schema : list) {
                ChatSchemaDto dto = new ChatSchemaDto();
                BeanUtils.copyProperties(schema, dto);
                dtoList.add(dto);
            }
            return dtoList;
        }

        List<ChatModelSchema> defaultRecallSchema = chatModelSchemaService.queryDefaultRecallFields();
        mergeSchema(recallSchema, defaultRecallSchema);
        return recallSchema;
    }

    private void mergeSchema(List<ChatSchemaDto> schemaList, List<ChatModelSchema> defaultRecallSchema) {
        if (CollectionUtils.isEmpty(defaultRecallSchema)) {
            return;
        }
        Map<String, Set<String>> existMap = schemaList.stream()
                .collect(Collectors.groupingBy(
                        ChatSchemaDto::getModelCode,
                        Collectors.mapping(ChatSchemaDto::getColumnId, Collectors.toSet())
                ));
        List<ChatSchemaDto> toAdd = defaultRecallSchema.stream()
                .filter(schema -> !existMap.getOrDefault(schema.getModelCode(), Collections.emptySet())
                        .contains(schema.getColumnId()))
                .map(schema -> {
                    ChatSchemaDto dto = new ChatSchemaDto();
                    BeanUtils.copyProperties(schema, dto);
                    return dto;
                })
                .toList();
        schemaList.addAll(toAdd);
    }

    private NL2SQLReq buildBaseNl2SqlReq(String query) {
        NL2SQLReq nl2SQLReq = new NL2SQLReq();
        nl2SQLReq.setQuery(query);
        nl2SQLReq.setUseElastic(dataAgentConfig.getEsConfig().getEnable());
        nl2SQLReq.setUseVector(dataAgentConfig.getQdrantConfig().getEnable());

        String week = LocalDate.now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        nl2SQLReq.setCurrentDateInfo(String.format(nl2SQLReq.getCurrentDateInfo(), LocalDate.now(), week));

        List<ChatModelInfo> modelList = chatModelInfoService.listDistinctModels();
        if (CollectionUtils.isEmpty(modelList)) {
            throw new IllegalStateException("问数模型为空，请检查 chat_model_info 表是否存在 yn=1 的有效数据，以及 MyBatis-Plus 逻辑删除配置是否正确");
        }

        List<String> modelCodeList = new ArrayList<>();
        List<ChatModelInfoDto> dtoList = new ArrayList<>();
        nl2SQLReq.setModelCodeList(modelCodeList);
        nl2SQLReq.setSchemaInfo(dtoList);

        for (ChatModelInfo modelInfo : modelList) {
            ChatModelInfoDto dto = new ChatModelInfoDto();
            dto.setModelCode(modelInfo.getCode());
            dto.setModelName(modelInfo.getName());
            dto.setBusinessPrompt(modelInfo.getBusinessPrompt());
            dto.setUsePrompt(modelInfo.getUsePrompt());
            dto.setType(modelInfo.getType());
            dto.setContent(modelInfo.getContent());
            modelCodeList.add(modelInfo.getCode());
            dtoList.add(dto);
        }
        return nl2SQLReq;
    }
}

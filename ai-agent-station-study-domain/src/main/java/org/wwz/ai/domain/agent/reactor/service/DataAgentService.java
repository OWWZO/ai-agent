package org.wwz.ai.domain.agent.reactor.service;


import com.alibaba.fastjson.JSONObject;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.reactor.agent.util.ThreadUtil;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.config.data.DbConfig;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatModelInfoDto;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatQueryData;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatSchemaDto;
import org.wwz.ai.domain.agent.reactor.data.dto.NL2SQLReq;
import org.wwz.ai.domain.agent.reactor.data.provider.jdbc.JdbcDataProvider;
import org.wwz.ai.domain.agent.reactor.data.provider.jdbc.JdbcQueryRequest;
import org.wwz.ai.domain.agent.reactor.entity.ChatModelInfo;
import org.wwz.ai.domain.agent.reactor.entity.ChatModelSchema;
import org.wwz.ai.domain.agent.reactor.model.enums.EventTypeEnum;
import org.wwz.ai.domain.agent.reactor.model.req.DataAgentChatReq;
import org.wwz.ai.domain.agent.reactor.model.response.ChatDataMessage;
import org.wwz.ai.domain.agent.reactor.util.JdbcUtils;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DataAgentService {
    public static Long AUTO_AGENT_SSE_TIMEOUT = 60 * 60 * 1000L;
    @Autowired
    DataAgentConfig dataAgentConfig;
    @Autowired
    TableRagService tableRagService;
    @Autowired
    ChatModelInfoService chatModelInfoService;
    @Autowired
    ChatModelSchemaService chatModelSchemaService;
    @Autowired
    Nl2SqlService nl2SqlService;
    @Autowired
    JdbcDataProvider jdbcDataProvider;

    public NL2SQLReq getNl2SqlReqWithOutRecall(String query) throws IOException {
        NL2SQLReq baseNl2SqlReq = getBaseNl2SqlReq(query);
        baseNl2SqlReq.setRequestId(UUID.randomUUID().toString());
        baseNl2SqlReq.setTraceId(baseNl2SqlReq.getRequestId());
        enrichNl2Sql(baseNl2SqlReq);
        baseNl2SqlReq.setDbType(dataAgentConfig.getDbConfig().getType());
        return baseNl2SqlReq;
    }

    public NL2SQLReq getNl2SqlReq(String query) throws IOException {
        NL2SQLReq baseNl2SqlReq = getBaseNl2SqlReq(query);
        baseNl2SqlReq.setRequestId(UUID.randomUUID().toString());
        baseNl2SqlReq.setTraceId(baseNl2SqlReq.getRequestId());
        enrichNl2Sql(baseNl2SqlReq);
        baseNl2SqlReq.setDbType(dataAgentConfig.getDbConfig().getType());
        return baseNl2SqlReq;
    }

    public List<ChatQueryData> apiChatQueryData(DataAgentChatReq req) {
        long start = System.currentTimeMillis();
        NL2SQLReq baseNl2SqlReq = getBaseNl2SqlReq(req.getContent());
        baseNl2SqlReq.setRequestId(UUID.randomUUID().toString());
        baseNl2SqlReq.setTraceId(req.getTraceId());
        log.info("{},api chat query request: {}", baseNl2SqlReq.getRequestId(), JSONObject.toJSONString(req));
        try {
            enrichNl2Sql(baseNl2SqlReq);
            baseNl2SqlReq.setDbType(dataAgentConfig.getDbConfig().getType());
            return nl2SqlService.runNL2SQLSync(baseNl2SqlReq);
        } catch (Exception e) {
            log.error("{},{} api chat query error : {}", baseNl2SqlReq.getTraceId(), baseNl2SqlReq.getRequestId(), e.getMessage(), e);
            return new ArrayList<>();
        } finally {
            log.info("{},{} query:{},数据分析取数耗时:{}", baseNl2SqlReq.getTraceId(), baseNl2SqlReq.getRequestId(), req.getContent(), System.currentTimeMillis() - start);
        }

    }

    public SseEmitter webChatQueryData(DataAgentChatReq req) throws Exception {
        SseEmitter emitter = new SseEmitter(AUTO_AGENT_SSE_TIMEOUT);
        NL2SQLReq baseNl2SqlReq = getBaseNl2SqlReq(req.getContent());
        baseNl2SqlReq.setRequestId(UUID.randomUUID().toString());
        baseNl2SqlReq.setTraceId(baseNl2SqlReq.getRequestId());
        baseNl2SqlReq.setDbType(dataAgentConfig.getDbConfig().getType());
        enrichNl2Sql(baseNl2SqlReq);
        emitter.send(ChatDataMessage.ofStatus(EventTypeEnum.DEBUG.name(), baseNl2SqlReq.getRequestId()));
        ThreadUtil.execute(() -> {
            try {
                List<ChatQueryData> result = nl2SqlService.runNL2SQLSse(baseNl2SqlReq, emitter);
                emitter.send(ChatDataMessage.ofData(result));
            } catch (Exception e) {
                log.error("{},{} 智能问数异常：{}", baseNl2SqlReq.getTraceId(), baseNl2SqlReq.getRequestId(), e.getMessage(), e);
                try {
                    emitter.send(ChatDataMessage.ofError(e.getMessage()));
                } catch (IOException ex) {
                    log.warn("{},{} sse 发送异常：{}", baseNl2SqlReq.getTraceId(), baseNl2SqlReq.getRequestId(), e.getMessage(), e);
                }
            } finally {
                try {
                    emitter.send(ChatDataMessage.ofReady(""));
                } catch (IOException e) {
                    log.warn("{},{} sse 发送异常：{}", baseNl2SqlReq.getTraceId(), baseNl2SqlReq.getRequestId(), e.getMessage(), e);
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    public void enrichNl2Sql(NL2SQLReq baseNl2SqlReq) throws IOException {
        List<ChatSchemaDto> chatSchemaDtoList = recallModelSchema(baseNl2SqlReq);
        Map<String, List<ChatSchemaDto>> modelSchemaMap = chatSchemaDtoList.stream()
                .filter(f -> StringUtils.isNotBlank(f.getColumnId()))
                .collect(Collectors.groupingBy(
                        ChatSchemaDto::getModelCode,
                        Collectors.toList()
                ));
        for (ChatModelInfoDto dto : baseNl2SqlReq.getSchemaInfo()) {
            dto.setSchemaList(modelSchemaMap.get(dto.getModelCode()));
        }
    }

    private List<ChatSchemaDto> recallModelSchema(NL2SQLReq baseNl2SqlReq) throws IOException {
        List<ChatSchemaDto> recallSchema = null;
        try {
            recallSchema = tableRagService.tableRag(baseNl2SqlReq);
        } catch (Exception e) {
            log.warn("{},{} tableRag 异常：{}", baseNl2SqlReq.getTraceId(), baseNl2SqlReq.getRequestId(), e.getMessage(), e);
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
                        Collectors.mapping(
                                ChatSchemaDto::getColumnId,
                                Collectors.toSet()
                        )
                ));
        List<ChatSchemaDto> toAdd = defaultRecallSchema.stream()
                .filter(schema -> {
                    Set<String> existColumns = existMap.getOrDefault(schema.getModelCode(), Collections.emptySet());
                    return !existColumns.contains(schema.getColumnId());
                }).map(m -> {
                    ChatSchemaDto dto = new ChatSchemaDto();
                    BeanUtils.copyProperties(m, dto);
                    return dto;
                })
                .toList();

        schemaList.addAll(toAdd);
    }

    public NL2SQLReq queryAllSchemaNl2SqlReq() {
        NL2SQLReq baseNl2SqlReq = getBaseNl2SqlReq("");
        List<ChatSchemaDto> chatSchemaDtos = chatModelSchemaService.queryAllSchemaDto();
        Map<String, List<ChatSchemaDto>> schemaMap = chatSchemaDtos.stream().collect(Collectors.groupingBy(ChatSchemaDto::getModelCode, Collectors.toList()));
        for (ChatModelInfoDto modelInfoDto : baseNl2SqlReq.getSchemaInfo()) {
            modelInfoDto.setSchemaList(schemaMap.get(modelInfoDto.getModelCode()));
        }
        return baseNl2SqlReq;
    }

    private NL2SQLReq getBaseNl2SqlReq(String query) {
        NL2SQLReq nl2SQLReq = new NL2SQLReq();
        nl2SQLReq.setQuery(query);
        nl2SQLReq.setUseElastic(dataAgentConfig.getEsConfig().getEnable());
        nl2SQLReq.setUseVector(dataAgentConfig.getQdrantConfig().getEnable());
        String week = LocalDate.now().getDayOfWeek().getDisplayName(
                TextStyle.FULL,
                Locale.CHINA
        );
        nl2SQLReq.setCurrentDateInfo(String.format(nl2SQLReq.getCurrentDateInfo(), LocalDate.now(), week));
        List<ChatModelInfo> modelList = chatModelInfoService.listDistinctModels();
        if (CollectionUtils.isEmpty(modelList)) {
            throw new IllegalStateException("问数模型为空，请检查 chat_model_info 表是否存在 yn=1 的有效数据，以及 MyBatis-Plus 逻辑删除配置是否正确");
        }
        List<String> modelCodeList = new ArrayList<>();
        nl2SQLReq.setModelCodeList(modelCodeList);
        List<ChatModelInfoDto> dtoList = new ArrayList<>();
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

    public Object testQuery(DataAgentChatReq req) throws SQLException {
        JdbcQueryRequest jdbcQueryRequest = new JdbcQueryRequest();
        DbConfig dbConfig = dataAgentConfig.getDbConfig();
        jdbcQueryRequest.setJdbcConnectionConfig(JdbcUtils.parseJdbcConnectionConfig(dbConfig));
        jdbcQueryRequest.setSql(req.getContent());
        return jdbcDataProvider.queryData(jdbcQueryRequest);
    }
}

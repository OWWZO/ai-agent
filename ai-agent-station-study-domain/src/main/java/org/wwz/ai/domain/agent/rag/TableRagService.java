package org.wwz.ai.domain.agent.rag;


import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatSchemaDto;
import org.wwz.ai.domain.agent.reactor.data.dto.NL2SQLReq;
import org.wwz.ai.domain.agent.reactor.data.dto.TableRagResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TableRagService {

    public static final String TABLE_RAG_URL = "/v1/tool/table_rag";

    @Autowired
    DataAgentConfig dataAgentConfig;
    @Autowired
    RemoteHttpPort remoteHttpPort;

    public List<ChatSchemaDto> tableRag(NL2SQLReq req) throws IOException {
        if (!dataAgentConfig.getEsConfig().getEnable() && !dataAgentConfig.getQdrantConfig().getEnable()) {
            log.info("{},{} 未开启向量和es，不进行tableRag",req.getTraceId(),req.getRequestId());
            return new ArrayList<>();
        }
        String res;
        try {
            res = postTableRag(req);
        } catch (Exception e) {
            log.warn("{},{} tableRag server error,retry:{}",req.getTraceId(),req.getRequestId(), e.getMessage());
            res = postTableRag(req);
        }
        log.info("{},{} tableRag result:{}", req.getTraceId(),req.getRequestId(),res);
        TableRagResult tableRagResult = JSONObject.parseObject(res, TableRagResult.class);
        if (tableRagResult == null || tableRagResult.getCode() == null) {
            throw new RuntimeException("tableRag result is null");
        }
        if (tableRagResult.getCode() != 200) {
            throw new RuntimeException("tableRag server return error");
        }
        List<TableRagResult.TableRagData> data = tableRagResult.getData();
        if (CollectionUtils.isEmpty(data)) {
            log.warn("{},{} tableRag result data is empty，降级为空结果，由上游决定是否回退",
                    req.getTraceId(), req.getRequestId());
            return new ArrayList<>();
        }
        return data.stream()
                .filter(Objects::nonNull)
                .map(TableRagResult.TableRagData::getSchemaList)
                .filter(CollectionUtils::isNotEmpty)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private String postTableRag(NL2SQLReq req) throws IOException {
        return remoteHttpPort.execute(RemoteHttpRequest.builder()
                .method("POST")
                .url(dataAgentConfig.getAgentUrl() + TABLE_RAG_URL)
                .headers(java.util.Map.of("Content-Type", "application/json"))
                .body(JSONObject.toJSONString(req))
                .build());
    }
}

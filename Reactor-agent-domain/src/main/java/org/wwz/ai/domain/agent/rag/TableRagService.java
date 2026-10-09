package org.wwz.ai.domain.agent.rag;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.rag.model.config.DataQuerySettings;
import org.wwz.ai.domain.agent.rag.model.query.Nl2SqlQuery;
import org.wwz.ai.domain.agent.rag.model.schema.DataQuerySchema;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Table-level schema recall; an unavailable or empty recall leaves fallback to the caller. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableRagService {

    public static final String TABLE_RAG_URL = "/v1/tool/table_rag";

    private final DataQuerySettings settings;
    private final RemoteHttpPort remoteHttpPort;

    public List<DataQuerySchema> tableRag(Nl2SqlQuery query) throws IOException {
        if (!Boolean.TRUE.equals(settings.getEsConfig().getEnable())
                && !Boolean.TRUE.equals(settings.getQdrantConfig().getEnable())) {
            log.info("{},{} 未开启向量和es，不进行tableRag", query.getTraceId(), query.getRequestId());
            return new ArrayList<>();
        }

        String response;
        try {
            response = postTableRag(query);
        } catch (Exception e) {
            log.warn("{},{} tableRag server error,retry:{}", query.getTraceId(), query.getRequestId(), e.getMessage());
            response = postTableRag(query);
        }
        log.info("{},{} tableRag result:{}", query.getTraceId(), query.getRequestId(), response);

        JSONObject body = JSONObject.parseObject(response);
        if (body == null || body.getInteger("code") == null) {
            throw new RuntimeException("tableRag result is null");
        }
        if (body.getIntValue("code") != 200) {
            throw new RuntimeException("tableRag server return error");
        }
        JSONArray data = body.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            log.warn("{},{} tableRag result data is empty，降级为空结果，由上游决定是否回退",
                    query.getTraceId(), query.getRequestId());
            return new ArrayList<>();
        }

        List<DataQuerySchema> schemas = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            JSONObject model = data.getJSONObject(i);
            if (model == null) {
                continue;
            }
            JSONArray schemaList = model.getJSONArray("schemaList");
            if (CollectionUtils.isEmpty(schemaList)) {
                continue;
            }
            for (int j = 0; j < schemaList.size(); j++) {
                JSONObject schema = schemaList.getJSONObject(j);
                if (schema != null) {
                    schemas.add(schema.toJavaObject(DataQuerySchema.class));
                }
            }
        }
        return schemas;
    }

    private String postTableRag(Nl2SqlQuery query) throws IOException {
        return remoteHttpPort.execute(RemoteHttpRequest.builder()
                .method("POST")
                .url(settings.getAgentUrl() + TABLE_RAG_URL)
                .headers(Map.of("Content-Type", "application/json"))
                .body(JSONObject.toJSONString(query))
                .build());
    }
}

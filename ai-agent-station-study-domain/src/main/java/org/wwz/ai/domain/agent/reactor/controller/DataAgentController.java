package org.wwz.ai.domain.agent.reactor.controller;


import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatQueryData;
import org.wwz.ai.domain.agent.reactor.data.dto.ColumnEsRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.ColumnVectorRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.NL2SQLReq;
import org.wwz.ai.domain.agent.reactor.model.req.DataAgentChatReq;
import org.wwz.ai.domain.agent.reactor.service.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/data")
public class DataAgentController {

    @Autowired
    DataAgentService dataAgentService;
    @Autowired
    VectorService vectorService;
    @Autowired
    SchemaRecallService schemaRecallService;
    @Autowired
    ChatModelSchemaService chatModelSchemaService;
    @Autowired
    ChatModelInfoService chatModelInfoService;

    @PostMapping(value = "queryModelInfo")
    public NL2SQLReq vectorRecall(@RequestBody JSONObject req) {
        return dataAgentService.queryAllSchemaNl2SqlReq();
    }

    @PostMapping(value = "vectorRecall")
    public List<Map<String, Object>> vectorRecall(@RequestBody ColumnVectorRecallReq req) {
        return schemaRecallService.vectorRecall(req);
    }

    @PostMapping(value = "esRecall")
    public List<Map<String, Object>> esRecall(@RequestBody ColumnEsRecallReq req) throws IOException {
        return schemaRecallService.esValueRecall(req);
    }

    @PostMapping(value = "chatQuery", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatQuery(@RequestBody DataAgentChatReq req) throws Exception {
        return dataAgentService.webChatQueryData(req);
    }

    @PostMapping(value = "apiChatQuery")
    public List<ChatQueryData> apiChatQuery(@RequestBody DataAgentChatReq req) {
        return dataAgentService.apiChatQueryData(req);
    }


    @PostMapping(value = "testQuery")
    public Object testQuery(@RequestBody DataAgentChatReq req) throws Exception {
        return dataAgentService.testQuery(req);
    }

    @PostMapping(value = "getNl2SqlReq")
    public NL2SQLReq getNl2SqlReq(@RequestBody DataAgentChatReq req) throws Exception {
        return dataAgentService.getNl2SqlReq(req.getContent());
    }

    @GetMapping(value = "allModels")
    public Map<String, Object> allModels() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", chatModelInfoService.queryAllModelsWithSchema());
        return result;
    }

    @GetMapping(value = "previewData")
    public Map<String, Object> previewData(@RequestParam("modelCode") String modelCode) throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", chatModelInfoService.previewData(modelCode));
        return result;
    }

}

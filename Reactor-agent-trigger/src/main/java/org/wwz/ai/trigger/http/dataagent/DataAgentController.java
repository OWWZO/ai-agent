package org.wwz.ai.trigger.http.dataagent;


import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.application.agent.dataquery.IDataAgentApplicationService;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatQueryData;
import org.wwz.ai.domain.agent.reactor.data.dto.ColumnEsRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.ColumnVectorRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.NL2SQLReq;
import org.wwz.ai.domain.agent.reactor.model.req.DataAgentChatReq;
import org.wwz.ai.trigger.http.reactor.support.SseEmitterAgentSessionStream;
import org.wwz.ai.trigger.http.reactor.support.SseLifecycleSupport;

import javax.annotation.Resource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/data")
public class DataAgentController {

    @Resource
    private IDataAgentApplicationService dataAgentApplicationService;

    @PostMapping(value = "queryModelInfo")
    public NL2SQLReq vectorRecall(@RequestBody JSONObject req) {
        return dataAgentApplicationService.queryAllSchemaNl2SqlReq();
    }

    @PostMapping(value = "vectorRecall")
    public List<Map<String, Object>> vectorRecall(@RequestBody ColumnVectorRecallReq req) {
        return dataAgentApplicationService.vectorRecall(req);
    }

    @PostMapping(value = "esRecall")
    public List<Map<String, Object>> esRecall(@RequestBody ColumnEsRecallReq req) throws IOException {
        return dataAgentApplicationService.esRecall(req);
    }

    @PostMapping(value = "chatQuery", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatQuery(@RequestBody DataAgentChatReq req) throws Exception {
        SseEmitter emitter = SseLifecycleSupport.createEmitter(TimeUnit.HOURS.toMillis(1));
        SseLifecycleSupport.registerLifecycle(emitter,
                Objects.toString(req.getTraceId(), "data-agent-chat"),
                null,
                log);
        dataAgentApplicationService.chatQuery(req, new SseEmitterAgentSessionStream(emitter));
        return emitter;
    }

    @PostMapping(value = "apiChatQuery")
    public List<ChatQueryData> apiChatQuery(@RequestBody DataAgentChatReq req) {
        return dataAgentApplicationService.apiChatQuery(req);
    }


    @PostMapping(value = "testQuery")
    public Object testQuery(@RequestBody DataAgentChatReq req) throws Exception {
        return dataAgentApplicationService.testQuery(req);
    }

    @PostMapping(value = "getNl2SqlReq")
    public NL2SQLReq getNl2SqlReq(@RequestBody DataAgentChatReq req) throws Exception {
        return dataAgentApplicationService.getNl2SqlReq(req.getContent());
    }

    @GetMapping(value = "allModels")
    public Map<String, Object> allModels() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", dataAgentApplicationService.queryAllModelsWithSchema());
        return result;
    }

    @GetMapping(value = "previewData")
    public Map<String, Object> previewData(@RequestParam("modelCode") String modelCode) throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", dataAgentApplicationService.previewData(modelCode));
        return result;
    }

}

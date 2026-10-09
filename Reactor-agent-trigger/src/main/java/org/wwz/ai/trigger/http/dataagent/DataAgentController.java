package org.wwz.ai.trigger.http.dataagent;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.application.agent.dataquery.IDataAgentApplicationService;
import org.wwz.ai.application.agent.dataquery.command.DataQueryCommand;
import org.wwz.ai.trigger.http.dataagent.mapper.DataAgentRequestMapper;
import org.wwz.ai.trigger.http.dataagent.mapper.DataAgentResponseMapper;
import org.wwz.ai.trigger.http.dataagent.mapper.DataAgentSseResponseStream;
import org.wwz.ai.trigger.http.dataagent.vo.ColumnValueRecallRequestVO;
import org.wwz.ai.trigger.http.dataagent.vo.DataAgentApiResponseVO;
import org.wwz.ai.trigger.http.dataagent.vo.DataAgentChatRequestVO;
import org.wwz.ai.trigger.http.dataagent.vo.DataQueryModelResponseVO;
import org.wwz.ai.trigger.http.dataagent.vo.DataQueryResultResponseVO;
import org.wwz.ai.trigger.http.dataagent.vo.Nl2SqlQueryResponseVO;
import org.wwz.ai.trigger.http.dataagent.vo.SqlExecutionResultResponseVO;
import org.wwz.ai.trigger.http.dataagent.vo.VectorRecallRequestVO;
import org.wwz.ai.trigger.http.reactor.support.SseEmitterAgentSessionStream;
import org.wwz.ai.trigger.http.reactor.support.SseLifecycleSupport;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** HTTP and SSE protocol adapter for Data Query use cases. */
@Slf4j
@RestController
@RequestMapping("/data")
public class DataAgentController {

    @Resource
    private IDataAgentApplicationService dataAgentApplicationService;
    @Resource
    private DataAgentRequestMapper requestMapper;
    @Resource
    private DataAgentResponseMapper responseMapper;

    @PostMapping(value = "queryModelInfo")
    public Nl2SqlQueryResponseVO queryModelInfo(@RequestBody JSONObject request) {
        return responseMapper.toResponse(dataAgentApplicationService.queryAllSchema());
    }

    @PostMapping(value = "vectorRecall")
    public List<java.util.Map<String, Object>> vectorRecall(@RequestBody VectorRecallRequestVO request) {
        return dataAgentApplicationService.vectorRecall(requestMapper.toCommand(request));
    }

    @PostMapping(value = "esRecall")
    public List<java.util.Map<String, Object>> esRecall(@RequestBody ColumnValueRecallRequestVO request) throws IOException {
        return dataAgentApplicationService.esRecall(requestMapper.toCommand(request));
    }

    @PostMapping(value = "chatQuery", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatQuery(@RequestBody DataAgentChatRequestVO request) throws Exception {
        SseEmitter emitter = SseLifecycleSupport.createLongLivedEmitter();
        SseLifecycleSupport.registerLifecycle(emitter, Objects.toString(request.getTraceId(), "data-agent-chat"), null, log);
        SseEmitterAgentSessionStream sessionStream = new SseEmitterAgentSessionStream(emitter);
        dataAgentApplicationService.chatQuery(requestMapper.toCommand(request),
                new DataAgentSseResponseStream(sessionStream, responseMapper));
        return emitter;
    }

    @PostMapping(value = "apiChatQuery")
    public List<DataQueryResultResponseVO> apiChatQuery(@RequestBody DataAgentChatRequestVO request) {
        return dataAgentApplicationService.apiChatQuery(requestMapper.toCommand(request)).stream()
                .map(responseMapper::toResponse)
                .toList();
    }

    @PostMapping(value = "testQuery")
    public SqlExecutionResultResponseVO testQuery(@RequestBody DataAgentChatRequestVO request) {
        return responseMapper.toResponse(dataAgentApplicationService.testQuery(
                new DataQueryCommand(request.getContent())));
    }

    @PostMapping(value = "getNl2SqlReq")
    public Nl2SqlQueryResponseVO getNl2SqlReq(@RequestBody DataAgentChatRequestVO request) throws Exception {
        return responseMapper.toResponse(dataAgentApplicationService.buildNl2SqlQuery(
                new DataQueryCommand(request.getContent())));
    }

    @GetMapping(value = "allModels")
    public DataAgentApiResponseVO<List<DataQueryModelResponseVO>> allModels() {
        List<DataQueryModelResponseVO> models = dataAgentApplicationService.queryAllModelsWithSchema().stream()
                .map(responseMapper::toResponse)
                .toList();
        return responseMapper.apiResponse(models);
    }

    @GetMapping(value = "previewData")
    public DataAgentApiResponseVO<SqlExecutionResultResponseVO> previewData(@RequestParam("modelCode") String modelCode) throws Exception {
        return responseMapper.apiResponse(responseMapper.toResponse(dataAgentApplicationService.previewData(modelCode)));
    }
}

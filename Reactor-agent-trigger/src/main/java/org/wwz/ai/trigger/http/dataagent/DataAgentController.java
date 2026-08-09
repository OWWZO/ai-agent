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

/**
 * 数据问数 HTTP 入口。
 *
 * <p>该控制器同时提供模型元数据、向量/ES 召回、同步查询和 SSE 问数接口。它只负责
 * 把 HTTP 请求交给 {@link IDataAgentApplicationService}，不在触发层拼装 NL2SQL、
 * 检索或 Agent 工具流程，避免数据问数规则散落在 Controller 中。</p>
 *
 * <p>同步接口直接返回应用服务结果；{@code chatQuery} 则额外建立 SSE 生命周期，
 * 将流写入适配器后立即返回 emitter，让长时间运行的问数过程不被 HTTP 方法调用阻塞。</p>
 */
@Slf4j
@RestController
@RequestMapping("/data")
public class DataAgentController {

    @Resource
    private IDataAgentApplicationService dataAgentApplicationService;

    @PostMapping(value = "queryModelInfo")
    public NL2SQLReq vectorRecall(@RequestBody JSONObject req) {
        // 该接口沿用历史路由名，实际返回的是全量模型结构对应的 NL2SQL 请求模板。
        return dataAgentApplicationService.queryAllSchemaNl2SqlReq();
    }

    @PostMapping(value = "vectorRecall")
    public List<Map<String, Object>> vectorRecall(@RequestBody ColumnVectorRecallReq req) {
        // 向量召回的过滤条件和 TopK 等语义由应用服务及其下游检索实现负责解释。
        return dataAgentApplicationService.vectorRecall(req);
    }

    @PostMapping(value = "esRecall")
    public List<Map<String, Object>> esRecall(@RequestBody ColumnEsRecallReq req) throws IOException {
        // ES 召回可能抛出 IO 异常，交由上层统一异常处理，不在入口伪造空结果。
        return dataAgentApplicationService.esRecall(req);
    }

    @PostMapping(value = "chatQuery", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatQuery(@RequestBody DataAgentChatReq req) throws Exception {
        // 问数流只在入口创建 emitter 并注册关闭回调，具体查询、工具调用和结果事件由
        // application service 完成；这样 HTTP 生命周期与问数业务生命周期保持分层。
        SseEmitter emitter = SseLifecycleSupport.createLongLivedEmitter();
        SseLifecycleSupport.registerLifecycle(emitter,
                Objects.toString(req.getTraceId(), "data-agent-chat"),
                null,
                log);
        dataAgentApplicationService.chatQuery(req, new SseEmitterAgentSessionStream(emitter));
        return emitter;
    }

    @PostMapping(value = "apiChatQuery")
    public List<ChatQueryData> apiChatQuery(@RequestBody DataAgentChatReq req) {
        // API 查询保留结构化同步结果，适合不需要 SSE 增量事件的调用方。
        return dataAgentApplicationService.apiChatQuery(req);
    }


    @PostMapping(value = "testQuery")
    public Object testQuery(@RequestBody DataAgentChatReq req) throws Exception {
        // 测试查询复用应用服务的真实问数链路，返回类型保持历史兼容协议。
        return dataAgentApplicationService.testQuery(req);
    }

    @PostMapping(value = "getNl2SqlReq")
    public NL2SQLReq getNl2SqlReq(@RequestBody DataAgentChatReq req) throws Exception {
        return dataAgentApplicationService.getNl2SqlReq(req.getContent());
    }

    @GetMapping(value = "allModels")
    public Map<String, Object> allModels() throws Exception {
        // 这里保持旧的 code/data 包装格式，不能直接改为统一 Response 以免破坏现有客户端。
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", dataAgentApplicationService.queryAllModelsWithSchema());
        return result;
    }

    @GetMapping(value = "previewData")
    public Map<String, Object> previewData(@RequestParam("modelCode") String modelCode) throws Exception {
        // 预览只按模型编码读取示例数据，不在 Controller 层校验模型内容或转换数据结构。
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", dataAgentApplicationService.previewData(modelCode));
        return result;
    }

}

package org.wwz.ai.trigger.http;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.query.AgentQuerySubmitResult;
import org.wwz.ai.application.agent.query.GptQueryCommand;
import org.wwz.ai.application.agent.query.IGptQueryApplicationService;
import org.wwz.ai.trigger.http.agent.mapper.GptQueryRequestMapper;
import org.wwz.ai.trigger.http.agent.vo.GptQueryRequestVO;
import org.wwz.ai.types.agent.exception.AgentConcurrentRunException;
import org.wwz.ai.types.agent.exception.AgentExecutorBusyException;
import org.wwz.ai.types.enums.ResponseCode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Agent HTTP 入口：主对话提交与健康检查。
 */
@Slf4j
@RestController
@RequestMapping("/")
public class AiAgentController {

    @Resource
    private IGptQueryApplicationService gptQueryApplicationService;

    @Resource
    private GptQueryRequestMapper gptQueryRequestMapper;

    @RequestMapping(value = "/web/health", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    /**
     * 提交一轮 Agent 执行。成功返回 JSON；观察流走 GET /api/agent/session/{sessionId}/stream。
     * 已有进行中的任务返回 409，不创建 SSE。
     */
    @RequestMapping(value = "/web/api/v1/gpt/queryAgentStreamIncr", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response<Map<String, Object>>> queryAgentStreamIncr(@RequestBody GptQueryRequestVO params) {
        String requestId = Objects.toString(params == null ? null : params.getRequestId(), "legacy-gpt-query");
        try {
            GptQueryCommand command = gptQueryRequestMapper.toCommand(params);
            AgentQuerySubmitResult submitted = gptQueryApplicationService.submitAgentQuery(command);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("accepted", submitted.isAccepted());
            data.put("sessionId", submitted.getSessionId());
            data.put("requestId", submitted.getRequestId());
            return ResponseEntity.ok(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build());
        } catch (AgentConcurrentRunException e) {
            log.warn("{} concurrent run rejected", requestId, e);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("accepted", false);
            data.put("activeRequestId", e.getActiveRequestId());
            data.put("activeSessionId", e.getActiveSessionId());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .data(data)
                    .build());
        } catch (AgentExecutorBusyException e) {
            log.warn("{} dispatch busy", requestId, e);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("{} reject gpt query", requestId, e);
            return ResponseEntity.badRequest().body(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("{} queryAgentStreamIncr bootstrap error", requestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build());
        }
    }
}

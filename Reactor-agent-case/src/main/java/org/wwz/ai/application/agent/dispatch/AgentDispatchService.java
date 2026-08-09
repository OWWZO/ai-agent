package org.wwz.ai.application.agent.dispatch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.execute.IExecuteStrategy;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.enums.ResponseTypeEnum;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.types.agent.exception.AgentConcurrentRunException;
import org.wwz.ai.types.exception.BizException;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent 应用层调度器。
 * 负责根据 agentType 选择执行策略，并把输出协议隔离在 case 边界之外。
 */
@Slf4j
@Service
public class AgentDispatchService implements IAgentDispatchService {

    @Resource
    private Map<String, IExecuteStrategy> executeStrategyMap;

    @Override
    public void dispatch(AgentRequest request, AgentSessionStream stream) throws Exception {
        String strategy = null;

        // agentType 只负责选择应用策略，具体执行和输出协议分别交给 strategy 与 stream，避免调度器承载业务逻辑。
        if (request.getAgentType() != null) {
            if (AgentType.WORKFLOW.getValue().equals(request.getAgentType())) {
                strategy = "flowAgentExecuteStrategy";
            } else if (AgentType.PLAN_SOLVE.getValue().equals(request.getAgentType())) {
                strategy = "planSolveAgentExecuteStrategy";
            } else if (AgentType.REACT.getValue().equals(request.getAgentType())) {
                strategy = "reactAgentExecuteStrategy";
            }
        }

        if (strategy == null || strategy.isEmpty()) {
            // 未识别类型沿用 ReAct 作为兼容默认策略，保证旧客户端不因缺少 agentType 直接失效。
            strategy = "reactAgentExecuteStrategy";
        }

        IExecuteStrategy executeStrategy = executeStrategyMap.get(strategy);
        if (executeStrategy == null) {
            throw new BizException("不存在的执行策略类型 strategy:" + strategy);
        }

        try {
            // case 层只负责转发请求；SSE/WebSocket 等协议适配由 trigger 实现 AgentSessionStream。
            executeStrategy.execute(request, stream);
        } catch (AgentConcurrentRunException e) {
            // 结构化终态错误，供前端 isTerminalGuardError 收口，而不是裸 completeWithError。
            log.warn("{} concurrent run rejected activeRequestId={} activeSessionId={}",
                    request == null ? "-" : request.getRequestId(),
                    e.getActiveRequestId(),
                    e.getActiveSessionId());
            if (stream != null && !stream.isAborted()) {
                stream.send(buildConcurrentRejectResult(request, e));
                stream.complete();
            }
        }
    }

    static GptProcessResult buildConcurrentRejectResult(AgentRequest request, AgentConcurrentRunException e) {
        GptProcessResult result = new GptProcessResult();
        result.setFinished(true);
        result.setStatus("failed");
        result.setPackageType("result");
        result.setResponseType(ResponseTypeEnum.text.name());
            result.setErrorMsg(e != null && e.getMessage() != null
                ? e.getMessage()
                : "已有任务在进行中，请等待完成或先停止后再试");
        result.setResponse("");
        result.setResponseAll("");
        result.setReqId(request == null ? null : request.getRequestId());
        result.setTraceId(request == null ? null : request.getRequestId());
        result.setEncrypted(false);
        Map<String, Object> resultMap = new HashMap<>();
        if (e != null) {
            if (e.getActiveRequestId() != null) {
                resultMap.put("activeRequestId", e.getActiveRequestId());
            }
            if (e.getActiveSessionId() != null) {
                resultMap.put("activeSessionId", e.getActiveSessionId());
            }
        }
        result.setResultMap(resultMap);
        return result;
    }
}

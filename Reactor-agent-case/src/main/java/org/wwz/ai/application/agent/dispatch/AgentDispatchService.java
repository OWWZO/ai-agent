package org.wwz.ai.application.agent.dispatch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.execute.IExecuteStrategy;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.application.agent.stream.AgentStreamFrameMapper;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamResult;
import org.wwz.ai.domain.agent.runtime.enums.ResponseTypeEnum;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
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

    /** 唯一的应用层执行策略 bean；链路差异由策略内部按 agentType 选择。 */
    private static final String STRATEGY_BEAN = "agentExecuteStrategy";

    @Resource
    private Map<String, IExecuteStrategy> executeStrategyMap;

    @Resource
    private AgentStreamFrameMapper agentStreamFrameMapper;

    @Override
    public void dispatch(AgentExecutionCommand request, AgentSessionStream stream) throws Exception {
        // 执行策略只有一个实现：链路差异由 AgentExecuteStrategy 按 agentType 自行选择。
        // 这里保留 Map 注入是为了让 dispatch 不感知具体实现类，新增策略只需注册 bean。
        IExecuteStrategy executeStrategy = executeStrategyMap.get(STRATEGY_BEAN);
        if (executeStrategy == null) {
            throw new BizException("不存在的执行策略类型 strategy:" + STRATEGY_BEAN);
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
                stream.sendFrame(agentStreamFrameMapper.toFrame(buildConcurrentRejectResult(request, e)));
                stream.complete();
            }
        }
    }

    static AgentStreamResult buildConcurrentRejectResult(AgentExecutionCommand request, AgentConcurrentRunException e) {
        AgentStreamResult result = new AgentStreamResult();
        result.setComplete(true);
        result.setStatus("failed");
        result.setFrameType("result");
        result.setContentType(ResponseTypeEnum.text.name());
        result.setErrorMessage(e != null && e.getMessage() != null
                ? e.getMessage()
                : "已有任务在进行中，请等待完成或先停止后再试");
        result.setContentDelta("");
        result.setContent("");
        result.setRequestId(request == null ? null : request.getRequestId());
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
        result.setEventData(resultMap);
        return result;
    }
}

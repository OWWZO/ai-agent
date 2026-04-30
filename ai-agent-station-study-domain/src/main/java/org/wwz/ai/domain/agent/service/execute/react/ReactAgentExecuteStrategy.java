package org.wwz.ai.domain.agent.service.execute.react;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.service.IExecuteStrategy;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

import java.util.Map;

/**
 * React Agent 执行策略：以 AgentRequest 贯穿逻辑树，避免重复转换
 */
@Slf4j
@Service("reactAgentExecuteStrategy")
public class ReactAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultReactAgentExecuteStrategyFactory defaultReactAgentExecuteStrategyFactory;

    @Resource
    private ReactorConfig reactorConfig;

    /**
     * 主入口：直接使用 AgentRequest，无转换（AutoAgent 等 Reactor 入口调用）
     */
    @Override
    public void execute(AgentRequest request, SseEmitter emitter) throws Exception {
        applyOutputStyle(request);
        doExecute(request, emitter);
    }


    private void doExecute(AgentRequest request, SseEmitter emitter) throws Exception {

        //获取到root节点
        StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultReactAgentExecuteStrategyFactory.armoryStrategyHandler();

        //构建上下文
        DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext = DefaultReactAgentExecuteStrategyFactory.DynamicContext.builder()
                .emitter(emitter)
                .build();

        try {
            //传入动态上下文 触发链式调用
            String result = executeHandler.apply(request, dynamicContext);
            log.info("ReactAgent execute result: {}", result);
        } catch (Exception e) {
            finishRunOnFailure(dynamicContext.getAgentContext(), "REACT_EXECUTE_ERROR", e);
            throw e;
        }
    }

    private void applyOutputStyle(AgentRequest request) {
        //获取最终的产物展示方式的prompt
        Map<String, String> outputStyleMap = reactorConfig.getOutputStylePrompts();
        // 判断用户是否选择了特定的输出风格
        if (StringUtils.isNotEmpty(request.getOutputStyle())) {
            // 从配置中查找该风格对应的提示词，找不到则返回空字符串
            String append = outputStyleMap.computeIfAbsent(request.getOutputStyle(), k -> "");
            // 将风格提示词追加到用户原始查询后面
            request.setQuery(request.getQuery() + append);
        }
    }

    private void finishRunOnFailure(AgentContext agentContext, String errorCode, Exception e) {
        if (agentContext == null || !agentContext.hasActiveLedgerRun() || agentContext.getAgentRunState() == null) {
            return;
        }
        agentContext.getExecutionRecorder().finishRun(DialogueRunFinishRecord.builder()
                .runId(agentContext.getAgentRunState().getRunId())
                .requestId(agentContext.getRequestId())
                .status(ExecutionLedgerConstants.STATUS_FAILED)
                .errorCode(errorCode)
                .errorMsg(e == null ? null : e.getMessage())
                .build());
    }


}

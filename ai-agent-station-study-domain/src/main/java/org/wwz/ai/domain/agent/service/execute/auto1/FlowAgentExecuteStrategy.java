package org.wwz.ai.domain.agent.service.execute.auto1;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.service.IExecuteStrategy;
import org.wwz.ai.domain.agent.service.execute.auto1.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程执行策略
 */
@Slf4j
public class FlowAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultFlowAgentExecuteStrategyFactory defaultFlowAgentExecuteStrategyFactory;

    @Override
    public void execute(AgentRequest request, SseEmitter emitter) throws Exception {
        ExecuteCommandEntity executeCommandEntity = ExecuteCommandEntity.builder()
                .requestId(request.getRequestId())
                .message(request.getQuery())
                .agentType(request.getAgentType())
                .outputStyle(request.getOutputStyle())
                .isStream(request.getIsStream())
                .sopPrompt(request.getSopPrompt())
                .basePrompt(request.getBasePrompt())
                .build();
//        execute(executeCommandEntity, emitter);
    }

//
//    public void execute(ExecuteCommandEntity executeCommandEntity, ResponseBodyEmitter emitter) throws Exception {
//        StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
//                = defaultFlowAgentExecuteStrategyFactory.armoryStrategyHandler();
//
//        // 创建动态上下文并注入 SSE emitter（业务上下文初始化统一下沉到 RootNode）
//        DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext = new DefaultFlowAgentExecuteStrategyFactory.DynamicContext();
//        dynamicContext.setEmitter(emitter);
//        // 兼容老代码：部分节点仍通过 magic key 取 emitter，这里保留一份
//        dynamicContext.setValue("emitter", emitter);
//
//        String apply = executeHandler.apply(executeCommandEntity, dynamicContext);
//        log.info("流程执行结果:{}", apply);
//        // 完成标识由 Step4 在 sendCompleteResult 中携带 metrics 发送
//    }

}

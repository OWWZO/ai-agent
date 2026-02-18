package org.wwz.ai.domain.agent.service.execute.react;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.wwz.ai.domain.agent.genie.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.genie.agent.printer.Printer;
import org.wwz.ai.domain.agent.genie.agent.util.DateUtil;
import org.wwz.ai.domain.agent.genie.config.GenieConfig;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.service.IExecuteStrategy;
import org.wwz.ai.domain.agent.service.execute.react.printer.ResponseBodyPrinter;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * React Agent 执行策略
 */
@Slf4j
@Service("reactAgentExecuteStrategy")
public class ReactAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultReactAgentExecuteStrategyFactory defaultReactAgentExecuteStrategyFactory;

    @Resource
    private GenieConfig genieConfig;

    @Override
    public void execute(ExecuteCommandEntity executeCommandEntity, ResponseBodyEmitter emitter) throws Exception {
        StrategyHandler<ExecuteCommandEntity, AgentContext, String> executeHandler
                = defaultReactAgentExecuteStrategyFactory.armoryStrategyHandler();

        // 1. 处理输出样式
        String query = executeCommandEntity.getMessage();
        Map<String, String> outputStyleMap = genieConfig.getOutputStylePrompts();
        if (!StringUtils.isEmpty(executeCommandEntity.getOutputStyle())) {
            query += outputStyleMap.computeIfAbsent(executeCommandEntity.getOutputStyle(), k -> "");
        }
        executeCommandEntity.setMessage(query);

        // 2. 构建 AgentContext
        Printer printer = new ResponseBodyPrinter(emitter, executeCommandEntity.getRequestId(), executeCommandEntity.getAgentType());
        AgentContext agentContext = AgentContext.builder()
                .requestId(executeCommandEntity.getRequestId())
                .sessionId(executeCommandEntity.getSessionId()) // Use sessionId if needed, or alias
                .printer(printer)
                .query(query)
                .task("")
                .dateInfo(DateUtil.CurrentDateInfo())
                .productFiles(new ArrayList<>())
                .taskProductFiles(new ArrayList<>())
                .sopPrompt(executeCommandEntity.getSopPrompt())
                .basePrompt(executeCommandEntity.getBasePrompt())
                .agentType(executeCommandEntity.getAgentType())
                .isStream(Objects.nonNull(executeCommandEntity.getIsStream()) ? executeCommandEntity.getIsStream() : false)
                .templateType("dataAgent".equals(executeCommandEntity.getOutputStyle()) ? "fix" : "empty")
                .build();

        // 3. 执行策略链
        String result = executeHandler.apply(executeCommandEntity, agentContext);

        log.info("ReactAgent execute result: {}", result);
    }

}

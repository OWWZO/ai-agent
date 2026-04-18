package org.wwz.ai.domain.agent.service.execute.auto1.step;

import org.wwz.ai.domain.agent.model.entity.AgentExecuteResultEntity;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import org.wwz.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import org.wwz.ai.domain.agent.service.execute.auto1.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 步骤1：MCP工具能力分析节点
 */
@Slf4j
@Service
public class Step1McpToolsAnalysisNode extends AbstractExecuteSupport {

    @Resource
    private Step2PlanningNode step2PlanningNode;

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n步骤1: MCP工具能力分析（仅分析阶段，不执行用户请求）");

        // 获取配置信息
        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.TOOL_MCP_CLIENT.getCode());

        // 获取MCP工具分析客户端
        ChatClient mcpToolsChatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        String mcpAnalysisPrompt = String.format(
                """
                        # MCP工具能力分析任务

                        ## 重要说明
                        **注意：本阶段仅进行MCP工具能力分析，不执行用户的实际请求。**\s
                         先根据mcp工具的名称或者用途进行过滤 快速筛选掉用户请求完全用不到的mcp工具 严禁过度分析完全用不到的mcp工具的信息 影响执行效率 然后再基于上述实际能用的MCP工具信息，针对用户请求进行详细的工具能力分析（仅分析，不执行
                        这是一个纯分析阶段，目的是评估可用工具的能力和适用性，为后续的执行规划提供依据。

                        ## 用户请求
                        %s

                        ## 分析要求
）：

                        ### 1. 工具匹配分析
                        - 分析每个可用工具的核心功能和适用场景
                        - 评估哪些工具能够满足用户请求的具体需求
                        - 标注每个工具的匹配度（高/中/低）

                        ### 2. 工具使用指南
                        - 提供每个相关工具的具体调用方式
                        - 说明必需的参数和可选参数
                        - 给出参数的示例值和格式要求

                        ### 3. 执行策略建议
                        - 推荐最优的工具组合方案
                        - 建议工具的调用顺序和依赖关系
                        - 提供备选方案和降级策略

                        ### 4. 注意事项
                        - 标注工具的使用限制和约束条件
                        - 提醒可能的错误情况和处理方式
                        - 给出性能优化建议

                        ### 5. 分析总结
                        - 明确说明这是分析阶段，不要执行用的任何实际操作
                        - 总结工具能力评估结果
                        - 为后续执行阶段提供建议

                        请确保分析结果准确、详细、可操作，并再次强调这仅是分析阶段。""",
                dynamicContext.getCurrentTask()
        );

        // 流式调用 LLM，前端实时看到输出
        int step = dynamicContext.getStep();
        String stepName = (step >= 1 && step < AgentExecuteResultEntity.STEP_NAMES.length)
                ? AgentExecuteResultEntity.STEP_NAMES[step] : "MCP工具分析";

        String mcpToolsAnalysis = streamLlmWithMetrics(
                mcpToolsChatClient,
                mcpAnalysisPrompt,
                dynamicContext,
                requestParameter.getSessionId(),
                step,
                stepName,
                "MCP分析");

        log.info("步骤一 MCP工具分析结果: {}", mcpToolsAnalysis);

        // 保存分析结果到上下文
        dynamicContext.setValue("mcpToolsAnalysis", mcpToolsAnalysis);

        // 更新步骤
        dynamicContext.setStep(dynamicContext.getStep() + 1);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step2PlanningNode;
    }

}

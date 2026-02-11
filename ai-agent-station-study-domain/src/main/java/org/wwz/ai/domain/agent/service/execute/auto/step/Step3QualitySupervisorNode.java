package org.wwz.ai.domain.agent.service.execute.auto.step;

import org.wwz.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import org.wwz.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import org.wwz.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 质量监督节点
 */
@Slf4j
@Service
public class Step3QualitySupervisorNode extends AbstractExecuteSupport {

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        // Reflect 阶段：反思、评估、输出供 Plan 修订的反馈（Plan-Act-Reflect 模式）
        log.info("\n阶段3: Reflect - 反思与计划修订反馈");
        
        // 从动态上下文中获取执行结果
        String executionResult = dynamicContext.getValue("executionResult");
        if (executionResult == null || executionResult.trim().isEmpty()) {
            log.warn("执行结果为空，跳过质量监督");
            return "质量监督跳过";
        }

        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.QUALITY_SUPERVISOR_CLIENT.getCode());
        
        String supervisionPrompt = String.format(aiAgentClientFlowConfigVO.getStepPrompt(), requestParameter.getMessage(), executionResult);

        // 获取对话客户端
        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        String supervisionResult = chatClient
                .prompt(supervisionPrompt)
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1024))
                .call().content();

        assert supervisionResult != null;
        var reflectMeta = parseSupervisionResult(dynamicContext, supervisionResult, requestParameter.getSessionId());
        
        dynamicContext.setValue("supervisionResult", supervisionResult);
        dynamicContext.setReflectFeedback(reflectMeta.feedback);
        
        boolean reExecute = reflectMeta.nextDecision != null && reflectMeta.nextDecision.contains("REEXECUTE");
        if (reExecute) {
            log.info("❌ Reflect 决定重跑本步");
            dynamicContext.setCurrentTask(reflectMeta.revisionHint != null ? reflectMeta.revisionHint : "根据反思反馈重新执行本步");
        } else if (supervisionResult.contains("是否通过: FAIL")) {
            log.info("❌ 质量检查未通过，需要重新执行");
            dynamicContext.setCurrentTask(reflectMeta.revisionHint != null ? reflectMeta.revisionHint : "根据质量监督的建议重新执行任务");
        } else if (supervisionResult.contains("是否通过: OPTIMIZE")) {
            log.info("🔧 质量检查建议优化，继续改进");
            dynamicContext.setCurrentTask(reflectMeta.revisionHint != null ? reflectMeta.revisionHint : "根据质量监督的建议优化执行结果");
        } else {
            log.info("✅ 质量检查通过");
            dynamicContext.setCompleted(true);
        }
        
        String stepSummary = String.format("""
                === 第 %d 步完整记录 ===
                【Plan】%s
                【Act】%s
                【Reflect】%s
                """, dynamicContext.getStep(), 
                dynamicContext.getValue("analysisResult"), 
                executionResult, 
                supervisionResult);
        
        dynamicContext.getExecutionHistory().append(stepSummary);
        if (!reExecute) {
            dynamicContext.setStep(dynamicContext.getStep() + 1);
        }
        
        // 如果任务已完成或达到最大步数，进入总结阶段
        if (dynamicContext.isCompleted() || dynamicContext.getStep() > dynamicContext.getMaxStep()) {
            return router(requestParameter, dynamicContext);
        }
        
        // 否则继续下一轮执行，返回到Step1AnalyzerNode
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        // 如果任务已完成或达到最大步数，进入总结阶段
        if (dynamicContext.isCompleted() || dynamicContext.getStep() > dynamicContext.getMaxStep()) {
            return getBean("step4LogExecutionSummaryNode");
        }
        
        // 否则返回到Step1AnalyzerNode进行下一轮分析
        return getBean("step1AnalyzerNode");
    }
    
    /** Reflect 阶段解析出的元数据，供 Plan 修订与重跑决策 */
    @Data
    private static class ReflectMeta {
        String feedback;      // 供下一轮 Plan 的反馈（质量评估+问题+建议+计划修订建议）
        String revisionHint;  // 计划修订建议，用于 setCurrentTask
        String nextDecision;  // CONTINUE_AND_REVISE / REEXECUTE_CURRENT / COMPLETE
    }

    /**
     * 解析监督结果，提取 Reflect 反馈与下一步决策
     */
    private ReflectMeta parseSupervisionResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, String supervisionResult, String sessionId) {
        int step = dynamicContext.getStep();
        log.info("\n🔍 === 第 {} 步 Reflect 结果 ===", step);
        
        ReflectMeta meta = new ReflectMeta();
        
        String[] lines = supervisionResult.split("\n");
        String currentSection = "";
        StringBuilder sectionContent = new StringBuilder();
        String revisionHint = null;
        String nextDecision = null;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (line.contains("质量评估:")) {
                sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "assessment";
                sectionContent = new StringBuilder();
                log.info("\n📊 质量评估:");
                continue;
            } else if (line.contains("问题识别:")) {
                sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "issues";
                sectionContent = new StringBuilder();
                log.info("\n⚠️ 问题识别:");
                continue;
            } else if (line.contains("改进建议:")) {
                sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "suggestions";
                sectionContent = new StringBuilder();
                log.info("\n💡 改进建议:");
                continue;
            } else if (line.contains("计划修订建议:")) {
                sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                revisionHint = line.substring(line.indexOf(":") + 1).trim();
                currentSection = "revision";
                sectionContent = new StringBuilder();
                log.info("\n📝 计划修订建议: {}", revisionHint);
                continue;
            } else if (line.contains("下一步决策:")) {
                sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                nextDecision = line.substring(line.indexOf(":") + 1).trim().toUpperCase();
                currentSection = "decision";
                sectionContent = new StringBuilder();
                log.info("\n🔄 下一步决策: {}", nextDecision);
                continue;
            } else if (line.contains("质量评分:")) {
                sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "score";
                sectionContent = new StringBuilder(line.substring(line.indexOf(":") + 1).trim());
                log.info("\n📊 质量评分: {}", sectionContent);
                continue;
            } else if (line.contains("是否通过:")) {
                sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
                currentSection = "pass";
                sectionContent = new StringBuilder(line.substring(line.indexOf(":") + 1).trim());
                log.info("\n✅ 是否通过: {}", sectionContent);
                continue;
            }
            
            if (!currentSection.isEmpty()) {
                if (sectionContent.length() > 0) sectionContent.append("\n");
                sectionContent.append(line);
            }
            switch (currentSection) {
                case "assessment" -> log.info("   📋 {}", line);
                case "issues" -> log.info("   ⚠️ {}", line);
                case "suggestions" -> log.info("   💡 {}", line);
                default -> log.info("   📝 {}", line);
            }
        }
        
        sendSupervisionSubResult(dynamicContext, currentSection, sectionContent.toString(), sessionId);
        sendSupervisionResult(dynamicContext, supervisionResult, sessionId);
        
        meta.setFeedback(supervisionResult);
        meta.setRevisionHint(revisionHint);
        meta.setNextDecision(nextDecision);
        return meta;
    }
    
    /**
     * 发送监督结果到流式输出
     */
    private void sendSupervisionResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext, 
                                     String supervisionResult, String sessionId) {
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSupervisionResult(
                dynamicContext.getStep(), supervisionResult, sessionId);
        sendSseResult(dynamicContext, result);
    }
    
    /**
     * 发送监督子结果到流式输出（细粒度标识）
     * 跳过 score、pass 等机械项，减少思考过程噪音
     */
    private void sendSupervisionSubResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                        String section, String content, String sessionId) {
        if (content.isEmpty() || section.isEmpty()) return;
        if ("score".equals(section) || "pass".equals(section)) return; // 质量评分、检查结果不单独展示
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSupervisionSubResult(
                dynamicContext.getStep(), section, content, sessionId);
        sendSseResult(dynamicContext, result);
    }

}

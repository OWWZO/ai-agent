package org.wwz.ai.domain.agent.runtime.subagent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReactImplAgent;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * 同步子 Agent 执行引擎（对标 cc-haha runAgent 同步路径 + finalizeAgentTool）。
 * 阻塞跑完嵌套 ReactImplAgent，只把结论文本回传主 Agent。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubAgentRunner {

    private final SubAgentRegistry registry;

    public SubAgentResult run(AgentContext parentContext,
                              String description,
                              String prompt,
                              String subagentType) {
        long start = System.currentTimeMillis();
        String agentId = SubAgentContextFactory.newAgentId();
        // Plan Mode：强制 Explore 画像（对标 cchaha 只读子代理）
        String effectiveType = subagentType;
        if (parentContext != null
                && parentContext.getPlanModeState() != null
                && parentContext.getPlanModeState().isPlanMode()) {
            if (StringUtils.isBlank(effectiveType)
                    || SubAgentRegistry.TYPE_GENERAL_PURPOSE.equals(effectiveType)) {
                effectiveType = SubAgentRegistry.TYPE_EXPLORE;
            }
        }
        SubAgentDefinition definition = registry.resolveOrDefault(effectiveType);

        if (parentContext == null) {
            return failed(agentId, definition, description, prompt, start, "parent AgentContext 为空");
        }
        if (StringUtils.isBlank(prompt)) {
            return failed(agentId, definition, description, prompt, start, "prompt 不能为空");
        }
        if (parentContext.getToolCollection() == null) {
            return failed(agentId, definition, description, prompt, start, "父 Agent 工具池为空");
        }

        boolean parentInPlanMode = parentContext.getPlanModeState() != null
                && parentContext.getPlanModeState().isPlanMode();
        ToolCollection childTools = SubAgentToolFilter.filter(
                parentContext.getToolCollection(), definition, parentInPlanMode);
        AgentContext childContext = SubAgentContextFactory.create(
                parentContext, prompt, description, childTools, agentId, definition.getAgentType());

        // 同步路径：父 Agent 阻塞等待，可临时 rebind 共享工具实例到子 context
        rebindTools(childTools, childContext);
        try {
            ReactImplAgent agent = new ReactImplAgent(childContext);
            agent.setName("subagent:" + definition.getAgentType());
            agent.setDescription(StringUtils.defaultIfBlank(description, definition.getAgentType()));
            if (StringUtils.isNotBlank(definition.getSystemPrompt())) {
                String base = agent.getSystemPrompt() == null ? "" : agent.getSystemPrompt();
                agent.setSystemPrompt(base + "\n\n# Subagent directive\n" + definition.getSystemPrompt());
            }
            if (definition.getMaxSteps() != null && definition.getMaxSteps() > 0) {
                agent.setMaxSteps(definition.getMaxSteps());
            }

            log.info("{} spawn sync subagent type={} id={} desc={}",
                    parentContext.getRequestId(), definition.getAgentType(), agentId, description);
            String runResult = agent.run(prompt);
            String content = finalizeContent(agent, runResult);
            int toolUseCount = countToolUses(agent);

            return SubAgentResult.builder()
                    .status(SubAgentResult.STATUS_COMPLETED)
                    .agentId(agentId)
                    .agentType(definition.getAgentType())
                    .description(description)
                    .prompt(prompt)
                    .content(content)
                    .totalToolUseCount(toolUseCount)
                    .totalDurationMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("{} sync subagent failed type={} id={}",
                    parentContext.getRequestId(), definition.getAgentType(), agentId, e);
            return failed(agentId, definition, description, prompt, start,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            rebindTools(childTools, parentContext);
            if (parentContext.getToolCollection() != null) {
                parentContext.getToolCollection().setAgentContext(parentContext);
            }
        }
    }

    private static String finalizeContent(ReactImplAgent agent, String runResult) {
        if (StringUtils.isNotBlank(runResult)
                && !runResult.startsWith("Terminated:")
                && !"No steps executed".equals(runResult)) {
            return runResult.trim();
        }
        if (agent.getMemory() == null || agent.getMemory().getMessages() == null) {
            return StringUtils.defaultString(runResult);
        }
        List<Message> messages = agent.getMemory().getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message == null || message.getRole() != RoleType.ASSISTANT) {
                continue;
            }
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                continue;
            }
            if (StringUtils.isNotBlank(message.getContent())) {
                return message.getContent().trim();
            }
        }
        return StringUtils.defaultIfBlank(runResult, "");
    }

    private static int countToolUses(ReactImplAgent agent) {
        if (agent.getMemory() == null || agent.getMemory().getMessages() == null) {
            return 0;
        }
        int count = 0;
        for (Message message : agent.getMemory().getMessages()) {
            if (message != null && message.getRole() == RoleType.TOOL) {
                count++;
            }
        }
        return count;
    }

    private static void rebindTools(ToolCollection tools, AgentContext context) {
        if (tools == null) {
            return;
        }
        tools.setAgentContext(context);
        Map<String, BaseTool> toolMap = tools.getToolMap();
        if (toolMap == null) {
            return;
        }
        for (BaseTool tool : toolMap.values()) {
            setAgentContextIfPresent(tool, context);
        }
    }

    private static void setAgentContextIfPresent(BaseTool tool, AgentContext context) {
        if (tool == null) {
            return;
        }
        try {
            Method setter = tool.getClass().getMethod("setAgentContext", AgentContext.class);
            setter.invoke(tool, context);
        } catch (NoSuchMethodException ignored) {
            // 无 agentContext 的工具跳过
        } catch (Exception e) {
            throw new IllegalStateException("rebind tool agentContext failed: " + tool.getName(), e);
        }
    }

    private static SubAgentResult failed(String agentId,
                                         SubAgentDefinition definition,
                                         String description,
                                         String prompt,
                                         long start,
                                         String errorMsg) {
        return SubAgentResult.builder()
                .status(SubAgentResult.STATUS_FAILED)
                .agentId(agentId)
                .agentType(definition == null ? null : definition.getAgentType())
                .description(description)
                .prompt(prompt)
                .content("")
                .totalToolUseCount(0)
                .totalDurationMs(System.currentTimeMillis() - start)
                .errorMsg(errorMsg)
                .build();
    }
}

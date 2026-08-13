package org.wwz.ai.domain.agent.runtime.subagent;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReactImplAgent;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.tool.ContextScopedTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.List;

/**
 * 同步子 Agent 执行引擎（对标 cc-haha runAgent 同步路径 + finalizeAgentTool）。
 * 阻塞跑完嵌套 ReactImplAgent，只把结论文本回传主 Agent。
 */
@Slf4j
@Component
public class SubAgentRunner {

    private final SubAgentRegistry registry;
    private final SubAgentConcurrencyGate concurrencyGate;

    public SubAgentRunner(SubAgentRegistry registry) {
        this(registry, SubAgentConcurrencyGate.defaults());
    }

    @Autowired
    public SubAgentRunner(SubAgentRegistry registry, SubAgentConcurrencyGate concurrencyGate) {
        this.registry = registry;
        this.concurrencyGate = concurrencyGate == null
                ? SubAgentConcurrencyGate.defaults()
                : concurrencyGate;
    }

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
        if (parentContext.getToolCollection() == null
                && parentContext.getSubAgentToolCollection() == null) {
            return failed(agentId, definition, description, prompt, start, "父 Agent 工具池为空");
        }

        try {
            SubAgentResult gated = concurrencyGate.runWithPermit(() ->
                    runUnlocked(parentContext, description, prompt, definition, agentId, start));
            if (gated == null) {
                return failed(agentId, definition, description, prompt, start,
                        "子 Agent 并发已达上限(" + concurrencyGate.getMaxConcurrent()
                                + ")，等待 " + concurrencyGate.getAcquireTimeoutSeconds() + "s 仍无空闲许可");
            }
            return gated;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed(agentId, definition, description, prompt, start, "子 Agent 等待并发许可被中断");
        }
    }

    private SubAgentResult runUnlocked(AgentContext parentContext,
                                       String description,
                                       String prompt,
                                       SubAgentDefinition definition,
                                       String agentId,
                                       long start) {
        boolean parentInPlanMode = parentContext.getPlanModeState() != null
                && parentContext.getPlanModeState().isPlanMode();
        ToolCollection parentToolCollection = parentContext.getSubAgentToolCollection() != null
                ? parentContext.getSubAgentToolCollection()
                : parentContext.getToolCollection();
        ToolCollection childTools = SubAgentToolFilter.filter(
                parentToolCollection, definition, parentInPlanMode);
        String parentToolUseId = resolveParentToolUseId(parentContext);
        if (StringUtils.isBlank(parentToolUseId)) {
            log.warn("{} subagent spawn without parentToolUseId type={} id={} — nested tools will not attach to Agent card",
                    parentContext.getRequestId(), definition.getAgentType(), agentId);
        }
        AgentContext childContext = SubAgentContextFactory.create(
                parentContext, prompt, description, childTools, agentId, definition.getAgentType(), parentToolUseId);
        // 默认每子 Agent 独占工具实例（ToolIsolation）；仅无法 fork 时才共享锁
        ContextScopedTool.bindAll(childTools, childContext);

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
        }
    }

    private static String resolveParentToolUseId(AgentContext parent) {
        if (parent == null || parent.getCurrentToolArtifactSource() == null) {
            return null;
        }
        return StringUtils.trimToNull(parent.getCurrentToolArtifactSource().getToolCallId());
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

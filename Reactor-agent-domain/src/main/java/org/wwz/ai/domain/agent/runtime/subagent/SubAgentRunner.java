package org.wwz.ai.domain.agent.runtime.subagent;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.memory.WorkingMemoryScopes;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReactImplAgent;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionAgentMailboxHub;
import org.wwz.ai.domain.agent.runtime.tool.ContextScopedTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 子 Agent 执行引擎（对标 cc-haha runAgent + finalizeAgentTool + resume）。
 * 阻塞跑完嵌套 ReactImplAgent（LLM 侧流式），只把结论文本回传主 Agent。
 * 结束后将 Memory 投影到 working_memory scope=sub:{agentId}，支持再次唤醒。
 */
@Slf4j
@Component
public class SubAgentRunner {

    /** 长 thinking / 工具静默时向前端发心跳，避免 UI 假死感（对标 onQueryProgress）。 */
    private static final long PROGRESS_HEARTBEAT_SECONDS = 30L;

    private static final ScheduledExecutorService PROGRESS_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "subagent-progress");
        t.setDaemon(true);
        return t;
    });

    private final SubAgentRegistry registry;
    private final SubAgentConcurrencyGate concurrencyGate;
    private SessionWorkingMemoryService sessionWorkingMemoryService;

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

    @Autowired(required = false)
    public void setSessionWorkingMemoryService(SessionWorkingMemoryService sessionWorkingMemoryService) {
        this.sessionWorkingMemoryService = sessionWorkingMemoryService;
    }

    public SubAgentResult run(AgentContext parentContext,
                              String description,
                              String prompt,
                              String subagentType) {
        return run(parentContext, description, prompt, subagentType, null, null, null);
    }

    /**
     * @param resumeAgentId 非空时从 working_memory sub scope hydrate 并续跑同一 agentId
     */
    public SubAgentResult run(AgentContext parentContext,
                              String description,
                              String prompt,
                              String subagentType,
                              String resumeAgentId) {
        return run(parentContext, description, prompt, subagentType, resumeAgentId, null, null);
    }

    public SubAgentResult run(AgentContext parentContext,
                              String description,
                              String prompt,
                              String subagentType,
                              String resumeAgentId,
                              RunCancellation cancellationOverride) {
        return run(parentContext, description, prompt, subagentType, resumeAgentId, cancellationOverride, null);
    }

    /**
     * @param resumeAgentId         非空时续跑
     * @param cancellationOverride  非空时覆盖子上下文取消令牌（后台 Agent 用任务级令牌）
     * @param preferredAgentId      后台预分配 agentId（非 resume 时优先使用，便于 SendMessage 寻址）
     */
    public SubAgentResult run(AgentContext parentContext,
                              String description,
                              String prompt,
                              String subagentType,
                              String resumeAgentId,
                              RunCancellation cancellationOverride,
                              String preferredAgentId) {
        return run(parentContext, description, prompt, subagentType, resumeAgentId,
                cancellationOverride, preferredAgentId, null);
    }

    /**
     * @param explicitParentToolUseId 调用方在父线程捕获的 Agent 工具 toolCallId。
     *                                异步派发必须显式传入，不能依赖后台线程的 ThreadLocal。
     */
    public SubAgentResult run(AgentContext parentContext,
                              String description,
                              String prompt,
                              String subagentType,
                              String resumeAgentId,
                              RunCancellation cancellationOverride,
                              String preferredAgentId,
                              String explicitParentToolUseId) {
        long start = System.currentTimeMillis();
        boolean resume = StringUtils.isNotBlank(resumeAgentId);
        String agentId;
        if (resume) {
            agentId = resumeAgentId.trim();
        } else if (StringUtils.isNotBlank(preferredAgentId)) {
            agentId = preferredAgentId.trim();
        } else {
            agentId = SubAgentContextFactory.newAgentId();
        }
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

        if (resume && sessionWorkingMemoryService == null) {
            return failed(agentId, definition, description, prompt, start,
                    "无法唤醒子 Agent：SessionWorkingMemoryService 未注入");
        }

        try {
            SubAgentResult gated = concurrencyGate.runWithPermit(() ->
                    runUnlocked(parentContext, description, prompt, definition, agentId, start, resume,
                            cancellationOverride, explicitParentToolUseId));
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
                                       long start,
                                       boolean resume,
                                       RunCancellation cancellationOverride,
                                       String explicitParentToolUseId) {
        boolean parentInPlanMode = parentContext.getPlanModeState() != null
                && parentContext.getPlanModeState().isPlanMode();
        ToolCollection parentToolCollection = parentContext.getSubAgentToolCollection() != null
                ? parentContext.getSubAgentToolCollection()
                : parentContext.getToolCollection();
        ToolCollection childTools = SubAgentToolFilter.filter(
                parentToolCollection, definition, parentInPlanMode);
        String parentToolUseId = StringUtils.isNotBlank(explicitParentToolUseId)
                ? explicitParentToolUseId.trim()
                : resolveParentToolUseId(parentContext);
        if (StringUtils.isBlank(parentToolUseId)) {
            log.warn("{} subagent spawn without parentToolUseId type={} id={} — nested tools will not attach to Agent card",
                    parentContext.getRequestId(), definition.getAgentType(), agentId);
        }
        AgentContext childContext = SubAgentContextFactory.create(
                parentContext, prompt, description, childTools, agentId, definition.getAgentType(), parentToolUseId);
        // 后台 Agent：用任务级取消令牌，TaskStop 不误伤主 run；同步派发仍共享父令牌
        if (cancellationOverride != null) {
            childContext.setRunCancellation(cancellationOverride);
        }
        // 主→子 inject 邮箱（SendMessage / 用户指导）；与父 run 的 inject 队列隔离
        String sessionKey = StringUtils.defaultIfBlank(parentContext.getSessionId(), parentContext.getRequestId());
        childContext.bindPendingInjectQueue(SessionAgentMailboxHub.queue(sessionKey, agentId));

        String memoryScope = WorkingMemoryScopes.forSubAgent(agentId);
        childContext.setMemoryScope(memoryScope);

        // resume：每次运行需要唯一 requestId，否则 working_memory persist 幂等跳过
        if (resume) {
            String resumeRequestId = buildResumeRequestId(parentContext.getRequestId(), agentId);
            childContext.setRequestId(resumeRequestId);
            List<Message> prior = sessionWorkingMemoryService.loadReadyMessages(
                    parentContext.getSessionId(), memoryScope, resumeRequestId);
            if (prior == null || prior.isEmpty()) {
                return failed(agentId, definition, description, prompt, start,
                        "无法唤醒子 Agent：未找到 agentId=" + agentId + " 的工作记忆（可能已过期或从未成功结束）");
            }
            childContext.setWorkingMemoryMessages(new ArrayList<>(prior));
            log.info("{} resume subagent type={} id={} priorMsgs={}",
                    parentContext.getRequestId(), definition.getAgentType(), agentId, prior.size());
        } else {
            childContext.setWorkingMemoryMessages(null);
        }

        // 默认每子 Agent 独占工具实例（ToolIsolation）；仅无法 fork 时才共享锁
        ContextScopedTool.bindAll(childTools, childContext);

        SessionAgentMailboxHub.markActive(sessionKey, agentId, true);
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

            log.info("{} {} streaming subagent type={} id={} desc={}",
                    parentContext.getRequestId(),
                    resume ? "resume" : "spawn",
                    definition.getAgentType(), agentId, description);
            AtomicBoolean finished = new AtomicBoolean(false);
            ScheduledFuture<?> heartbeat = PROGRESS_SCHEDULER.scheduleAtFixedRate(
                    () -> emitSubAgentProgress(childContext, agentId, definition.getAgentType(), description, start, finished),
                    PROGRESS_HEARTBEAT_SECONDS,
                    PROGRESS_HEARTBEAT_SECONDS,
                    TimeUnit.SECONDS);
            try {
                String runResult = agent.run(prompt);
                finished.set(true);
                String content = finalizeContent(agent, runResult);
                emitSubAgentFinalReply(childContext, content);
                int toolUseCount = countToolUses(agent);
                persistSubWorkingMemory(childContext, agent, definition);

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
            } finally {
                finished.set(true);
                heartbeat.cancel(false);
            }
        } catch (Exception e) {
            log.error("{} streaming subagent failed type={} id={}",
                    parentContext.getRequestId(), definition.getAgentType(), agentId, e);
            return failed(agentId, definition, description, prompt, start,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            SessionAgentMailboxHub.markActive(sessionKey, agentId, false);
        }
    }

    private void persistSubWorkingMemory(AgentContext childContext,
                                         ReactImplAgent agent,
                                         SubAgentDefinition definition) {
        if (sessionWorkingMemoryService == null || childContext == null || agent == null) {
            return;
        }
        try {
            List<Message> delta = agent.exportWorkingMemoryDelta();
            if (delta == null || delta.isEmpty()) {
                return;
            }
            Long runId = childContext.getAgentRunState() == null
                    ? null
                    : childContext.getAgentRunState().getRunId();
            String entry = "sub_" + StringUtils.defaultIfBlank(
                    definition == null ? null : definition.getAgentType(), "agent");
            sessionWorkingMemoryService.persistTurn(
                    childContext.getSessionId(),
                    childContext.resolveMemoryScope(),
                    childContext.getRequestId(),
                    runId,
                    entry,
                    delta);
            log.info("{} persisted sub working memory scope={} msgs={}",
                    childContext.getRequestId(), childContext.resolveMemoryScope(), delta.size());
        } catch (Exception e) {
            log.warn("{} persist sub working memory failed: {}",
                    childContext.getRequestId(), e.getMessage());
        }
    }

    private static String buildResumeRequestId(String parentRequestId, String agentId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String base = StringUtils.defaultString(parentRequestId) + ":sub:" + agentId + ":r" + suffix;
        if (base.length() <= 64) {
            return base;
        }
        // request_id 列宽 64：过长时用短形态
        String shortId = "sr:" + agentId + ":" + suffix;
        return shortId.length() <= 64 ? shortId : shortId.substring(0, 64);
    }

    private static void emitSubAgentFinalReply(AgentContext childContext, String content) {
        if (childContext == null
                || childContext.getPrinter() == null
                || StringUtils.isBlank(content)
                || StringUtils.isBlank(childContext.getParentToolUseId())) {
            return;
        }
        try {
            childContext.getPrinter().send("result", content);
        } catch (Exception e) {
            log.debug("subagent final reply skipped id={}: {}", childContext.getSubAgentId(), e.getMessage());
        }
    }

    private static void emitSubAgentProgress(AgentContext childContext,
                                             String agentId,
                                             String agentType,
                                             String description,
                                             long startMs,
                                             AtomicBoolean finished) {
        if (finished.get() || childContext == null || childContext.getPrinter() == null) {
            return;
        }
        if (childContext.isRunCancelled()) {
            return;
        }
        try {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            // 与前端 agentRuntime 契约对齐：kind=heartbeat|text|line
            payload.put("kind", "heartbeat");
            payload.put("phase", "working");
            payload.put("agentId", agentId);
            payload.put("agentType", agentType);
            payload.put("description", description);
            payload.put("elapsedMs", System.currentTimeMillis() - startMs);
            payload.put("status", "running");
            // parentToolUseId / subAgent* 由 SubAgentPrinter 自动注入
            childContext.getPrinter().send("subagent_progress", payload);
        } catch (Exception e) {
            log.debug("subagent progress heartbeat skipped id={}: {}", agentId, e.getMessage());
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

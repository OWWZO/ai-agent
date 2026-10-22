package org.wwz.ai.domain.agent.runtime.subagent;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.SubAgentSessionPort;
import org.wwz.ai.domain.agent.adapter.port.SubAgentSessionStreamPort;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.memory.WorkingMemoryScopes;
import org.wwz.ai.domain.agent.runtime.CuteRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReactImplAgent;
import org.wwz.ai.domain.agent.runtime.artifact.TaskSummaryArtifactProtocol;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactBinding;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.TaskSummaryResult;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTaskRegistry;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionAgentMailboxHub;
import org.wwz.ai.domain.agent.runtime.tool.ContextScopedTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 子 Agent 执行引擎。
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

    public boolean hasPersistedMemory(String sessionId, String agentId) {
        if (sessionWorkingMemoryService == null
                || StringUtils.isBlank(sessionId)
                || StringUtils.isBlank(agentId)) {
            return false;
        }
        List<Message> prior = sessionWorkingMemoryService.loadReadyMessages(
                sessionId,
                WorkingMemoryScopes.forSubAgent(agentId),
                SubAgentContextFactory.newChildRequestId(agentId));
        return prior != null && !prior.isEmpty();
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
        String effectiveType = subagentType;
        SubAgentDefinition definition = registry.resolveOrDefault(effectiveType);
        // 确定性子会话 ID：续跑复用同一个，首跑由 agentId 派生（见 SubAgentContextFactory#newChildSessionId）
        String childSessionId = SubAgentContextFactory.newChildSessionId(agentId);

        if (parentContext == null) {
            return failed(agentId, childSessionId, definition, description, prompt, start, "parent AgentContext 为空");
        }
        if (StringUtils.isBlank(prompt)) {
            return failed(agentId, childSessionId, definition, description, prompt, start, "prompt 不能为空");
        }
        if (parentContext.getToolCollection() == null
                && parentContext.getSubAgentToolCollection() == null) {
            return failed(agentId, childSessionId, definition, description, prompt, start, "父 Agent 工具池为空");
        }

        if (resume && sessionWorkingMemoryService == null) {
            return failed(agentId, childSessionId, definition, description, prompt, start,
                    "无法唤醒子 Agent：SessionWorkingMemoryService 未注入");
        }

        try {
            SubAgentResult gated = concurrencyGate.runWithPermit(() ->
                    runUnlocked(parentContext, description, prompt, definition, agentId, childSessionId, start, resume,
                            cancellationOverride, explicitParentToolUseId));
            if (gated == null) {
                return failed(agentId, childSessionId, definition, description, prompt, start,
                        "子 Agent 并发已达上限(" + concurrencyGate.getMaxConcurrent()
                                + ")，等待 " + concurrencyGate.getAcquireTimeoutSeconds() + "s 仍无空闲许可");
            }
            return gated;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed(agentId, childSessionId, definition, description, prompt, start, "子 Agent 等待并发许可被中断");
        }
    }

    private SubAgentResult runUnlocked(AgentContext parentContext,
                                       String description,
                                       String prompt,
                                       SubAgentDefinition definition,
                                       String agentId,
                                       String childSessionId,
                                       long start,
                                       boolean resume,
                                       RunCancellation cancellationOverride,
                                       String explicitParentToolUseId) {
        ToolCollection parentToolCollection = parentContext.getSubAgentToolCollection() != null
                ? parentContext.getSubAgentToolCollection()
                : parentContext.getToolCollection();
        ToolCollection childTools = SubAgentToolFilter.filter(parentToolCollection, definition);
        String parentToolUseId = StringUtils.isNotBlank(explicitParentToolUseId)
                ? explicitParentToolUseId.trim()
                : resolveParentToolUseId(parentContext);
        if (StringUtils.isBlank(parentToolUseId)) {
            log.warn("{} subagent spawn without parentToolUseId type={} id={} — nested tools will not attach to Agent card",
                    parentContext.getRequestId(), definition.getAgentType(), agentId);
        }
        // 先落库后运行：子会话身份行必须在 child run 启动前写入（对齐 ZCode「持久化早于 Spawned 广播」）。
        // 落库失败即中止，避免无主运行与不可查询的孤儿会话；端口缺失时降级放行（仅告警）。
        if (!ensureChildSessionPersisted(parentContext, childSessionId, description, definition)) {
            return failed(agentId, childSessionId, definition, description, prompt, start,
                    "子会话身份落库失败，已中止子 Agent 启动");
        }

        // Phase 3 SSE 隔离：开关开启且端口可用时，子事件写子会话自己的投影流，
        // 父流只留 subagent_start / subagent_end 两条自包含帧。
        // 隔离失败（端口返回 null）时回退旧的 SubAgentPrinter 透传父流行为，保证不破线。
        String childRequestId = SubAgentContextFactory.newChildRequestId(agentId);
        Printer isolatedChildPrinter = openIsolatedChildStream(
                parentContext, childSessionId, childRequestId, prompt);
        if (isolatedChildPrinter != null) {
            emitParentSubAgentFrame(parentContext, "subagent_start", childSessionId, parentToolUseId,
                    agentId, definition.getAgentType(), description, "running", null);
        }

        AgentContext childContext = SubAgentContextFactory.create(
                parentContext, prompt, description, childTools, agentId, definition.getAgentType(), parentToolUseId,
                isolatedChildPrinter, isolatedChildPrinter == null ? null : childRequestId);
        // 后台 Agent：用任务级取消令牌，TaskStop 不误伤主 run；同步派发仍共享父令牌
        if (cancellationOverride != null) {
            childContext.setRunCancellation(cancellationOverride);
        }
        // 子 Agent 记账落独立 run：LLM / tool / artifact 挂到 childSessionId 的 run，
        // 父 run 的 llm_call_count / tool_call_count 不再包含子代理调用。
        // recorder 共享同一无状态实例，run 身份由 AgentRunState 隔离。
        ExecutionLedgerRunSupport.initializeSubAgentRun(
                childContext.getExecutionRecorder(),
                childContext,
                ExecutionLedgerConstants.ENTRY_AGENT_SUBAGENT,
                prompt);
        // 主→子 inject 邮箱（SendMessage / 用户指导）；与父 run 的 inject 队列隔离
        String sessionKey = StringUtils.defaultIfBlank(parentContext.getSessionId(), parentContext.getRequestId());
        childContext.bindPendingInjectQueue(SessionAgentMailboxHub.queue(sessionKey, agentId));

        String memoryScope = WorkingMemoryScopes.forSubAgent(agentId);
        childContext.setMemoryScope(memoryScope);

        // resume：每次运行需要唯一 requestId，否则 working_memory persist 幂等跳过
        if (resume) {
            if (isSubAgentStillRunning(parentContext, sessionKey, agentId)) {
                return failed(agentId, childSessionId, definition, description, prompt, start,
                        "子 Agent 仍在运行，请用 TaskOutput 等待后再 resume",
                        null, false);
            }
            // 与首跑统一短 requestId；父 requestId 仅用于日志
            // 隔离流路径下 requestId 已在建流时定死，不能重新分配，否则投影与 ledger run 对不上；
            // 仅旧透传路径仍按原逻辑换新 requestId，保证 working_memory persist 不被幂等跳过。
            if (isolatedChildPrinter == null) {
                childContext.setRequestId(SubAgentContextFactory.newChildRequestId(agentId));
            }
            String resumeRequestId = childContext.getRequestId();
            List<Message> prior = sessionWorkingMemoryService.loadReadyMessages(
                    parentContext.getSessionId(), memoryScope, resumeRequestId);
            if (prior == null || prior.isEmpty()) {
                return failed(agentId, childSessionId, definition, description, prompt, start,
                        "无法唤醒子 Agent：未找到 agentId=" + agentId + " 的工作记忆（可能已过期或从未成功结束）",
                        null, false);
            }
            childContext.setWorkingMemoryMessages(new ArrayList<>(prior));
            log.info("{} resume subagent type={} id={} priorMsgs={} childRequestId={} parentRequestId={}",
                    parentContext.getRequestId(), definition.getAgentType(), agentId, prior.size(),
                    resumeRequestId, parentContext.getRequestId());
        } else {
            childContext.setWorkingMemoryMessages(null);
            log.info("{} spawn subagent type={} id={} childRequestId={} parentRequestId={}",
                    parentContext.getRequestId(), definition.getAgentType(), agentId,
                    childContext.getRequestId(), parentContext.getRequestId());
        }

        // 默认每子 Agent 独占工具实例（ToolIsolation）；仅无法 fork 时才共享锁
        ContextScopedTool.bindAll(childTools, childContext);

        SessionAgentMailboxHub.markActive(sessionKey, agentId, true);
        ReactImplAgent agent = null;
        // 子 run 终态收口：三条返回路径 + 异常路径都要收敛到同一个 finally，避免漏写导致 run 永挂 RUNNING。
        java.util.concurrent.atomic.AtomicInteger childRunStatus =
                new java.util.concurrent.atomic.AtomicInteger(ExecutionLedgerConstants.STATUS_FAILED);
        java.util.concurrent.atomic.AtomicReference<String> childRunSummary =
                new java.util.concurrent.atomic.AtomicReference<>("");
        try {
            agent = new ReactImplAgent(childContext);
            agent.setName("subagent:" + definition.getAgentType());
            agent.setDescription(StringUtils.defaultIfBlank(description, definition.getAgentType()));
            // 整段替换为子 Agent 底座（面向协调者），不复用 ReAct 用户向 system / USER_FACING
            agent.applySubAgentSystemPrompt(definition.getSystemPrompt());
            // 子 agent 与主 agent 复用同一套行模型：正文 delta 同样走 agent_stream，
            // 由隔离子会话流（openIsolatedChildStream）承载、子会话面板用同一条 combineData
            // 链路消费；透传回退路径由 SubAgentPrinter 打嵌套标签、前端挂嵌套时间线。
            // "终答回给协调者"是消费语义，不改变展示语义。
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
                boolean thinkFailed = isThinkFailed(agent, runResult);
                int toolUseCount = countToolUses(agent);
                // 成功/think 失败都落库，便于主 Agent resume
                boolean memoryPersisted = persistSubWorkingMemory(childContext, parentContext, agent, definition);
                String content;
                if (thinkFailed) {
                    String reason = StringUtils.defaultIfBlank(
                            agent.getThinkFailureReason(), extractThinkFailedReason(runResult));
                    content = buildResumeHintContent(
                            "Terminated: LLM think failed: " + reason, agentId, memoryPersisted);
                    content = emitSubAgentFinalReply(childContext, agent, content);
                    return SubAgentResult.builder()
                            .status(SubAgentResult.STATUS_FAILED)
                            .agentId(agentId)
                            .childSessionId(childSessionId)
                            .agentType(definition.getAgentType())
                            .description(description)
                            .prompt(prompt)
                            .content(content)
                            .totalToolUseCount(toolUseCount)
                            .totalDurationMs(System.currentTimeMillis() - start)
                            .errorMsg("LLM think failed: " + reason
                                    + "；请用 SendMessage(to=\"" + agentId + "\", message=…) 续跑"
                                    + (memoryPersisted ? "" : "（注意：工作记忆未落库，resume 可能失败）"))
                            .memoryPersisted(memoryPersisted)
                            .build();
                }
                content = finalizeContent(agent, runResult);
                content = emitSubAgentFinalReply(childContext, agent, content);
                childRunStatus.set(ExecutionLedgerConstants.STATUS_SUCCESS);
                childRunSummary.set(content);
                return SubAgentResult.builder()
                        .status(SubAgentResult.STATUS_COMPLETED)
                        .agentId(agentId)
                        .childSessionId(childSessionId)
                        .agentType(definition.getAgentType())
                        .description(description)
                        .prompt(prompt)
                        .content(content)
                        .totalToolUseCount(toolUseCount)
                        .totalDurationMs(System.currentTimeMillis() - start)
                        .memoryPersisted(memoryPersisted)
                        .build();
            } finally {
                finished.set(true);
                heartbeat.cancel(false);
            }
        } catch (Exception e) {
            log.error("{} streaming subagent failed type={} id={}",
                    parentContext.getRequestId(), definition.getAgentType(), agentId, e);
            boolean memoryPersisted = persistSubWorkingMemory(childContext, parentContext, agent, definition);
            String err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String content = buildResumeHintContent(
                    "Terminated: subagent failed: " + err, agentId, memoryPersisted);
            content = emitSubAgentFinalReply(childContext, agent, content);
            childRunStatus.set(ExecutionLedgerConstants.STATUS_FAILED);
            childRunSummary.set(err);
            return failed(agentId, childSessionId, definition, description, prompt, start, err, content, memoryPersisted);
        } finally {
            // 子 run 终态收口（含取消：/stop 走异常或正常路径都已覆盖）
            try {
                if (childContext.isRunCancelled()) {
                    childRunStatus.set(ExecutionLedgerConstants.STATUS_STOPPED);
                }
                ExecutionLedgerRunSupport.finishRun(
                        childContext,
                        childRunStatus.get(),
                        childRunSummary.get(),
                        null,
                        null);
            } catch (Exception ledgerError) {
                // 子 run 记账失败不得影响子 Agent 结果回传
                log.warn("{} subagent run finish failed id={}: {}",
                        parentContext.getRequestId(), agentId, ledgerError.getMessage());
            }
            // Phase 3：父流补一条自包含的 subagent_end，然后关闭子会话投影流。
            if (isolatedChildPrinter != null) {
                emitParentSubAgentFrame(parentContext, "subagent_end", childSessionId, parentToolUseId,
                        agentId, definition.getAgentType(), description,
                        childContext.isRunCancelled() ? "stopped" : resolveEndStatus(childRunStatus.get()),
                        resolveEndSummary(childRunStatus, childRunSummary));
                closeIsolatedChildStream(parentContext, childSessionId, isolatedChildPrinter);
            }
            SessionAgentMailboxHub.markActive(sessionKey, agentId, false);
        }
    }

    private static String resolveEndStatus(int runStatus) {
        return runStatus == ExecutionLedgerConstants.STATUS_SUCCESS ? "completed" : "failed";
    }

    private static String resolveEndSummary(java.util.concurrent.atomic.AtomicInteger runStatus,
                                            java.util.concurrent.atomic.AtomicReference<String> runSummary) {
        if (runStatus.get() == ExecutionLedgerConstants.STATUS_SUCCESS) {
            return runSummary.get();
        }
        return runSummary.get();
    }

    /**
     * 打开子会话独立投影流（Phase 3）。
     * <p>
     * 只有开关开启、端口可用且拿到 Printer 时才算隔离成功；否则返回 null，
     * 调用方回退到旧的 SubAgentPrinter 透传父流行为。
     */
    private static Printer openIsolatedChildStream(AgentContext parentContext,
                                                   String childSessionId,
                                                   String childRequestId,
                                                   String prompt) {
        CuteRuntimeDependencies dependencies = parentContext == null
                ? null
                : parentContext.getRuntimeDependencies();
        if (dependencies == null || !dependencies.isSubAgentIsolatedSessionEnabled()) {
            return null;
        }
        SubAgentSessionStreamPort port = dependencies.getOptionalSubAgentSessionStreamPort();
        if (port == null) {
            log.warn("{} subagent isolated session port absent, fallback to parent passthrough childSessionId={}",
                    parentContext.getRequestId(), childSessionId);
            return null;
        }
        try {
            Printer printer = port.openChildSessionStream(
                    childSessionId,
                    parentContext.getUserId(),
                    childRequestId,
                    prompt);
            if (printer == null) {
                log.warn("{} open child session stream returned null, fallback childSessionId={}",
                        parentContext.getRequestId(), childSessionId);
            }
            return printer;
        } catch (Exception e) {
            log.error("{} open child session stream failed childSessionId={}, fallback",
                    parentContext.getRequestId(), childSessionId, e);
            return null;
        }
    }

    /**
     * 关闭子会话投影流。仅隔离路径调用；异常不影响主流程。
     */
    private static void closeIsolatedChildStream(AgentContext parentContext,
                                                 String childSessionId,
                                                 Printer isolatedChildPrinter) {
        if (isolatedChildPrinter == null) {
            return;
        }
        CuteRuntimeDependencies dependencies = parentContext == null
                ? null
                : parentContext.getRuntimeDependencies();
        SubAgentSessionStreamPort port = dependencies == null
                ? null
                : dependencies.getOptionalSubAgentSessionStreamPort();
        if (port == null) {
            return;
        }
        try {
            port.closeChildSessionStream(childSessionId, isolatedChildPrinter);
        } catch (Exception e) {
            log.warn("{} close child session stream failed childSessionId={}: {}",
                    parentContext == null ? "-" : parentContext.getRequestId(),
                    childSessionId, e.getMessage());
        }
    }

    /**
     * 父流子代理摘要帧（{@code subagent_start} / {@code subagent_end}）。
     * 只在隔离路径下发，复用既有 messageType 通道，不造新协议；
     * 帧内容自包含，不依赖子会话流。
     */
    private static void emitParentSubAgentFrame(AgentContext parentContext,
                                                String messageType,
                                                String childSessionId,
                                                String parentToolUseId,
                                                String agentId,
                                                String agentType,
                                                String description,
                                                String status,
                                                String summary) {
        Printer parentPrinter = parentContext == null ? null : parentContext.getPrinter();
        if (parentPrinter == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("delegationId", agentId);
            payload.put("childSessionId", childSessionId);
            payload.put("parentToolCallId", parentToolUseId);
            payload.put("subAgentId", agentId);
            payload.put("subagentType", agentType);
            payload.put("description", description);
            payload.put("status", status);
            if (summary != null) {
                payload.put("summary", summary);
            }
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put(SubAgentPrinter.KEY_PARENT_TOOL_USE_ID, parentToolUseId);
            extra.put(SubAgentPrinter.KEY_SUB_AGENT_ID, agentId);
            extra.put(SubAgentPrinter.KEY_SUB_AGENT_TYPE, agentType);
            extra.put("childSessionId", childSessionId);
            parentPrinter.send(null, messageType, payload, extra, null, true);
        } catch (Exception e) {
            log.debug("{} emit {} skipped id={}: {}",
                    parentContext.getRequestId(), messageType, agentId, e.getMessage());
        }
    }

    /**
     * 确保子会话身份行已落库（幂等）。端口缺失时降级放行并告警，不阻断子 Agent。
     *
     * @return true 表示可以继续启动 child run
     */
    private static boolean ensureChildSessionPersisted(AgentContext parentContext,
                                                       String childSessionId,
                                                       String description,
                                                       SubAgentDefinition definition) {
        SubAgentSessionPort port = parentContext == null || parentContext.getRuntimeDependencies() == null
                ? null
                : parentContext.getRuntimeDependencies().getOptionalSubAgentSessionPort();
        if (port == null) {
            log.warn("{} subagent child session port absent, skip persist childSessionId={}",
                    parentContext == null ? "-" : parentContext.getRequestId(), childSessionId);
            return true;
        }
        String title = StringUtils.defaultIfBlank(description,
                definition == null ? null : definition.getAgentType());
        try {
            port.ensureChildSession(
                    childSessionId,
                    parentContext.getSessionId(),
                    parentContext.getUserId(),
                    title);
            return true;
        } catch (Exception e) {
            log.error("{} subagent child session persist failed childSessionId={} parentSessionId={}",
                    parentContext.getRequestId(), childSessionId, parentContext.getSessionId(), e);
            return false;
        }
    }

    /**
     * 子 Agent 工作记忆落库。
     * <p>
     * 作用域键仍是 <b>父会话 sessionId</b>：{@code sub:{agentId}} 侧链是 resume 的承载，
     * 而 {@link #hasPersistedMemory} / resume 分支都按父会话寻址。若跟着子 sessionId 走，
     * 唤醒时会读到空投影。子会话自己的 ledger run 与这条记忆侧链互不相干。
     *
     * @return true 若成功写入至少一条投影
     */
    private boolean persistSubWorkingMemory(AgentContext childContext,
                                            AgentContext parentContext,
                                            ReactImplAgent agent,
                                            SubAgentDefinition definition) {
        if (sessionWorkingMemoryService == null || childContext == null || agent == null) {
            log.warn("{} skip persist sub WM: service/agent missing scope={}",
                    childContext == null ? "-" : childContext.getRequestId(),
                    childContext == null ? "-" : childContext.resolveMemoryScope());
            return false;
        }
        String workingMemorySessionId = parentContext == null
                ? childContext.getSessionId()
                : StringUtils.defaultIfBlank(parentContext.getSessionId(), childContext.getSessionId());
        try {
            List<Message> delta = agent.exportWorkingMemoryDelta();
            List<Message> toPersist = delta;
            boolean fullSnapshot = false;
            if (toPersist == null || toPersist.isEmpty()) {
                // mid-run compact 后 delta 可能为空：全量兜底，避免 resume 空记忆
                List<Message> all = agent.getMemory() == null ? null : agent.getMemory().getMessages();
                if (all == null || all.isEmpty()) {
                    log.info("{} skip persist sub WM empty memory scope={} agentId={}",
                            childContext.getRequestId(), childContext.resolveMemoryScope(),
                            childContext.getSubAgentId());
                    return false;
                }
                toPersist = new ArrayList<>(all);
                fullSnapshot = true;
            }
            Long runId = childContext.getAgentRunState() == null
                    ? null
                    : childContext.getAgentRunState().getRunId();
            String entry = "sub_" + StringUtils.defaultIfBlank(
                    definition == null ? null : definition.getAgentType(), "agent");
            String scope = childContext.resolveMemoryScope();
            if (fullSnapshot) {
                String snapRequestId = SubAgentContextFactory.newChildRequestId(childContext.getSubAgentId());
                sessionWorkingMemoryService.replaceReadyProjection(
                        workingMemorySessionId,
                        scope,
                        snapRequestId,
                        toPersist);
                log.info("{} persisted sub working memory scope={} msgs={} fullSnapshot=true "
                                + "requestId={} persistOk=true",
                        childContext.getRequestId(), scope, toPersist.size(), snapRequestId);
            } else {
                sessionWorkingMemoryService.persistTurn(
                        workingMemorySessionId,
                        scope,
                        childContext.getRequestId(),
                        runId,
                        entry,
                        toPersist);
                log.info("{} persisted sub working memory scope={} msgs={} fullSnapshot=false "
                                + "requestId={} persistOk=true",
                        childContext.getRequestId(), scope, toPersist.size(), childContext.getRequestId());
            }
            return true;
        } catch (Exception e) {
            log.warn("{} persist sub working memory failed persistOk=false: {}",
                    childContext.getRequestId(), e.getMessage());
            return false;
        }
    }

    private static boolean isSubAgentStillRunning(AgentContext parent,
                                                 String sessionKey,
                                                 String agentId) {
        if (StringUtils.isBlank(agentId)) {
            return false;
        }
        if (SessionAgentMailboxHub.isActive(sessionKey, agentId)) {
            return true;
        }
        RuntimeBackgroundTaskRegistry registry = parent == null ? null : parent.getBackgroundTasks();
        if (registry == null) {
            return false;
        }
        for (RuntimeBackgroundTask task : registry.listRunning()) {
            if (task != null && agentId.equals(StringUtils.trimToEmpty(task.getAgentId()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isThinkFailed(ReactImplAgent agent, String runResult) {
        if (agent != null && agent.getState() == AgentState.ERROR) {
            return true;
        }
        return StringUtils.isNotBlank(runResult) && runResult.startsWith("Terminated: LLM think failed");
    }

    private static String extractThinkFailedReason(String runResult) {
        if (StringUtils.isBlank(runResult)) {
            return "unknown";
        }
        String prefix = "Terminated: LLM think failed:";
        if (runResult.startsWith(prefix)) {
            return runResult.substring(prefix.length()).trim();
        }
        return runResult.trim();
    }

    private static String buildResumeHintContent(String head, String agentId, boolean memoryPersisted) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(head)) {
            sb.append(head.trim());
        }
        sb.append("\n\n请使用 SendMessage(to=\"")
                .append(StringUtils.defaultString(agentId))
                .append("\", message=…) 唤醒该子 Agent 继续任务");
        if (memoryPersisted) {
            sb.append("（工作记忆已尽量保留）。");
        } else {
            sb.append("（注意：本轮工作记忆未成功落库，resume 可能失败）。");
        }
        return sb.toString();
    }

    /**
     * 子 Agent 与主 Agent 共用终答收口协议，但不结束父 run。
     * 子上下文共享 artifact registry，因此只挑出当前子 Agent 实际调用过的工具产物。
     */
    private static String emitSubAgentFinalReply(
            AgentContext childContext,
            ReactImplAgent agent,
            String content
    ) {
        String fallback = StringUtils.defaultString(content);
        if (childContext == null
                || childContext.getPrinter() == null
                || StringUtils.isBlank(childContext.getParentToolUseId())) {
            return fallback;
        }
        try {
            List<ToolArtifactBinding> visibleArtifacts = collectSubAgentVisibleArtifacts(childContext, agent);
            TaskSummaryResult result = TaskSummaryArtifactProtocol.resolveForDelivery(
                    fallback,
                    visibleArtifacts
            );
            String summary = StringUtils.defaultString(result.getTaskSummary());
            childContext.getPrinter().send(
                    "result",
                    TaskSummaryArtifactProtocol.toEventPayload(result, visibleArtifacts)
            );
            return summary;
        } catch (Exception e) {
            log.debug("subagent final reply skipped id={}: {}", childContext.getSubAgentId(), e.getMessage());
            return fallback;
        }
    }

    private static List<ToolArtifactBinding> collectSubAgentVisibleArtifacts(
            AgentContext childContext,
            ReactImplAgent agent
    ) {
        if (childContext == null || agent == null || agent.getMemory() == null
                || agent.getMemory().getMessages() == null) {
            return List.of();
        }

        Set<String> toolCallIds = new LinkedHashSet<>();
        for (Message message : agent.getMemory().getMessages()) {
            if (message == null || message.getToolCalls() == null) {
                continue;
            }
            for (ToolCall toolCall : message.getToolCalls()) {
                if (toolCall != null && StringUtils.isNotBlank(toolCall.getId())) {
                    toolCallIds.add(toolCall.getId());
                }
            }
        }
        if (toolCallIds.isEmpty()) {
            return List.of();
        }

        Set<ToolArtifactBinding> bindings = new LinkedHashSet<>();
        for (String toolCallId : toolCallIds) {
            for (ToolArtifactBinding binding : childContext.getArtifactBindingsByToolCallId(toolCallId)) {
                if (binding != null && !binding.isInternalFile() && binding.getFile() != null) {
                    bindings.add(binding);
                }
            }
        }
        return new ArrayList<>(bindings);
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
        if (StringUtils.isNotBlank(runResult) && !isNonUserFacingRunResult(runResult)) {
            return runResult.trim();
        }
        if (agent == null || agent.getMemory() == null || agent.getMemory().getMessages() == null) {
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

    private static boolean isNonUserFacingRunResult(String runResult) {
        if (StringUtils.isBlank(runResult)) {
            return true;
        }
        String text = runResult.trim();
        return text.startsWith("Terminated:")
                || "No steps executed".equals(text)
                || "Thinking complete - no action needed".equals(text);
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
                                         String childSessionId,
                                         SubAgentDefinition definition,
                                         String description,
                                         String prompt,
                                         long start,
                                         String errorMsg) {
        return failed(agentId, childSessionId, definition, description, prompt, start, errorMsg, "", null);
    }

    private static SubAgentResult failed(String agentId,
                                         String childSessionId,
                                         SubAgentDefinition definition,
                                         String description,
                                         String prompt,
                                         long start,
                                         String errorMsg,
                                         String content,
                                         Boolean memoryPersisted) {
        return SubAgentResult.builder()
                .status(SubAgentResult.STATUS_FAILED)
                .agentId(agentId)
                .childSessionId(childSessionId)
                .agentType(definition == null ? null : definition.getAgentType())
                .description(description)
                .prompt(prompt)
                .content(StringUtils.defaultString(content))
                .totalToolUseCount(0)
                .totalDurationMs(System.currentTimeMillis() - start)
                .errorMsg(errorMsg)
                .memoryPersisted(memoryPersisted)
                .build();
    }
}

package org.wwz.ai.domain.agent.runtime.subagent;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.AgentRunState;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.planmode.PlanModeState;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 创建隔离的子 Agent 上下文。
 * 不继承主对话记忆；共享 runtime 依赖、账本、产物登记；
 * printer 包装为 SubAgentPrinter，事件挂到父 Agent tool_use 下。
 * <p>
 * 身份隔离（对齐 ZCode「子会话是协议层真实会话」）：
 * <ul>
 *   <li>{@code sessionId} 使用确定性 {@code sub_{agentId}}，子 Agent 的 ledger 记账落子会话；</li>
 *   <li>{@code agentRunState} 使用<b>全新实例</b>，runId / LLM 序号 / toolCallId 映射都与父 run 解耦，
 *       否则子 Agent 的调用会计进父 run 计数。</li>
 * </ul>
 * 注意：executionRecorder 仍共享父实例（无状态账本写入服务），Todo 列表与后台任务注册表
 * 继续沿用父引用——它们是父会话控制面，TaskStop / TaskOutput 必须能命中子 Agent。
 */
public final class SubAgentContextFactory {

    private SubAgentContextFactory() {
    }

    public static AgentContext create(AgentContext parent,
                                      String prompt,
                                      String description,
                                      ToolCollection childTools,
                                      String agentId,
                                      String agentType) {
        return create(parent, prompt, description, childTools, agentId, agentType, null);
    }

    public static AgentContext create(AgentContext parent,
                                      String prompt,
                                      String description,
                                      ToolCollection childTools,
                                      String agentId,
                                      String agentType,
                                      String explicitParentToolUseId) {
        return create(parent, prompt, description, childTools, agentId, agentType, explicitParentToolUseId,
                (Printer) null, null);
    }

    /**
     * @param isolatedChildPrinter Phase 3 SSE 隔离时外部提供的子会话 Printer；
     *                              非 null 时不再包装父 printer，子事件直接写子会话投影流。
     * @param childRequestIdOverride 指定的子 requestId；空时按 agentId 派生。
     *                              与 isolatedChildPrinter 必须来自同一次 {@code openChildSessionStream}。
     */
    public static AgentContext create(AgentContext parent,
                                      String prompt,
                                      String description,
                                      ToolCollection childTools,
                                      String agentId,
                                      String agentType,
                                      String explicitParentToolUseId,
                                      Printer isolatedChildPrinter,
                                      String childRequestIdOverride) {
        if (parent == null) {
            throw new IllegalArgumentException("parent AgentContext 不能为空");
        }
        // 短 requestId：避免 parentRequestId + ":sub:" + agentId 撑破 working_memory.request_id 列宽
        String childRequestId = newChildRequestId(agentId);
        // 显式 parentToolUseId 优先；否则回退当前线程绑定的 Agent 工具 toolCallId。
        // 二者皆空时子工具无法嵌套到父卡片（前端只显示 totalToolUseCount）。
        String parentToolUseId = StringUtils.isNotBlank(explicitParentToolUseId)
                ? explicitParentToolUseId.trim()
                : resolveParentToolUseId(parent);
        Printer childPrinter = isolatedChildPrinter != null
                ? isolatedChildPrinter
                : wrapPrinter(parent.getPrinter(), parentToolUseId, agentId, agentType, description);
        // 确定性子会话 ID：由 agentId 纯函数派生，无需外部传参即可保证三处一致
        // （落库 / AgentContext / 账本 run）。
        String childSessionId = newChildSessionId(agentId);
        String effectiveChildRequestId = StringUtils.isNotBlank(childRequestIdOverride)
                ? childRequestIdOverride.trim()
                : newChildRequestId(agentId);

        AgentContext child = AgentContext.builder()
                .requestId(effectiveChildRequestId)
                .sessionId(childSessionId)
                .query(prompt)
                .task(description)
                .model(parent.getModel())
                .thinking(parent.getThinking())
                .thinkingEffort(parent.getThinkingEffort())
                .printer(childPrinter)
                .toolCollection(childTools)
                .runtimeDependencies(parent.getRuntimeDependencies())
                .executionRecorder(parent.getExecutionRecorder())
                // 独立 AgentRunState：不传父实例，走 builder 默认值拿到全新 run 级状态
                .agentRunState(new AgentRunState())
                .toolArtifactRegistry(parent.getToolArtifactRegistry())
                // 与主 Agent 共享 todo 列表 / 后台任务注册表；plan mode 只约束主 Agent
                .sessionTaskList(parent.requireSessionTaskList())
                .backgroundTasks(parent.requireBackgroundTasks())
                .planModeState(PlanModeState.builder().build())
                .workspaceRoot(parent.getWorkspaceRoot())
                // 子 Agent 必须自己读取文件，不能复用主 Agent 的 unchanged 判定。
                .workspaceReadStateByPath(new ConcurrentHashMap<>())
                .dateInfo(parent.getDateInfo())
                // 子 Agent 统一走流式 LLM（askTool stream=true），兼容仅支持 stream 的网关；
                // 过程文仍由 SubAgentPrinter 折叠，不刷主时间线。
                .isStream(Boolean.TRUE)
                .streamMessageType(null)
                .sopPrompt(null)
                .basePrompt(null)
                .historyDialogue(null)
                .workingMemoryMessages(null)
                // 共享父取消令牌，否则 /stop 杀不到嵌套子 Agent
                .runCancellation(parent.getRunCancellation())
                // 子代理不参与长期记忆；仅保留独立 working_memory 投影用于 resume。
                .skipMemory(Boolean.TRUE)
                .ltmOwner(null)
                .ltmMemoryContext(null)
                .agentType(parent.getAgentType())
                .templateType(parent.getTemplateType())
                .productFiles(parent.getProductFiles() == null ? new ArrayList<>() : new ArrayList<>(parent.getProductFiles()))
                // 账本 + 重放：与 SubAgentPrinter SSE 标签同契约
                .parentToolUseId(parentToolUseId)
                .subAgentId(agentId)
                .subAgentType(agentType)
                .subAgentDescription(description)
                // 确定性子会话 ID：与 SubAgentRunner 落库用的 childSessionId 必须一致
                .subAgentChildSessionId(newChildSessionId(agentId))
                .build();
        // 显式再钉一次，避免 builder 默认值路径漏传 skipMemory
        child.setSkipMemory(Boolean.TRUE);
        if (childTools != null) {
            childTools.setAgentContext(child);
        }
        return child;
    }

    private static String resolveParentToolUseId(AgentContext parent) {
        ToolArtifactSource source = parent.getCurrentToolArtifactSource();
        if (source == null || StringUtils.isBlank(source.getToolCallId())) {
            return null;
        }
        return source.getToolCallId();
    }

    private static Printer wrapPrinter(Printer parentPrinter,
                                       String parentToolUseId,
                                       String agentId,
                                       String agentType,
                                       String description) {
        if (parentPrinter == null) {
            return null;
        }
        // 即使 parentToolUseId 缺失也必须包装：至少打上 subAgentId，禁止子事件伪装成根结果。
        return new SubAgentPrinter(parentPrinter, parentToolUseId, agentId, agentType, description);
    }

    public static String newAgentId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 子 Agent 运行 requestId（首跑/续跑/快照共用）。
     * 形态 {@code sub:{agentId}:{8hex}}，长度远低于 64/128 列宽；父 requestId 只写日志不嵌入。
     */
    public static String newChildRequestId(String agentId) {
        String aid = StringUtils.defaultIfBlank(agentId, "unknown").trim();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String id = "sub:" + aid + ":" + suffix;
        return id.length() <= 64 ? id : id.substring(0, 64);
    }

    /**
     * 子会话 ID 前缀。{@code sub_}（会话 ID）与 {@code sub:}（requestId）是两个不同命名空间，
     * 不要混用。
     */
    public static final String CHILD_SESSION_ID_PREFIX = "sub_";

    /**
     * 子会话 ID（<b>确定性</b>，可由 agentId 反推）：{@code sub_{agentId}}。
     * <p>
     * 对齐 ZCode「子会话是协议层真实会话、ID 可由 agentId 派生」的做法：
     * 冷恢复时无需额外映射表即可定位子会话。确定性也要求<b>续跑必须复用同一 childSessionId</b>，
     * 否则同一次 SendMessage 唤醒会被拆成两个子会话（agentId 全局唯一，见 {@link #newAgentId()}）。
     */
    public static String newChildSessionId(String agentId) {
        String aid = StringUtils.defaultIfBlank(agentId, "unknown").trim();
        String id = CHILD_SESSION_ID_PREFIX + aid;
        return id.length() <= 64 ? id : id.substring(0, 64);
    }
}

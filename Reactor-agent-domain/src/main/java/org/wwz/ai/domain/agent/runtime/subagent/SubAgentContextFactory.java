package org.wwz.ai.domain.agent.runtime.subagent;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.UUID;

/**
 * 创建隔离的子 Agent 上下文。
 * 不继承主对话记忆；共享 runtime 依赖、账本、产物登记；
 * printer 包装为 SubAgentPrinter，事件挂到父 Agent tool_use 下。
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
        Printer childPrinter = wrapPrinter(parent.getPrinter(), parentToolUseId, agentId, agentType, description);

        AgentContext child = AgentContext.builder()
                .requestId(childRequestId)
                .sessionId(parent.getSessionId())
                .query(prompt)
                .task(description)
                .model(parent.getModel())
                .thinking(parent.getThinking())
                .thinkingEffort(parent.getThinkingEffort())
                .printer(childPrinter)
                .toolCollection(childTools)
                .runtimeDependencies(parent.getRuntimeDependencies())
                .executionRecorder(parent.getExecutionRecorder())
                .agentRunState(parent.getAgentRunState())
                .toolArtifactRegistry(parent.getToolArtifactRegistry())
                // 与主 Agent 共享 todo 列表 / plan mode / 后台任务注册表
                .sessionTaskList(parent.requireSessionTaskList())
                .backgroundTasks(parent.requireBackgroundTasks())
                .planModeState(parent.requirePlanModeState())
                .workspaceRoot(parent.getWorkspaceRoot())
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
                // 子代理不继承主会话 LTM 写入权。
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
                .build();
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
        if (StringUtils.isBlank(parentToolUseId)) {
            return parentPrinter;
        }
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
}

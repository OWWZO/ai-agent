package org.wwz.ai.domain.agent.runtime.subagent;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.UUID;

/**
 * 创建隔离的子 Agent 上下文（对标 cc-haha createSubagentContext）。
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
        if (parent == null) {
            throw new IllegalArgumentException("parent AgentContext 不能为空");
        }
        String childRequestId = parent.getRequestId() + ":sub:" + agentId;
        String parentToolUseId = resolveParentToolUseId(parent);
        Printer childPrinter = wrapPrinter(parent.getPrinter(), parentToolUseId, agentId, agentType, description);

        AgentContext child = AgentContext.builder()
                .requestId(childRequestId)
                .sessionId(parent.getSessionId())
                .query(prompt)
                .task(description)
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
                .isStream(Boolean.FALSE)
                .streamMessageType(null)
                .sopPrompt(null)
                .basePrompt(null)
                .historyDialogue(null)
                .workingMemoryMessages(null)
                .agentType(parent.getAgentType())
                .templateType(parent.getTemplateType())
                .productFiles(parent.getProductFiles() == null ? new ArrayList<>() : new ArrayList<>(parent.getProductFiles()))
                .taskProductFiles(new ArrayList<>())
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
}

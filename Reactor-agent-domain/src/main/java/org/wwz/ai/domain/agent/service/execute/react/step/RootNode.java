package org.wwz.ai.domain.agent.service.execute.react.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.factory.AgentToolCollectionFactory;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceSessionFileMaterializer;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceReadStateStore;
import org.wwz.ai.domain.agent.runtime.util.DateUtil;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.memory.ltm.LtmRuntimeBootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * React 逻辑树 - 步骤1：准备上下文与工具（AgentContext、AgentRequest、ToolCollection）
 */
@Slf4j
@Service("reactRootNode")
public class RootNode extends AbstractExecuteSupport {

    @Resource
    private AgentToolCollectionFactory agentToolCollectionFactory;

    @Resource
    private WorkspaceService workspaceService;

    @Resource
    private WorkspaceSessionFileMaterializer workspaceSessionFileMaterializer;

    @Resource
    private WorkspaceReadStateStore workspaceReadStateStore;

    @Resource
    private RunReactNode step2RunReactNode;

    @Resource
    private AgentExecutionRecorder agentExecutionRecorder;

    @Resource
    private ReactorRuntimeDependencies reactorRuntimeDependencies;

    @Resource
    private org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry activeAgentRunRegistry;

    @Override
    protected String doApply(AgentRequest request, DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("React Step1: Prepare context and tools for requestId: {}", request.getRequestId());

        // 根节点只负责把请求翻译成运行时上下文，并把后续节点需要的能力装配齐；它是
        // 请求边界进入 runtime 的唯一准备点，后续节点不应重新读取原始请求来拼装上下文。
        dynamicContext.setStep(0);
        Printer printer = dynamicContext.getPrinter();

        AgentContext agentContext = AgentContext.builder()
                .requestId(request.getRequestId())
                .sessionId(request.getSessionId())
                .printer(printer)
                .query(request.getQuery())
                .task("")
                .dateInfo(DateUtil.CurrentDateInfo())
                .productFiles(new ArrayList<>(convertFiles(request.getSessionFiles())))
                .workspaceRoot(resolveWorkspaceRoot(request.getSessionId()))
                .sopPrompt(request.getSopPrompt())
                .basePrompt(request.getBasePrompt())
                .historyDialogue(request.getHistoryDialogue())
                .workingMemoryMessages(request.getWorkingMemoryMessages())
                .agentType(request.getAgentType())
                .isStream(Objects.nonNull(request.getIsStream()) ? request.getIsStream() : false)
                .templateType("dataAgent".equals(request.getOutputStyle()) ? "fix" : "empty")
                .executionRecorder(agentExecutionRecorder)
                .runtimeDependencies(reactorRuntimeDependencies)
                .build();

        materializeSessionFiles(agentContext, request.getSessionFiles());
        hydrateWorkspaceReadState(agentContext);
        LtmRuntimeBootstrap.bootstrap(agentContext, request);

        // 运行账本记录本轮执行事实；working memory 只在本轮结束后作为下一轮 prompt 投影，
        // 因此这里初始化 run 但不把历史消息重新写入旧 message/transcript 主账本。
        ExecutionLedgerRunSupport.initializeRun(
                agentExecutionRecorder,
                agentContext,
                request,
                ExecutionLedgerConstants.ENTRY_AGENT_REACT
        );
        agentContext.setToolCollection(buildToolCollection(agentContext, request));
        bindActiveRunIfPresent(agentContext);
        dynamicContext.setAgentContext(agentContext);
        dynamicContext.setStep(1);

        return router(request, dynamicContext);
    }

    private void bindActiveRunIfPresent(AgentContext agentContext) {
        if (activeAgentRunRegistry == null || agentContext == null) {
            return;
        }
        activeAgentRunRegistry.bindContext(agentContext.getRequestId(), agentContext);
    }

    private ToolCollection buildToolCollection(AgentContext agentContext, AgentRequest request) {
        // 工具集合必须以已完成初始化的 AgentContext 为输入，确保工具看到相同的 session、
        // workspace、取消信号和执行记录器，而不是各自从请求参数重新推导运行状态。
        return agentToolCollectionFactory.buildForReact(agentContext, request);
    }




    private void hydrateWorkspaceReadState(AgentContext agentContext) {
        if (workspaceReadStateStore == null || agentContext == null) {
            return;
        }
        try {
            workspaceReadStateStore.hydrate(agentContext);
        } catch (Exception e) {
            log.warn("hydrate workspace read-state failed, requestId={}", agentContext.getRequestId(), e);
        }
    }

    private void materializeSessionFiles(AgentContext agentContext, java.util.List<FileInformation> sessionFiles) {
        if (workspaceSessionFileMaterializer == null) {
            return;
        }
        try {
            // 文件物化是 best-effort：文件服务异常不应阻止纯文本请求继续执行，工具后续仍可
            // 通过稳定引用或显式错误信息决定是否使用该文件。
            workspaceSessionFileMaterializer.materialize(agentContext, sessionFiles);
        } catch (Exception e) {
            log.warn("materialize session files failed, requestId={}", agentContext == null ? null : agentContext.getRequestId(), e);
        }
    }

    private String resolveWorkspaceRoot(String sessionId) {
        if (workspaceService == null || !workspaceService.isEnabled()) {
            return null;
        }
        try {
            return workspaceService.resolveAndEnsureRoot(sessionId).toString();
        } catch (Exception e) {
            log.warn("resolve workspace root failed, sessionId={}", sessionId, e);
            return null;
        }
    }

    private List<File> convertFiles(List<FileInformation> sessionFiles) {
        if (sessionFiles == null || sessionFiles.isEmpty()) {
            return List.of();
        }
        List<File> files = new ArrayList<>(sessionFiles.size());
        for (FileInformation sessionFile : sessionFiles) {
            files.add(File.builder()
                    .fileName(sessionFile.getFileName())
                    .description(sessionFile.getFileDesc())
                    .ossUrl(sessionFile.getOssUrl())
                    .domainUrl(sessionFile.getDomainUrl())
                    .fileSize(sessionFile.getFileSize())
                    .originFileName(sessionFile.getOriginFileName())
                    .originOssUrl(sessionFile.getOriginOssUrl())
                    .originDomainUrl(sessionFile.getOriginDomainUrl())
                    .isInternalFile(Boolean.FALSE)
                    .build());
        }
        return files;
    }

    private List<Message> convertMessages(List<AgentRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Message> result = new ArrayList<>(messages.size());
        for (AgentRequest.Message message : messages) {
            result.add(convertMessage(message));
        }
        return result;
    }

    private Message convertMessage(AgentRequest.Message message) {
        if (message == null) {
            return Message.builder()
                    .role(RoleType.USER)
                    .content(null)
                    .build();
        }
        return Message.builder()
                .role(resolveRoleType(message))
                .content(buildHistoryMessageContent(message))
                .build();
    }

    private RoleType resolveRoleType(AgentRequest.Message message) {
        if (hasStructuredToolTrace(message)) {
            return RoleType.ASSISTANT;
        }
        return resolveRoleType(message == null ? null : message.getRole());
    }

    private RoleType resolveRoleType(String role) {
        if ("assistant".equalsIgnoreCase(role)) {
            return RoleType.ASSISTANT;
        }
        if ("tool".equalsIgnoreCase(role)) {
            // ReAct 续聊历史中的 tool 角色仅作为文本上下文使用，避免网关继续按工具返回项校验 call_id。
            return RoleType.ASSISTANT;
        }
        return RoleType.USER;
    }

    /**
     * 历史续聊消息保留工具调用文本，但不再把旧的 tool_call 结构直接发给 LLM，
     * 避免网关将其误判为未闭合的原生 function-call 链路。
     */
    private String buildHistoryMessageContent(AgentRequest.Message message) {
        if (message == null) {
            return null;
        }
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            return joinParts(
                    message.getContent(),
                    buildToolUseSummary(message.getToolCalls()));
        }
        if (message.getToolCallId() != null && !message.getToolCallId().isBlank()) {
            return joinParts(
                    prefixLabel("历史工具结果", message.getContent()),
                    buildArtifactHint(message));
        }
        return message.getContent();
    }

    private boolean hasStructuredToolTrace(AgentRequest.Message message) {
        if (message == null) {
            return false;
        }
        return (message.getToolCalls() != null && !message.getToolCalls().isEmpty())
                || (message.getToolCallId() != null && !message.getToolCallId().isBlank());
    }

    private String buildToolUseSummary(List<ToolCall> toolCalls) {
        List<String> parts = new ArrayList<>(toolCalls.size());
        for (ToolCall toolCall : toolCalls) {
            if (toolCall == null || toolCall.getFunction() == null) {
                continue;
            }
            List<String> oneCallParts = new ArrayList<>(2);
            if (toolCall.getFunction().getName() != null && !toolCall.getFunction().getName().isBlank()) {
                oneCallParts.add("历史工具调用：" + toolCall.getFunction().getName());
            }
            if (toolCall.getFunction().getArguments() != null && !toolCall.getFunction().getArguments().isBlank()) {
                oneCallParts.add("参数：" + toolCall.getFunction().getArguments());
            }
            String oneCall = joinParts(oneCallParts.toArray(new String[0]));
            if (oneCall != null && !oneCall.isBlank()) {
                parts.add(oneCall);
            }
        }
        if (parts.isEmpty()) {
            return "历史工具调用";
        }
        return String.join("\n", parts);
    }

    private String buildArtifactHint(AgentRequest.Message message) {
        if (message.getFiles() == null || message.getFiles().isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (FileInformation file : message.getFiles()) {
            if (file != null && file.getFileName() != null && !file.getFileName().isBlank()) {
                names.add(file.getFileName());
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        return "关联文件：" + String.join("、", names);
    }

    private String prefixLabel(String label, String content) {
        if (content == null || content.isBlank()) {
            return label;
        }
        return label + "：\n" + content;
    }

    private String joinParts(String... parts) {
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                values.add(part);
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        return String.join("\n", values);
    }

    @Override
    public StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> get(
            AgentRequest requestParameter,
            DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step2RunReactNode;
    }
}

package org.wwz.ai.application.agent.execute;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.application.agent.askuser.AskUserResumeApplicationService;
import org.wwz.ai.application.agent.desktopcontrol.DesktopControlResumeApplicationService;
import org.wwz.ai.application.agent.planmode.PlanApprovalResumeApplicationService;
import org.wwz.ai.domain.agent.memory.SessionContextMemoryService;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.runtime.askuser.IUserQuestionRepository;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.IDesktopControlRepository;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.planmode.IPlanApprovalRepository;

import java.util.List;

/**
 * React / PlanSolve 共用的跨轮工作记忆 hydrate。
 * <p>
 * 两个应用层执行策略此前逐字重复这段逻辑：优先读 working_memory 投影，
 * 空则回退 ledger hydrate，再按 resume 标记追加让步观测。收敛到此处后，
 * 两条链路看到的消息序列只有一个来源。
 */
public final class WorkingMemoryHydration {

    private WorkingMemoryHydration() {
    }

    /**
     * 装配跨轮工作记忆，写回 request 供后续节点读取，并返回同一份列表。
     *
     * @param userQuestionRepository    AskUserQuestion 续跑仓储；无 resumeQuestionId 时可为 null
     * @param desktopControlRepository  DesktopControl 续跑仓储；无 resumeDesktopControlId 时可为 null
     * @param planApprovalRepository    ExitPlanMode 续跑仓储；无 resumeApprovalId 时可为 null
     */
    public static List<Message> enrich(AgentExecutionCommand request,
                                       SessionWorkingMemoryService sessionWorkingMemoryService,
                                       SessionContextMemoryService sessionContextMemoryService,
                                       IUserQuestionRepository userQuestionRepository,
                                       IDesktopControlRepository desktopControlRepository,
                                       IPlanApprovalRepository planApprovalRepository) {
        if (request == null) {
            return List.of();
        }
        List<Message> working = List.of();
        if (sessionWorkingMemoryService != null) {
            // 优先读取已持久化的 working_memory 投影，保持 prompt cache 友好的消息形状。
            working = sessionWorkingMemoryService.loadReadyMessages(request.getSessionId(), request.getRequestId());
        }
        // 冷启动/无投影时回退 ledger hydrate，保证首批会话仍有跨轮上下文
        if ((working == null || working.isEmpty()) && sessionContextMemoryService != null) {
            working = sessionContextMemoryService.hydrateWorkingMessages(request.getSessionId(), request.getRequestId());
        }
        if (StringUtils.isNotBlank(request.getResumeQuestionId()) && userQuestionRepository != null) {
            working = AskUserResumeApplicationService.appendAnswerObservation(
                    working,
                    userQuestionRepository.findByQuestionId(request.getResumeQuestionId()).orElse(null)
            );
        }
        if (StringUtils.isNotBlank(request.getResumeDesktopControlId()) && desktopControlRepository != null) {
            working = DesktopControlResumeApplicationService.appendCompletedObservation(
                    working,
                    desktopControlRepository.findByControlId(request.getResumeDesktopControlId()).orElse(null)
            );
        }
        if (StringUtils.isNotBlank(request.getResumeApprovalId()) && planApprovalRepository != null) {
            working = PlanApprovalResumeApplicationService.appendDecisionObservation(
                    working,
                    planApprovalRepository.findByApprovalId(request.getResumeApprovalId()).orElse(null)
            );
        }
        List<Message> hydrated = working == null ? List.of() : working;
        request.setWorkingMemoryMessages(hydrated);
        return hydrated;
    }
}

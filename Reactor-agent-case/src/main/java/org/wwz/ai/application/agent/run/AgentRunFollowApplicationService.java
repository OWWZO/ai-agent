package org.wwz.ai.application.agent.run;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.stream.AgentResponseProjectionStream;
import org.wwz.ai.application.agent.stream.AgentSessionPrinter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.application.agent.visitor.ConversationSessionOwnershipApplicationService;
import org.wwz.ai.application.agent.visitor.SessionOwnershipDeniedException;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import java.util.Optional;

/**
 * 刷新后续绑本轮仍在进程内执行的 Agent run 观察流（不重跑 Agent）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunFollowApplicationService {

    private final ActiveAgentRunRegistry activeAgentRunRegistry;
    private final ConversationSessionOwnershipApplicationService conversationSessionOwnershipApplicationService;
    private final IExecutionLedgerReadRepository executionLedgerReadRepository;

    /**
     * @return true 已挂上活跃 run；false 表示无法续绑 live 流，已向 observer 推送 follow_idle / follow_pending 并 complete
     */
    public boolean follow(String sessionId, String requestId, AgentSessionStream observer) {
        if (observer == null) {
            return false;
        }
        if (StringUtils.isBlank(requestId)) {
            completeIdle(observer, requestId);
            return false;
        }

        try {
            String visitorId = VisitorRequestContext.currentVisitorId();
            if (StringUtils.isBlank(visitorId)) {
                throw new IllegalArgumentException("visitorId不能为空");
            }
            if (StringUtils.isNotBlank(sessionId)) {
                conversationSessionOwnershipApplicationService.ensureExistingSessionAccessible(
                        visitorId, sessionId);
            }
        } catch (SessionOwnershipDeniedException | IllegalArgumentException e) {
            log.warn("follow rejected requestId={} sessionId={}", requestId, sessionId, e);
            completeIdle(observer, requestId);
            return false;
        }

        Optional<ActiveAgentRunRegistry.ActiveRun> found = activeAgentRunRegistry.find(requestId);
        if (found.isEmpty()) {
            // registry 空时查 ledger：仍 RUNNING 则让前端继续退避，不要当任务结束。
            if (isLedgerRunStillRunning(requestId)) {
                log.info("follow pending, no active run in registry but ledger RUNNING requestId={}",
                        requestId);
                completePending(observer, requestId);
                return false;
            }
            log.info("follow idle, no active run requestId={}", requestId);
            completeIdle(observer, requestId);
            return false;
        }

        ActiveAgentRunRegistry.ActiveRun run = found.get();
        if (StringUtils.isNotBlank(sessionId)
                && StringUtils.isNotBlank(run.getSessionId())
                && !sessionId.equals(run.getSessionId())) {
            log.warn("follow session mismatch requestId={} expected={} actual={}",
                    requestId, sessionId, run.getSessionId());
            completeIdle(observer, requestId);
            return false;
        }

        AgentContext agentContext = run.getAgentContext();
        Printer printer = agentContext == null ? null : agentContext.getPrinter();
        if (!(printer instanceof AgentSessionPrinter sessionPrinter)) {
            log.warn("follow unavailable, printer not ready requestId={}", requestId);
            if (isLedgerRunStillRunning(requestId)) {
                completePending(observer, requestId);
            } else {
                completeIdle(observer, requestId);
            }
            return false;
        }

        AgentSessionStream root = sessionPrinter.attachObserver(observer);
        if (root == null) {
            if (isLedgerRunStillRunning(requestId)) {
                completePending(observer, requestId);
            } else {
                completeIdle(observer, requestId);
            }
            return false;
        }

        // 根流仍是投影流，stop / 终态 complete 走同一条链路；observer 只是其下游 SSE。
        activeAgentRunRegistry.bindStream(requestId, root);
        log.info("follow attached requestId={} sessionId={}", requestId, run.getSessionId());
        return true;
    }

    private boolean isLedgerRunStillRunning(String requestId) {
        try {
            DialogueRun run = executionLedgerReadRepository.queryRunByRequestId(requestId);
            if (run == null || run.getStatus() == null) {
                return false;
            }
            // 用 equals，避免装箱比较踩坑导致误判 idle。
            return Integer.valueOf(ExecutionLedgerConstants.STATUS_RUNNING).equals(run.getStatus());
        } catch (Exception e) {
            log.warn("query ledger run status failed requestId={}", requestId, e);
            return false;
        }
    }

    private void completeIdle(AgentSessionStream observer, String requestId) {
        try {
            observer.send(AgentResponseProjectionStream.buildFollowIdle(requestId));
        } catch (Exception e) {
            log.debug("send follow_idle failed requestId={}", requestId, e);
        }
        try {
            observer.complete();
        } catch (Exception e) {
            log.debug("complete follow idle failed requestId={}", requestId, e);
        }
    }

    private void completePending(AgentSessionStream observer, String requestId) {
        try {
            observer.send(AgentResponseProjectionStream.buildFollowPending(requestId));
        } catch (Exception e) {
            log.debug("send follow_pending failed requestId={}", requestId, e);
        }
        try {
            observer.complete();
        } catch (Exception e) {
            log.debug("complete follow pending failed requestId={}", requestId, e);
        }
    }
}

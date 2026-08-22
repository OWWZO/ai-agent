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

    public static final long PENDING_RETRY_MS = 800L;

    private final ActiveAgentRunRegistry activeAgentRunRegistry;
    private final ConversationSessionOwnershipApplicationService conversationSessionOwnershipApplicationService;
    private final IExecutionLedgerReadRepository executionLedgerReadRepository;

    /**
     * 首次 follow：校验归属后尝试续绑。PENDING 时不 complete，由入口挂住 SSE 再试。
     */
    public FollowAttachResult follow(String sessionId,
                                     String requestId,
                                     long lastEventSeq,
                                     AgentSessionStream observer) {
        if (observer == null) {
            return FollowAttachResult.IDLE;
        }
        if (StringUtils.isBlank(requestId)) {
            completeIdle(observer, requestId);
            return FollowAttachResult.IDLE;
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
            return FollowAttachResult.IDLE;
        }

        return attachAttempt(sessionId, requestId, lastEventSeq, observer);
    }

    /**
     * 挂起后续试：不再读 visitor 线程上下文，只尝试续绑或根据 ledger 收口。
     */
    public FollowAttachResult retryAttach(String sessionId,
                                          String requestId,
                                          long lastEventSeq,
                                          AgentSessionStream observer) {
        if (observer == null || observer.isAborted()) {
            return FollowAttachResult.IDLE;
        }
        if (StringUtils.isBlank(requestId)) {
            completeIdle(observer, requestId);
            return FollowAttachResult.IDLE;
        }
        return attachAttempt(sessionId, requestId, lastEventSeq, observer);
    }

    public void completePending(AgentSessionStream observer, String requestId) {
        completePending(observer, requestId, PENDING_RETRY_MS);
    }

    public void completePending(AgentSessionStream observer, String requestId, long retryMs) {
        if (observer == null) {
            return;
        }
        try {
            observer.send(AgentResponseProjectionStream.buildFollowPending(requestId, retryMs));
        } catch (Exception e) {
            log.debug("send follow_pending failed requestId={}", requestId, e);
        }
        try {
            observer.complete();
        } catch (Exception e) {
            log.debug("complete follow pending failed requestId={}", requestId, e);
        }
    }

    private FollowAttachResult attachAttempt(String sessionId,
                                             String requestId,
                                             long lastEventSeq,
                                             AgentSessionStream observer) {
        Optional<ActiveAgentRunRegistry.ActiveRun> found = activeAgentRunRegistry.find(requestId);
        if (found.isEmpty()) {
            if (isLedgerRunStillRunning(requestId)) {
                log.info("follow pending, no active run in registry but ledger RUNNING requestId={}",
                        requestId);
                return FollowAttachResult.PENDING;
            }
            log.info("follow idle, no active run requestId={}", requestId);
            completeIdle(observer, requestId);
            return FollowAttachResult.IDLE;
        }

        ActiveAgentRunRegistry.ActiveRun run = found.get();
        if (StringUtils.isNotBlank(sessionId)
                && StringUtils.isNotBlank(run.getSessionId())
                && !sessionId.equals(run.getSessionId())) {
            log.warn("follow session mismatch requestId={} expected={} actual={}",
                    requestId, sessionId, run.getSessionId());
            completeIdle(observer, requestId);
            return FollowAttachResult.IDLE;
        }

        AgentContext agentContext = run.getAgentContext();
        Printer printer = agentContext == null ? null : agentContext.getPrinter();
        if (!(printer instanceof AgentSessionPrinter sessionPrinter)) {
            log.warn("follow unavailable, printer not ready requestId={}", requestId);
            if (isLedgerRunStillRunning(requestId)) {
                return FollowAttachResult.PENDING;
            }
            completeIdle(observer, requestId);
            return FollowAttachResult.IDLE;
        }

        AgentSessionStream root = sessionPrinter.attachObserver(observer, lastEventSeq);
        if (root == null) {
            if (isLedgerRunStillRunning(requestId)) {
                return FollowAttachResult.PENDING;
            }
            completeIdle(observer, requestId);
            return FollowAttachResult.IDLE;
        }

        activeAgentRunRegistry.bindStream(requestId, root);
        log.info("follow attached requestId={} sessionId={}", requestId, run.getSessionId());
        return FollowAttachResult.ATTACHED;
    }

    private boolean isLedgerRunStillRunning(String requestId) {
        try {
            DialogueRun run = executionLedgerReadRepository.queryRunByRequestId(requestId);
            if (run == null || run.getStatus() == null) {
                return false;
            }
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
}

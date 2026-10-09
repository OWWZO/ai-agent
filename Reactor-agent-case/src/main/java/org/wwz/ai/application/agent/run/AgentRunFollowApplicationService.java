package org.wwz.ai.application.agent.run;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.stream.AgentStreamProjection;
import org.wwz.ai.application.agent.stream.AgentSessionStreamFrame;
import org.wwz.ai.application.agent.stream.AgentSessionPrinter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.application.agent.stream.SessionProjectionRegistry;
import org.wwz.ai.application.agent.stream.StreamFrameConsumer;
import org.wwz.ai.application.agent.authorization.ConversationSessionAuthorizationService;
import org.wwz.ai.application.agent.authorization.SessionOwnershipDeniedException;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.types.agent.user.UserRequestContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 按 session 挂观察流：回放投影缓冲，不重跑 Agent。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunFollowApplicationService {

    public static final long PENDING_RETRY_MS = 800L;

    private final ActiveAgentRunRegistry activeAgentRunRegistry;
    private final ConversationSessionAuthorizationService conversationSessionAuthorizationService;
    private final IExecutionLedgerReadRepository executionLedgerReadRepository;
    private final AgentRunLaunchGate agentRunLaunchGate;
    private final SessionProjectionRegistry sessionProjectionRegistry;

    public FollowAttachResult authorizeAndAttach(String sessionId,
                                                 long lastEventSeq,
                                                 StreamFrameConsumer replay) {
        try {
            String userId = UserRequestContext.currentUserId();
            if (StringUtils.isBlank(userId)) {
                throw new IllegalArgumentException("userId不能为空");
            }
            if (StringUtils.isBlank(sessionId)) {
                throw new IllegalArgumentException("sessionId不能为空");
            }
            conversationSessionAuthorizationService.ensureExistingSessionAccessible(
                    userId, sessionId);
        } catch (SessionOwnershipDeniedException | IllegalArgumentException e) {
            log.warn("observe rejected sessionId={}", sessionId, e);
            return FollowAttachResult.IDLE;
        }
        return attachSession(sessionId, lastEventSeq, replay);
    }

    public FollowAttachResult attachSession(String sessionId,
                                            long lastEventSeq,
                                            StreamFrameConsumer replay) {
        if (StringUtils.isBlank(sessionId)) {
            return FollowAttachResult.IDLE;
        }
        agentRunLaunchGate.launchBySession(sessionId);
        List<AgentStreamProjection> live = sessionProjectionRegistry == null
                ? List.of()
                : sessionProjectionRegistry.listLive(sessionId);
        if (!live.isEmpty()) {
            if (!replayLiveProjections(sessionId, lastEventSeq, replay, live)) {
                return FollowAttachResult.PENDING;
            }
            Optional<ActiveAgentRunRegistry.ActiveRun> found = activeAgentRunRegistry.findBySessionId(sessionId);
            found.ifPresent(run -> activeAgentRunRegistry.bindStream(run.getRequestId(), live.get(live.size() - 1)));
            log.info("observe attached sessionId={} liveProjections={}", sessionId, live.size());
            return FollowAttachResult.ATTACHED;
        }

        Optional<ActiveAgentRunRegistry.ActiveRun> found = activeAgentRunRegistry.findBySessionId(sessionId);
        if (found.isEmpty()) {
            log.info("observe idle, no live projection sessionId={}", sessionId);
            return FollowAttachResult.IDLE;
        }

        ActiveAgentRunRegistry.ActiveRun run = found.get();
        AgentContext agentContext = run.getAgentContext();
        Printer printer = agentContext == null ? null : agentContext.getPrinter();
        if (!(printer instanceof AgentSessionPrinter sessionPrinter)) {
            log.info("observe pending, printer not ready sessionId={} requestId={}",
                    sessionId, run.getRequestId());
            return FollowAttachResult.PENDING;
        }

        AgentSessionStream root = sessionPrinter.getStream();
        if (root instanceof AgentStreamProjection projection) {
            if (replay != null) {
                for (var frame : projection.replayAfter(lastEventSeq)) {
                    try {
                        replay.accept(frame);
                    } catch (Exception e) {
                        log.warn("replay frame failed sessionId={}", sessionId, e);
                        return FollowAttachResult.PENDING;
                    }
                }
            }
            activeAgentRunRegistry.bindStream(run.getRequestId(), projection);
            log.info("observe attached sessionId={} requestId={}", sessionId, run.getRequestId());
            return FollowAttachResult.ATTACHED;
        }

        if (isLedgerRunStillRunning(run.getRequestId())) {
            return FollowAttachResult.PENDING;
        }
        return FollowAttachResult.IDLE;
    }

    private boolean replayLiveProjections(String sessionId,
                                          long lastEventSeq,
                                          StreamFrameConsumer replay,
                                          List<AgentStreamProjection> live) {
        if (replay == null) {
            return true;
        }
        List<AgentSessionStreamFrame> frames = new ArrayList<>();
        for (AgentStreamProjection projection : live) {
            frames.addAll(projection.replayAfter(lastEventSeq));
        }
        frames.sort(Comparator.comparingLong(AgentSessionStreamFrame::getEventSeq));
        for (AgentSessionStreamFrame frame : frames) {
            try {
                replay.accept(frame);
            } catch (Exception e) {
                log.warn("replay frame failed sessionId={}", sessionId, e);
                return false;
            }
        }
        return true;
    }

    public void completePending(AgentSessionStream observer, String requestId) {
        completePending(observer, requestId, PENDING_RETRY_MS);
    }

    public void completePending(AgentSessionStream observer, String requestId, long retryMs) {
        if (observer == null) {
            return;
        }
        try {
            observer.sendFrame(AgentStreamProjection.buildFollowPending(requestId, retryMs));
        } catch (Exception e) {
            log.debug("send follow_pending failed requestId={}", requestId, e);
        }
        try {
            observer.complete();
        } catch (Exception e) {
            log.debug("complete follow pending failed requestId={}", requestId, e);
        }
    }

    public void completeIdle(AgentSessionStream observer, String requestId) {
        if (observer == null) {
            return;
        }
        try {
            observer.sendFrame(AgentStreamProjection.buildFollowIdle(requestId));
        } catch (Exception e) {
            log.debug("send follow_idle failed requestId={}", requestId, e);
        }
        try {
            observer.complete();
        } catch (Exception e) {
            log.debug("complete follow idle failed requestId={}", requestId, e);
        }
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
}

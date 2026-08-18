package org.wwz.ai.domain.agent.runtime.planmode;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @deprecated Continuation 后由 {@code ai_agent_plan_approval} + resume SSE 取代；
 * 保留 bean 仅兼容旧测试与取消回退路径，主链路不再 await Future。
 */
@Deprecated
@Component
public class PendingPlanApprovalRegistry {

    public static final long DEFAULT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);

    private final ConcurrentHashMap<String, PendingPlanApproval> pending = new ConcurrentHashMap<>();

    public PendingPlanApproval create(String sessionId,
                                      String requestId,
                                      String toolCallId,
                                      String planContent,
                                      String planFilePath,
                                      Long timeoutMs) {
        String approvalId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long timeout = timeoutMs != null && timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        PendingPlanApproval item = PendingPlanApproval.builder()
                .approvalId(approvalId)
                .sessionId(sessionId)
                .requestId(requestId)
                .toolCallId(toolCallId)
                .planContent(planContent)
                .planFilePath(planFilePath)
                .createdAtMs(System.currentTimeMillis())
                .timeoutMs(timeout)
                .status(PendingPlanApproval.STATUS_PENDING)
                .future(new CompletableFuture<>())
                .build();
        pending.put(approvalId, item);
        return item;
    }

    public Optional<PendingPlanApproval> get(String approvalId) {
        if (StringUtils.isBlank(approvalId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(pending.get(approvalId.trim()));
    }

    public List<PendingPlanApproval> listBySession(String sessionId) {
        List<PendingPlanApproval> result = new ArrayList<>();
        if (StringUtils.isBlank(sessionId)) {
            return result;
        }
        for (PendingPlanApproval item : pending.values()) {
            if (sessionId.equals(item.getSessionId())
                    && PendingPlanApproval.STATUS_PENDING.equals(item.getStatus())) {
                result.add(item);
            }
        }
        return result;
    }

    public boolean approve(String approvalId, String editedPlanContent, String feedback) {
        PendingPlanApproval item = pending.get(StringUtils.trimToEmpty(approvalId));
        if (item == null) {
            return false;
        }
        synchronized (item) {
            if (!PendingPlanApproval.STATUS_PENDING.equals(item.getStatus())) {
                return false;
            }
            item.setStatus(PendingPlanApproval.STATUS_APPROVED);
            item.setFeedback(feedback);
            if (StringUtils.isNotBlank(editedPlanContent)) {
                item.setPlanContent(editedPlanContent);
            }
            item.getFuture().complete(PlanApprovalDecision.builder()
                    .approved(true)
                    .feedback(feedback)
                    .editedPlanContent(editedPlanContent)
                    .build());
        }
        return true;
    }

    public boolean reject(String approvalId, String feedback) {
        PendingPlanApproval item = pending.get(StringUtils.trimToEmpty(approvalId));
        if (item == null) {
            return false;
        }
        synchronized (item) {
            if (!PendingPlanApproval.STATUS_PENDING.equals(item.getStatus())) {
                return false;
            }
            item.setStatus(PendingPlanApproval.STATUS_REJECTED);
            item.setFeedback(feedback);
            item.getFuture().complete(PlanApprovalDecision.builder()
                    .approved(false)
                    .feedback(StringUtils.defaultIfBlank(feedback, "Plan rejected by user"))
                    .build());
        }
        return true;
    }

    public boolean cancel(String approvalId, String reason) {
        PendingPlanApproval item = pending.get(StringUtils.trimToEmpty(approvalId));
        if (item == null) {
            return false;
        }
        synchronized (item) {
            if (!PendingPlanApproval.STATUS_PENDING.equals(item.getStatus())) {
                return false;
            }
            item.setStatus(PendingPlanApproval.STATUS_CANCELLED);
            item.getFuture().completeExceptionally(
                    new IllegalStateException(StringUtils.defaultIfBlank(reason, "cancelled")));
        }
        return true;
    }

    public void cancelByRequestId(String requestId, String reason) {
        if (StringUtils.isBlank(requestId)) {
            return;
        }
        for (PendingPlanApproval item : pending.values()) {
            if (requestId.equals(item.getRequestId())
                    && PendingPlanApproval.STATUS_PENDING.equals(item.getStatus())) {
                cancel(item.getApprovalId(), reason);
            }
        }
    }

    public PlanApprovalDecision awaitDecision(PendingPlanApproval item)
            throws TimeoutException, InterruptedException {
        try {
            return item.getFuture().get(item.getTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            synchronized (item) {
                if (PendingPlanApproval.STATUS_PENDING.equals(item.getStatus())) {
                    item.setStatus(PendingPlanApproval.STATUS_TIMEOUT);
                    item.getFuture().completeExceptionally(e);
                }
            }
            throw e;
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause == null ? e.getMessage() : cause.getMessage(), cause);
        } finally {
            pending.remove(item.getApprovalId());
        }
    }
}

package org.wwz.ai.domain.agent.runtime.cancel;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.askuser.PendingUserQuestionRegistry;
import org.wwz.ai.domain.agent.runtime.planmode.PendingPlanApprovalRegistry;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内活跃 run 索引：requestId → 上下文/取消标志/流。
 * 供 POST /stop 与 SSE abort 定位当前执行。
 */
@Slf4j
@Component
public class ActiveAgentRunRegistry {

    public static final class ActiveRun {
        private final String requestId;
        private final String sessionId;
        private final RunCancellation cancellation;
        private volatile AgentContext agentContext;
        private volatile AgentMessageStream stream;

        public ActiveRun(String requestId, String sessionId, RunCancellation cancellation) {
            this.requestId = requestId;
            this.sessionId = sessionId;
            this.cancellation = cancellation;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public RunCancellation getCancellation() {
            return cancellation;
        }

        public AgentContext getAgentContext() {
            return agentContext;
        }

        public void setAgentContext(AgentContext agentContext) {
            this.agentContext = agentContext;
        }

        public AgentMessageStream getStream() {
            return stream;
        }

        public void setStream(AgentMessageStream stream) {
            this.stream = stream;
        }
    }

    private final Map<String, ActiveRun> byRequestId = new ConcurrentHashMap<>();

    @Resource
    private PendingUserQuestionRegistry pendingUserQuestionRegistry;

    @Resource
    private PendingPlanApprovalRegistry pendingPlanApprovalRegistry;

    public ActiveRun begin(String requestId, String sessionId) {
        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("requestId 不能为空");
        }
        RunCancellation cancellation = new RunCancellation();
        ActiveRun run = new ActiveRun(requestId, sessionId, cancellation);
        byRequestId.put(requestId, run);
        return run;
    }

    public void bindContext(String requestId, AgentContext agentContext) {
        ActiveRun run = byRequestId.get(requestId);
        if (run == null || agentContext == null) {
            return;
        }
        run.setAgentContext(agentContext);
        agentContext.setRunCancellation(run.getCancellation());
    }

    public void bindStream(String requestId, AgentMessageStream stream) {
        ActiveRun run = byRequestId.get(requestId);
        if (run == null) {
            return;
        }
        run.setStream(stream);
        if (stream != null) {
            stream.onAbort(() -> cancel(requestId, RunCancellation.REASON_CLIENT_DISCONNECT));
        }
    }

    /**
     * @return true 若首次成功取消
     */
    public boolean cancel(String requestId, String reason) {
        ActiveRun run = byRequestId.get(StringUtils.trimToEmpty(requestId));
        if (run == null) {
            return false;
        }
        boolean first = run.getCancellation().cancel(reason);
        if (!first) {
            return false;
        }
        log.info("cancel agent run requestId={} reason={}", requestId, reason);
        if (pendingUserQuestionRegistry != null) {
            pendingUserQuestionRegistry.cancelByRequestId(requestId, reason);
        }
        if (pendingPlanApprovalRegistry != null) {
            pendingPlanApprovalRegistry.cancelByRequestId(requestId, reason);
        }
        AgentContext ctx = run.getAgentContext();
        if (ctx != null) {
            try {
                ctx.requireBackgroundTasks().listRunning().forEach(task ->
                        ctx.requireBackgroundTasks().stop(task.getId()));
            } catch (Exception e) {
                log.warn("cancel background tasks failed, requestId={}", requestId, e);
            }
        }
        return true;
    }

    public Optional<ActiveRun> find(String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(byRequestId.get(requestId.trim()));
    }

    public void end(String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return;
        }
        byRequestId.remove(requestId.trim());
    }

    public boolean isCancelled(String requestId) {
        ActiveRun run = byRequestId.get(StringUtils.trimToEmpty(requestId));
        return run != null && run.getCancellation().isCancelled();
    }
}

package org.wwz.ai.domain.agent.runtime.cancel;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.askuser.PendingUserQuestionRegistry;
import org.wwz.ai.domain.agent.runtime.planmode.PendingPlanApprovalRegistry;
import org.wwz.ai.types.agent.exception.AgentConcurrentRunException;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 进程内活跃 run 索引：requestId → 上下文/取消标志/当前观察流。
 * 供 POST /stop 定位当前执行，也供 SSE 断开时解绑观察流。
 * <p>同一 session 同时只有一个活跃 run；SSE 断开只解绑观察流。</p>
 */
@Slf4j
@Component
public class ActiveAgentRunRegistry {

    public static final String CONCURRENT_RUN_MESSAGE =
            "已有任务在进行中，请等待完成或先停止后再试";

    public static final class ActiveRun {
        private final String requestId;
        private final String sessionId;
        private final String userId;
        private final RunCancellation cancellation;
        /** 控制面 inject 队列：与 AgentContext 共享，不 begin 第二次 run */
        private final ConcurrentLinkedQueue<PendingInjectMessage> pendingInjects = new ConcurrentLinkedQueue<>();
        private volatile AgentContext agentContext;
        private final AtomicReference<AgentMessageStream> stream = new AtomicReference<>();

        public ActiveRun(String requestId, String sessionId, String userId, RunCancellation cancellation) {
            this.requestId = requestId;
            this.sessionId = sessionId;
            this.userId = userId;
            this.cancellation = cancellation;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getUserId() {
            return userId;
        }

        public RunCancellation getCancellation() {
            return cancellation;
        }

        public ConcurrentLinkedQueue<PendingInjectMessage> getPendingInjects() {
            return pendingInjects;
        }

        public AgentContext getAgentContext() {
            return agentContext;
        }

        public void setAgentContext(AgentContext agentContext) {
            this.agentContext = agentContext;
        }

        public AgentMessageStream getStream() {
            return stream.get();
        }

        public void setStream(AgentMessageStream stream) {
            this.stream.set(stream);
        }

        private boolean detachStream(AgentMessageStream expected) {
            return stream.compareAndSet(expected, null);
        }
    }

    private final Map<String, ActiveRun> byRequestId = new ConcurrentHashMap<>();
    /** sessionId → requestId，供 GET 旁观按会话挂流，并保证同一会话只有一个活跃 run。 */
    private final Map<String, String> bySessionId = new ConcurrentHashMap<>();

    @Resource
    private PendingUserQuestionRegistry pendingUserQuestionRegistry;

    @Resource
    private PendingPlanApprovalRegistry pendingPlanApprovalRegistry;

    @Resource
    private org.wwz.ai.domain.agent.runtime.planmode.IPlanApprovalRepository planApprovalRepository;

    /**
     * @deprecated 使用 {@link #begin(String, String, String)}。仍转调三参数方法。
     */
    @Deprecated
    public ActiveRun begin(String requestId, String sessionId) {
        return begin(requestId, sessionId, null);
    }

    /**
     * 注册活跃 run。同一 session 若已有另一个仍存活的 requestId，抛出 {@link AgentConcurrentRunException}。
     * 同一 userId 的不同 session 可以同时 begin。
     */
    public ActiveRun begin(String requestId, String sessionId, String userId) {
        // begin 只建立进程内索引和取消令牌，不代表 ledger 已初始化；真正的运行账本
        // 仍由执行节点负责创建，避免取消注册表承担持久化职责。
        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("requestId 不能为空");
        }
        String rid = requestId.trim();
        ActiveRun existingRun = byRequestId.get(rid);
        if (existingRun != null) {
            return existingRun;
        }
        String sid = StringUtils.trimToNull(sessionId);
        if (sid != null) {
            String existingRequestId = bySessionId.get(sid);
            if (existingRequestId != null
                    && !existingRequestId.equals(rid)
                    && byRequestId.containsKey(existingRequestId)) {
                ActiveRun existing = byRequestId.get(existingRequestId);
                String existingSession = existing == null || StringUtils.isBlank(existing.getSessionId())
                        ? sid
                        : existing.getSessionId();
                log.warn("reject concurrent run sessionId={} activeRequestId={} activeSessionId={} newRequestId={}",
                        sid, existingRequestId, existingSession, rid);
                throw new AgentConcurrentRunException(
                        CONCURRENT_RUN_MESSAGE,
                        existingRequestId,
                        existingSession);
            }
        }

        String vid = StringUtils.trimToNull(userId);
        RunCancellation cancellation = new RunCancellation();
        ActiveRun run = new ActiveRun(rid, sessionId, vid, cancellation);
        ActiveRun previous = byRequestId.put(rid, run);
        if (previous != null) {
            log.warn("replace active run record requestId={}", rid);
        }
        if (sid != null) {
            bySessionId.put(sid, rid);
        }
        return run;
    }

    public void bindContext(String requestId, AgentContext agentContext) {
        // Context 在执行树准备完成后才绑定，因此 stop 可能先于 bind 到达；这种情况下
        // 取消令牌仍然有效，后续 bind 会把同一令牌注入 context。
        // inject 队列与 cancel 同理：bind 前到达的指导消息保留在 ActiveRun 队列中。
        ActiveRun run = byRequestId.get(requestId);
        if (run == null || agentContext == null) {
            return;
        }
        run.setAgentContext(agentContext);
        agentContext.setRunCancellation(run.getCancellation());
        agentContext.bindPendingInjectQueue(run.getPendingInjects());
    }

    public void bindStream(String requestId, AgentMessageStream stream) {
        // SSE 只负责承载观察结果。客户端断开后解绑当前流，但不能影响仍在后台执行的
        // run；真正的取消只允许由显式 stop 入口触发。
        ActiveRun run = byRequestId.get(requestId);
        if (run == null) {
            return;
        }
        run.setStream(stream);
        if (stream != null) {
            // 回调捕获具体流实例，旧连接的迟到 abort 不能清掉后来绑定的新连接。
            stream.onAbort(() -> detachStream(requestId, stream));
        }
    }

    /**
     * 解绑已经断开的观察流，但保留 run、取消令牌和 Agent 上下文。
     *
     * @return true 表示当前绑定的正是 expectedStream，并且已完成解绑
     */
    public boolean detachStream(String requestId, AgentMessageStream expectedStream) {
        if (StringUtils.isBlank(requestId) || expectedStream == null) {
            return false;
        }
        ActiveRun run = byRequestId.get(requestId.trim());
        if (run == null || !run.detachStream(expectedStream)) {
            return false;
        }
        log.info("detach agent run stream requestId={}", requestId);
        return true;
    }

    /**
     * @return true 若首次成功取消
     */
    public boolean cancel(String requestId, String reason) {
        // 取消顺序是“原子置位 -> 解除交互等待”。先置位保证并发的显式 stop
        // 只有一个调用者执行清理。detached 后台任务不级联停止。
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
        if (planApprovalRepository != null) {
            planApprovalRepository.cancelBySourceRequestId(requestId, reason);
        } else if (pendingPlanApprovalRegistry != null) {
            pendingPlanApprovalRegistry.cancelByRequestId(requestId, reason);
        }
        // detached 后台子 Agent 不随父 stop 级联；父占用释放后子任务继续。
        return true;
    }

    public Optional<ActiveRun> find(String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(byRequestId.get(requestId.trim()));
    }

    public Optional<ActiveRun> findBySessionId(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return Optional.empty();
        }
        String requestId = bySessionId.get(sessionId.trim());
        if (StringUtils.isBlank(requestId)) {
            return Optional.empty();
        }
        return find(requestId);
    }

    public void end(String requestId) {
        // end 只移除进程内索引；run 的最终状态已经由 ledger finishRun 持久化，不能
        // 因为内存清理而丢失历史查询所需事实。
        if (StringUtils.isBlank(requestId)) {
            return;
        }
        String rid = requestId.trim();
        ActiveRun removed = byRequestId.remove(rid);
        if (removed != null && StringUtils.isNotBlank(removed.getSessionId())) {
            bySessionId.remove(removed.getSessionId().trim(), rid);
        }
    }

    public boolean isCancelled(String requestId) {
        ActiveRun run = byRequestId.get(StringUtils.trimToEmpty(requestId));
        return run != null && run.getCancellation().isCancelled();
    }
}

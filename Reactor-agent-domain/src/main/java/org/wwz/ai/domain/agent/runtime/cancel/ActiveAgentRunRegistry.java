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
import java.util.concurrent.atomic.AtomicReference;

/**
 * 进程内活跃 run 索引：requestId → 上下文/取消标志/当前观察流。
 * 供 POST /stop 定位当前执行，也供 SSE 断开时解绑观察流。
 */
@Slf4j
@Component
public class ActiveAgentRunRegistry {

    public static final class ActiveRun {
        private final String requestId;
        private final String sessionId;
        private final RunCancellation cancellation;
        private volatile AgentContext agentContext;
        private final AtomicReference<AgentMessageStream> stream = new AtomicReference<>();

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

    @Resource
    private PendingUserQuestionRegistry pendingUserQuestionRegistry;

    @Resource
    private PendingPlanApprovalRegistry pendingPlanApprovalRegistry;

    public ActiveRun begin(String requestId, String sessionId) {
        // begin 只建立进程内索引和取消令牌，不代表 ledger 已初始化；真正的运行账本
        // 仍由执行节点负责创建，避免取消注册表承担持久化职责。
        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("requestId 不能为空");
        }
        RunCancellation cancellation = new RunCancellation();
        ActiveRun run = new ActiveRun(requestId, sessionId, cancellation);
        byRequestId.put(requestId, run);
        return run;
    }

    public void bindContext(String requestId, AgentContext agentContext) {
        // Context 在执行树准备完成后才绑定，因此 stop 可能先于 bind 到达；这种情况下
        // 取消令牌仍然有效，后续 bind 会把同一令牌注入 context。
        ActiveRun run = byRequestId.get(requestId);
        if (run == null || agentContext == null) {
            return;
        }
        run.setAgentContext(agentContext);
        agentContext.setRunCancellation(run.getCancellation());
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
        // 取消顺序是“原子置位 -> 解除交互等待 -> 停止后台任务”。先置位保证并发的
        // 显式 stop 只有一个调用者执行清理，其余调用只观察已取消状态。
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
        // end 只移除进程内索引；run 的最终状态已经由 ledger finishRun 持久化，不能
        // 因为内存清理而丢失历史查询所需事实。
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

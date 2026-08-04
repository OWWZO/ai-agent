package org.wwz.ai.application.agent.run;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;
import org.wwz.ai.domain.agent.runtime.printer.Printer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 用户主动停止本轮 Agent run（协作式取消）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunStopApplicationService {

    private final ActiveAgentRunRegistry activeAgentRunRegistry;

    public Map<String, Object> stop(String sessionId, String requestId) {
        // 停止是协作式的：先校验 request/session 归属，再向活动 run 发取消信号，
        // 最后写 stopped ledger、通知客户端并关闭流；底层 HTTP/工具是否立即中断由各自实现决定。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("sessionId", sessionId);

        if (StringUtils.isBlank(requestId)) {
            result.put("stopped", false);
            result.put("message", "requestId 不能为空");
            return result;
        }

        Optional<ActiveAgentRunRegistry.ActiveRun> found = activeAgentRunRegistry.find(requestId);
        if (found.isEmpty()) {
            result.put("stopped", false);
            result.put("message", "未找到进行中的 run（可能已结束）");
            return result;
        }

        ActiveAgentRunRegistry.ActiveRun run = found.get();
        if (StringUtils.isNotBlank(sessionId)
                && StringUtils.isNotBlank(run.getSessionId())
                && !sessionId.equals(run.getSessionId())) {
            result.put("stopped", false);
            result.put("message", "sessionId 与 run 不匹配");
            return result;
        }

        boolean first = activeAgentRunRegistry.cancel(requestId, RunCancellation.REASON_USER_STOP);
        AgentContext ctx = run.getAgentContext();
        if (ctx != null) {
            // finishRun 只记录一次用户停止事实，Printer 通知是即时 UI 反馈，两者职责不同，
            // 不能用关闭 SSE 代替 ledger 状态迁移。
            ExecutionLedgerRunSupport.finishRun(
                    ctx,
                    ExecutionLedgerConstants.STATUS_STOPPED,
                    null,
                    "USER_STOP",
                    "用户停止本轮对话");
            notifyStopped(ctx);
        }

        AgentMessageStream stream = run.getStream();
        if (stream != null) {
            try {
                stream.complete();
            } catch (Exception e) {
                log.warn("complete stream after stop failed, requestId={}", requestId, e);
            }
        }

        result.put("stopped", true);
        result.put("firstCancel", first);
        result.put("message", first ? "已请求停止" : "run 已在停止中");
        return result;
    }

    private void notifyStopped(AgentContext ctx) {
        Printer printer = ctx.getPrinter();
        if (printer == null) {
            return;
        }
        try {
            printer.send("result", "已停止。你可以继续发送新消息。");
        } catch (Exception e) {
            log.debug("send stop result skipped, requestId={}", ctx.getRequestId());
        }
    }
}

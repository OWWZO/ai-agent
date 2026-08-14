package org.wwz.ai.application.agent.run;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.cancel.PendingInjectMessage;
import org.wwz.ai.domain.agent.runtime.printer.Printer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 向进行中的 Agent run 注入用户指导（控制面，不 begin 新 run）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunInjectApplicationService {

    private static final int MAX_TEXT_CHARS = 8000;

    private final ActiveAgentRunRegistry activeAgentRunRegistry;

    public Map<String, Object> inject(String sessionId, String requestId, String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("sessionId", sessionId);

        if (StringUtils.isBlank(requestId)) {
            result.put("accepted", false);
            result.put("message", "requestId 不能为空");
            return result;
        }
        String body = StringUtils.trimToEmpty(text);
        if (StringUtils.isBlank(body)) {
            result.put("accepted", false);
            result.put("message", "text 不能为空");
            return result;
        }
        if (body.length() > MAX_TEXT_CHARS) {
            body = body.substring(0, MAX_TEXT_CHARS);
        }

        Optional<ActiveAgentRunRegistry.ActiveRun> found = activeAgentRunRegistry.find(requestId);
        if (found.isEmpty()) {
            result.put("accepted", false);
            result.put("message", "未找到进行中的 run（可能已结束）");
            return result;
        }

        ActiveAgentRunRegistry.ActiveRun run = found.get();
        if (StringUtils.isNotBlank(sessionId)
                && StringUtils.isNotBlank(run.getSessionId())
                && !sessionId.equals(run.getSessionId())) {
            result.put("accepted", false);
            result.put("message", "sessionId 与 run 不匹配");
            return result;
        }
        if (run.getCancellation() != null && run.getCancellation().isCancelled()) {
            result.put("accepted", false);
            result.put("message", "run 已在停止中，无法注入");
            return result;
        }

        PendingInjectMessage message = PendingInjectMessage.builder()
                .text(body)
                .source(PendingInjectMessage.SOURCE_USER)
                .createdAtMs(System.currentTimeMillis())
                .build();
        run.getPendingInjects().offer(message);

        AgentContext ctx = run.getAgentContext();
        if (ctx != null) {
            // bind 后 context 与 ActiveRun 共享队列；再 offer 会重复，上面已 offer 到共享队列
            notifyInjected(ctx, body);
        }

        log.info("inject accepted requestId={} sessionId={} chars={}", requestId, run.getSessionId(), body.length());
        result.put("accepted", true);
        result.put("message", "已接受指导，将在下一步生效");
        result.put("queued", true);
        return result;
    }

    private void notifyInjected(AgentContext ctx, String text) {
        Printer printer = ctx.getPrinter();
        if (printer == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("text", text);
            payload.put("source", PendingInjectMessage.SOURCE_USER);
            payload.put("status", "queued");
            printer.send("user_inject", payload);
        } catch (Exception e) {
            log.debug("send user_inject skipped, requestId={}", ctx.getRequestId());
        }
    }
}

package org.wwz.ai.domain.agent.runtime.agent;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayTiming;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.printer.Printer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用的 SSE 事件装配：running / success / cancelled / failed 四个终态。
 * <p>
 * 协议字段集中在这里，前端卡片依赖它渲染工具条；账本与执行逻辑不感知协议细节。
 */
final class ToolCallEventEmitter {

    private final BaseAgent agent;
    private final ToolTimingTracker timings;
    /** provider 判定复用账本口径，保证前端卡片与 ai_agent_tool_invocation 永远同源。 */
    private final ToolLedgerRecorder ledger;

    ToolCallEventEmitter(BaseAgent agent, ToolTimingTracker timings, ToolLedgerRecorder ledger) {
        this.agent = agent;
        this.timings = timings;
        this.ledger = ledger;
    }

    void emitRunning(List<ToolCall> commands,
                     Map<String, ToolDispatch> dispatches,
                     Map<String, Integer> dispatchIndexMapping) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (ToolCall command : commands) {
            ToolDispatch dispatch = dispatches == null ? null : dispatches.get(command == null ? null : command.getId());
            emit(command, dispatch, dispatchIndexMapping.get(command == null ? null : command.getId()),
                    "running", false, null);
        }
    }

    void emitFinished(ToolCall command,
                      ToolDispatch dispatch,
                      Integer dispatchIndex,
                      ToolExecutionOutcome outcome) {
        String status = outcome != null && outcome.isSuccess()
                ? "success"
                : ToolExecutionPipeline.isUserStop(outcome) ? "cancelled" : "failed";
        emit(command, dispatch, dispatchIndex, status, true, outcome);
    }

    private void emit(ToolCall command,
                      ToolDispatch dispatch,
                      Integer dispatchIndex,
                      String status,
                      boolean isFinal,
                      ToolExecutionOutcome outcome) {
        Printer printer = agent.getPrinter();
        if (printer == null || command == null || command.getFunction() == null) {
            return;
        }
        String toolCallId = command.getId();
        String toolName = dispatch == null ? command.getFunction().getName() : dispatch.toolName;
        if (StringUtils.isBlank(toolCallId) || StringUtils.isBlank(toolName)) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageType", "tool_call");
        payload.put("status", status);
        payload.put("toolName", toolName);
        payload.put("toolCallId", toolCallId);
        payload.put("streamToolKey", toolCallId);
        payload.put("toolProvider", ledger.providerOf(toolName));
        if (dispatchIndex != null) {
            payload.put("dispatchIndex", dispatchIndex);
        }

        AgentContext context = agent.getContext();
        Long toolInvocationId = context == null || context.getAgentRunState() == null
                ? null
                : context.getAgentRunState().resolveToolInvocationId(toolCallId);
        if (toolInvocationId != null) {
            payload.put("toolInvocationId", String.valueOf(toolInvocationId));
        }

        // 解包后的入参给前端卡片；Memory 仍保留模型发出的 ToolCall。
        String rawArguments = dispatch == null ? command.getFunction().getArguments() : dispatch.inputJson;
        if (StringUtils.isNotBlank(rawArguments)) {
            payload.put("argumentsText", rawArguments);
            payload.put("argumentsRaw", rawArguments);
        }

        Object input = ToolCallJson.parseOrNull(rawArguments);
        if (input != null) {
            payload.put("input", input);
        }

        payload.put("summary", buildSummary(toolName, status));
        payload.put("isFinal", isFinal);

        ReplayTiming timing = outcome == null ? timings.resolve(command) : outcome.getTiming();
        if (timing != null) {
            payload.put("timing", ReplayTiming.copyOf(timing));
        }

        if (outcome != null && StringUtils.isNotBlank(outcome.getErrorMsg())) {
            payload.put("errorMsg", outcome.getErrorMsg());
        }

        printer.send(toolCallId, "tool_call", payload, isFinal);
    }

    private static String buildSummary(String toolName, String status) {
        if ("success".equals(status)) {
            return toolName + " 调用完成";
        }
        if ("cancelled".equals(status)) {
            return toolName + " 已取消";
        }
        if ("failed".equals(status)) {
            return toolName + " 调用失败";
        }
        return "正在调用 " + toolName;
    }
}

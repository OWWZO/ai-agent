package org.wwz.ai.domain.agent.runtime.agent;

/**
 * 一个 tool call 解析后的派发目标：真实工具名、已解析入参，以及可在执行前确定的早退结果。
 * <p>
 * {@code inputJson} 始终是归一化后的合法 JSON 文本，供账本与 SSE 卡片直接使用。
 */
final class ToolDispatch {

    final String toolName;
    final Object args;
    final boolean argsReady;
    final String inputJson;
    final ToolExecutionOutcome earlyOutcome;

    ToolDispatch(String toolName,
                 Object args,
                 boolean argsReady,
                 String inputJson,
                 ToolExecutionOutcome earlyOutcome) {
        this.toolName = toolName;
        this.args = args;
        this.argsReady = argsReady;
        this.inputJson = inputJson;
        this.earlyOutcome = earlyOutcome;
    }
}

package org.wwz.ai.domain.agent.ledger.model;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/**
 * 执行账本常量。
 */
public final class ExecutionLedgerConstants {

    private ExecutionLedgerConstants() {
    }

    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_TIMEOUT = 3;
    public static final int STATUS_STOPPED = 4;
    /** 执行片段已结束，等待外部输入（AskUserQuestion 等）；不算 RUNNING，不算失败 */
    public static final int STATUS_WAITING_INPUT = 5;

    public static final String CALL_KIND_ASK = "ask";
    public static final String CALL_KIND_ASK_TOOL = "askTool";
    public static final String CALL_KIND_INTERNAL_DIGITAL_EMPLOYEE = "internalDigitalEmployee";
    /** 内部会话压缩摘要，不进入 UI 回放主语义。 */
    public static final String CALL_KIND_INTERNAL_COMPACT = "internalCompact";

    public static final String ENTRY_AGENT_REACT = "react";
    public static final String ENTRY_AGENT_PLAN_SOLVE = "plan_solve";

    public static final String ARTIFACT_ROLE_INPUT = "input";
    public static final String ARTIFACT_ROLE_OUTPUT = "output";

    public static final String VISIBILITY_VISIBLE = "visible";
    public static final String VISIBILITY_INTERNAL = "internal";

    public static final String SOURCE_TYPE_USER_UPLOAD = "user_upload";
    public static final String SOURCE_TYPE_TOOL_OUTPUT = "tool_output";

    public static final String REQUEST_SOURCE_AGENT = "agent";
    public static final String REQUEST_SOURCE_WORKSPACE = "workspace";

    public static final String TOOL_PROVIDER_LOCAL = "local";
    public static final String TOOL_PROVIDER_MCP = "mcp";

    /**
     * 根据异常推导终态。用户取消不是失败，也不能被超时覆盖成 TIMEOUT。
     */
    public static int resolveFailureStatus(Throwable throwable) {
        boolean timeout = false;
        boolean cancelled = false;
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                timeout = true;
            }
            if (current instanceof CancellationException || current instanceof InterruptedException) {
                cancelled = true;
            }
            Throwable next = current.getCause();
            current = next == current ? null : next;
        }
        if (cancelled && !timeout) {
            return STATUS_STOPPED;
        }
        return timeout ? STATUS_TIMEOUT : STATUS_FAILED;
    }
}

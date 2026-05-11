package org.wwz.ai.domain.agent.ledger.model;

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

    public static final String CALL_KIND_ASK = "ask";
    public static final String CALL_KIND_ASK_TOOL = "askTool";
    public static final String CALL_KIND_INTERNAL_DIGITAL_EMPLOYEE = "internalDigitalEmployee";

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
     * 根据异常推导失败状态。
     */
    public static int resolveFailureStatus(Throwable throwable) {
        return throwable instanceof TimeoutException ? STATUS_TIMEOUT : STATUS_FAILED;
    }
}

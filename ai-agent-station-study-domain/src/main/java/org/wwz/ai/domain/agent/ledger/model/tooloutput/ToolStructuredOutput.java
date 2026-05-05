package org.wwz.ai.domain.agent.ledger.model.tooloutput;

/**
 * rich tool 强类型输出根契约。
 */
public interface ToolStructuredOutput {

    /**
     * 返回稳定 toolName，用于 writer/reader 路由。
     */
    String getToolName();
}

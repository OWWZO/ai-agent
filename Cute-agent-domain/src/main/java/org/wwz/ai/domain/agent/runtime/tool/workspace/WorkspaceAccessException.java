package org.wwz.ai.domain.agent.runtime.tool.workspace;

/**
 * 工作区路径越界或参数非法时抛出。
 */
public class WorkspaceAccessException extends RuntimeException {

    public WorkspaceAccessException(String message) {
        super(message);
    }

    public WorkspaceAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}

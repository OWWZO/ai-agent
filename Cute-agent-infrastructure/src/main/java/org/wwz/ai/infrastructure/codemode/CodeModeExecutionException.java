package org.wwz.ai.infrastructure.codemode;

import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeErrorCode;

/**
 * Code Mode 执行期异常：携带结构化错误码，由 {@link GraalJsCodeModeExecutor} 统一转成 {@code CodeModeResult}。
 */
public class CodeModeExecutionException extends RuntimeException {

    private final CodeModeErrorCode errorCode;

    public CodeModeExecutionException(CodeModeErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CodeModeExecutionException(CodeModeErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public CodeModeErrorCode getErrorCode() {
        return errorCode;
    }
}

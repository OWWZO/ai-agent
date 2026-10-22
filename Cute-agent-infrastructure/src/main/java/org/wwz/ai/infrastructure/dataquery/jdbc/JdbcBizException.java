package org.wwz.ai.infrastructure.dataquery.jdbc;

public class JdbcBizException extends RuntimeException {
    public JdbcBizException(String message) {
        super(message);
    }

    public JdbcBizException(String message, Throwable cause) {
        super(message, cause);
    }

    public JdbcBizException(Throwable cause) {
        super(cause);
    }

    public JdbcBizException() {
    }
}

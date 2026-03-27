package org.wwz.ai.domain.agent.reactor.data.exception;

/**
 * @author: jinglingtuan
 * @date: 2023/4/25 20:45
 * @version: 1.0
 */
public class JdbcBizException  extends RuntimeException{
    public JdbcBizException(String message) {
        super(message);
    }

    public JdbcBizException(String message, Throwable e) {
        super(message, e);
    }

    public JdbcBizException(Throwable e) {
        super(e);
    }

    public JdbcBizException() {
    }
}

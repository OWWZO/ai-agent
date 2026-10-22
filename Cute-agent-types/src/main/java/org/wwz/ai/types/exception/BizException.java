package org.wwz.ai.types.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 可预期的业务规则异常。
 *
 * <p>与系统异常不同，它携带稳定业务码和可展示说明，适合由 Controller/网关转换成正常的失败响应。</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class BizException extends RuntimeException{

    @Serial
    private static final long serialVersionUID = 5317680961212299217L;

    /** 异常码 */
    private String code;

    /** 异常信息 */
    private String info;

    public BizException(String code) {
        this.code = code;
    }

    public BizException(String code, Throwable cause) {
        this.code = code;
        super.initCause(cause);
    }

    public BizException(String code, String message) {
        this.code = code;
        this.info = message;
    }

    public BizException(String code, String message, Throwable cause) {
        this.code = code;
        this.info = message;
        super.initCause(cause);
    }

    @Override
    public String toString() {
        return "org.wwz.ai.types.exception.BizException{" +
                "code='" + code + '\'' +
                ", info='" + info + '\'' +
                '}';
    }

}

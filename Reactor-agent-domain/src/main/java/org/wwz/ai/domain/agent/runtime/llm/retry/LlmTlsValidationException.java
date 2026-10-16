package org.wwz.ai.domain.agent.runtime.llm.retry;

/**
 * TLS 证书校验失败的稳定标记异常。
 *
 * <p>移植自 ZCode 的 {@code failure-tls.ts}：在 provider 适配层（infrastructure）捕获原始 TLS 异常后
 * 包一层这个类型，分类链路只认这个类型 + 证书异常的类层次，不再依赖 JDK 的英文错误文案。
 *
 * <p>与「握手被打断」区分：握手被打断是可重试瞬态故障（见 {@link LlmFailureInspector}），
 * 证书校验失败是配置性问题，重试永远不会成功。
 */
public class LlmTlsValidationException extends RuntimeException {

    /** 稳定错误码，写进日志便于跨版本对账。 */
    public static final String CODE = "MODEL_TLS_VALIDATION_FAILED";

    public LlmTlsValidationException(Throwable cause) {
        super("TLS validation failed for the provider request.", cause);
    }

    public LlmTlsValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public String code() {
        return CODE;
    }
}

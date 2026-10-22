package org.wwz.ai.domain.agent.runtime.llm.retry;

/**
 * 模型请求失败的归因原因。
 *
 * <p>移植自 ZCode 的 {@code ModelFailureReason}。与 {@link LlmErrorCode} 的分工：
 * errorCode 回答「这是什么错误」，reason 回答「该不该重试 / 该切模型还是该找人」。
 */
public enum LlmFailureReason {

    /** 传输层网络故障（连不上、连接重置、DNS 失败）。 */
    NETWORK_ERROR,
    /** 代理链路故障。 */
    PROXY_ERROR,
    /** TLS 证书校验失败（配置性问题，重试无意义）。 */
    TLS_ERROR,
    /** 请求超时。 */
    TIMEOUT,
    /** 被限流。 */
    RATE_LIMITED,
    /** provider 过载（529 / overloaded）。 */
    PROVIDER_OVERLOADED,
    /** provider 5xx。 */
    SERVER_ERROR,
    /** 鉴权失败。 */
    AUTH_FAILED,
    /** 请求本身非法。 */
    INVALID_REQUEST,
    /** 上下文超限。 */
    CONTEXT_EXCEEDED,
    /** provider 未配置。 */
    PROVIDER_NOT_CONFIGURED,
    /** 请求被取消。 */
    CANCELLED,
    /** 无法归因。 */
    UNKNOWN
}

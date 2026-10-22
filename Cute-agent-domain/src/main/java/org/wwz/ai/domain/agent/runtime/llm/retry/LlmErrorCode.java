package org.wwz.ai.domain.agent.runtime.llm.retry;

/**
 * 模型请求失败的稳定错误码。
 *
 * <p>移植自 ZCode 的 {@code ModelErrorCode}：分类结果只对外暴露这些枚举值，
 * 调用方、日志与前端不再依赖上游返回的英文文案来判语义。
 */
public enum LlmErrorCode {

    /** 通用请求失败（网络、5xx、未知上游错误）。 */
    MODEL_REQUEST_FAILED,
    /** 超时（含流空闲超时）。 */
    MODEL_REQUEST_TIMEOUT,
    /** 被限流 / 并发或配额触顶。 */
    MODEL_RATE_LIMITED,
    /** 模型不存在或选型无效。 */
    MODEL_NOT_FOUND,
    /** 上下文窗口超限。 */
    MODEL_CONTEXT_EXCEEDED,
    /** 请求被取消（用户 stop / 任务取消）。 */
    MODEL_REQUEST_CANCELLED,
    /** provider 拒绝了请求本身（400/422 等）。 */
    INVALID_MODEL_REQUEST,
    /** 响应无法解析（空补全、非法 JSON 等）。 */
    INVALID_MODEL_RESPONSE,
    /** provider 未配置 / 鉴权失败。 */
    PROVIDER_NOT_CONFIGURED
}

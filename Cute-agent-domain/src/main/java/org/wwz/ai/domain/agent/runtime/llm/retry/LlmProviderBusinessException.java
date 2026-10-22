package org.wwz.ai.domain.agent.runtime.llm.retry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * provider 业务错误：上游以 2xx/4xx 返回体携带业务码（如 BigModel 的 1005/3001/1261）。
 *
 * <p>移植自 ZCode 的 {@code ProviderBusinessError}。provider 适配层（infrastructure）在识别到
 * 业务码后抛这个异常，分类链路据此走 {@link LlmProviderBusinessCodes} 映射表，
 * 而不是把它当成一个普通的运行时异常。
 *
 * <p>之所以单独建类型：业务码的语义（配额耗尽 vs 并发上限 vs 上下文超限）无法从
 * HTTP 状态码或异常文案反推，必须由最接近响应体的那一层显式携带上来。
 */
public class LlmProviderBusinessException extends RuntimeException {

    /** 包装码：外层 code 是这个值时，真实业务码要看 {@link #getProviderCode()}。 */
    public static final String CODE = "PROVIDER_BUSINESS_ERROR";

    private final String providerCode;
    private final String providerId;
    private final Integer statusCode;
    private final String providerRequestId;
    private final Map<String, String> responseHeaders;
    private final Boolean retryable;

    private LlmProviderBusinessException(Builder builder) {
        super(builder.message);
        this.providerCode = builder.providerCode;
        this.providerId = builder.providerId;
        this.statusCode = builder.statusCode;
        this.providerRequestId = builder.providerRequestId;
        this.responseHeaders = builder.responseHeaders == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.responseHeaders));
        this.retryable = builder.retryable;
    }

    public String code() {
        return CODE;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getProviderId() {
        return providerId;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    /** provider 显式标注的可重试标记，可为 null（未标注）。 */
    public Boolean getRetryable() {
        return retryable;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String message;
        private String providerCode;
        private String providerId;
        private Integer statusCode;
        private String providerRequestId;
        private Map<String, String> responseHeaders;
        private Boolean retryable;
        private Throwable cause;

        public Builder message(String value) {
            this.message = value;
            return this;
        }

        public Builder providerCode(String value) {
            this.providerCode = value;
            return this;
        }

        public Builder providerId(String value) {
            this.providerId = value;
            return this;
        }

        public Builder statusCode(Integer value) {
            this.statusCode = value;
            return this;
        }

        public Builder providerRequestId(String value) {
            this.providerRequestId = value;
            return this;
        }

        public Builder responseHeaders(Map<String, String> value) {
            this.responseHeaders = value;
            return this;
        }

        public Builder retryable(Boolean value) {
            this.retryable = value;
            return this;
        }

        public Builder cause(Throwable value) {
            this.cause = value;
            return this;
        }

        public LlmProviderBusinessException build() {
            LlmProviderBusinessException error = new LlmProviderBusinessException(this);
            if (cause != null) {
                error.initCause(cause);
            }
            return error;
        }
    }
}

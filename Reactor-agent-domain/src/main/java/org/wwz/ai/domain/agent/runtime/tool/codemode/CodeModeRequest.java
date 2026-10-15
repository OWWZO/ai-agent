package org.wwz.ai.domain.agent.runtime.tool.codemode;

import java.util.Map;

/**
 * Code Mode 执行请求。
 * <p>
 * 约束（由 {@link CodeModeRequestValidator} 统一执行）：
 * </p>
 * <ul>
 *   <li>{@code source} 与 {@code workspaceFile} 必须二选一，同时存在或同时为空都直接失败；</li>
 *   <li>{@code inputs} 只能是 JSON 对象（Map），不接收数组或标量；</li>
 *   <li>{@code timeoutMs} 由配置上限收敛。</li>
 * </ul>
 */
public record CodeModeRequest(
        String source,
        String workspaceFile,
        Map<String, Object> inputs,
        long timeoutMs
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String source;
        private String workspaceFile;
        private Map<String, Object> inputs;
        private long timeoutMs;

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder workspaceFile(String workspaceFile) {
            this.workspaceFile = workspaceFile;
            return this;
        }

        public Builder inputs(Map<String, Object> inputs) {
            this.inputs = inputs;
            return this;
        }

        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public CodeModeRequest build() {
            return new CodeModeRequest(source, workspaceFile, inputs, timeoutMs);
        }
    }
}

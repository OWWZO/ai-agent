package org.wwz.ai.domain.agent.runtime.openui;

/**
 * Autofix 的上下文轮次。只保留文本，工具轮与非文本 part 由调用方提前丢弃。
 */
public record OpenUiFixTurn(String role, String content) {
}

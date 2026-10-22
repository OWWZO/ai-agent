package org.wwz.ai.domain.agent.runtime.openui;

/**
 * Autofix 报出的单条错误，字段与 Thesys 的 FixError 一致。
 */
public record OpenUiFixError(String code, String message, String statementId) {
}

package org.wwz.ai.domain.agent.runtime.openui;

import java.util.List;

/**
 * Autofix 单次修复的结果。
 *
 * <p>语义与 Thesys 的 `fix_summary.status` 对齐：</p>
 * <ul>
 *   <li>{@code fixed} —— content 是修好的完整程序，替换原程序重渲染；</li>
 *   <li>{@code already_valid} —— 原程序本来就合法，content 原样返回，不收费；</li>
 *   <li>{@code fix_failed} —— content 为 null，errors 说明还错在哪；</li>
 *   <li>{@code disabled} —— 没配 key 或开关关着，调用方走降级显示；</li>
 *   <li>{@code error} —— 网络/HTTP/解析失败，message 里有原因。</li>
 * </ul>
 */
public record OpenUiFixResult(String status, String content, List<OpenUiFixError> errors, String message) {

    public static final String FIXED = "fixed";
    public static final String ALREADY_VALID = "already_valid";
    public static final String FIX_FAILED = "fix_failed";
    public static final String DISABLED = "disabled";
    public static final String ERROR = "error";

    public static OpenUiFixResult fixed(String content, List<OpenUiFixError> fixedErrors) {
        return new OpenUiFixResult(FIXED, content, fixedErrors, null);
    }

    public static OpenUiFixResult alreadyValid(String content) {
        return new OpenUiFixResult(ALREADY_VALID, content, List.of(), null);
    }

    public static OpenUiFixResult fixFailed(List<OpenUiFixError> unfixedErrors) {
        return new OpenUiFixResult(FIX_FAILED, null, unfixedErrors, null);
    }

    public static OpenUiFixResult disabled() {
        return new OpenUiFixResult(DISABLED, null, List.of(), "autofix 未启用");
    }

    public static OpenUiFixResult error(String message) {
        return new OpenUiFixResult(ERROR, null, List.of(), message);
    }

    /** 调用方是否需要替换渲染内容。 */
    public boolean hasRepairedContent() {
        return content != null && !content.isBlank() && !ERROR.equals(status) && !DISABLED.equals(status);
    }
}

package org.wwz.ai.domain.agent.runtime.openui;

import java.util.List;

/**
 * 意图修复（mode=intent）的单次结果。
 *
 * <p>后端没有 parser，只负责把模型输出解析成块并原样透传；
 * 唯一性 / 长度 / 重叠 / 最终可渲染性的验收全在前端。</p>
 *
 * <ul>
 *   <li>{@code intent_ok} —— blocks 至少一条，内容是 SEARCH/REPLACE 原文；</li>
 *   <li>{@code intent_empty} —— 模型没给出任何合法块，调用方带反馈重试；</li>
 *   <li>{@code disabled} —— intent-enabled 配置关闭，调用方回退整段 Autofix；</li>
 *   <li>{@code error} —— 模型目录无可用配置 / 请求失败，message 有原因。</li>
 * </ul>
 */
public record OpenUiIntentFixResult(String status, List<OpenUiRepairIntent> blocks, String model, String message) {

    public static final String INTENT_OK = "intent_ok";
    public static final String INTENT_EMPTY = "intent_empty";
    public static final String DISABLED = "disabled";
    public static final String ERROR = "error";

    public OpenUiIntentFixResult {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public static OpenUiIntentFixResult ok(List<OpenUiRepairIntent> blocks, String model) {
        return new OpenUiIntentFixResult(INTENT_OK, blocks, model, null);
    }

    public static OpenUiIntentFixResult empty(String model) {
        return new OpenUiIntentFixResult(INTENT_EMPTY, List.of(), model, "模型未输出任何合法的 SEARCH/REPLACE 块");
    }

    public static OpenUiIntentFixResult disabled() {
        return new OpenUiIntentFixResult(DISABLED, List.of(), null, "openui 意图修复未启用");
    }

    public static OpenUiIntentFixResult error(String message) {
        return new OpenUiIntentFixResult(ERROR, List.of(), null, message);
    }
}

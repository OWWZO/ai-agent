package org.wwz.ai.domain.agent.runtime.openui;

/**
 * 单条修复意图（plans/038 v4）：只有一个形态 —— SEARCH/REPLACE 块。
 *
 * <p>{@code searchText} 必须在原文中唯一出现（前端执行时校验），{@code replaceText}
 * 为空表示删除。原 v2 的 ARG（结构化参数意图）已废弃。</p>
 */
public record OpenUiRepairIntent(String searchText, String replaceText) {

    public OpenUiRepairIntent {
        searchText = searchText == null ? "" : searchText;
        replaceText = replaceText == null ? "" : replaceText;
    }
}

package org.wwz.ai.domain.agent.runtime.tool.common.websearch;

/**
 * 一条搜索结果。
 *
 * @param title   标题；缺失时由 provider 用 URL 兜底
 * @param url     结果链接，非空
 * @param snippet 摘要，可能为空
 */
public record SearchHit(String title, String url, String snippet) {
}

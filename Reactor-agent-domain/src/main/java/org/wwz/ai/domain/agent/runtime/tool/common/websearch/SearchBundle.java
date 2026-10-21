package org.wwz.ai.domain.agent.runtime.tool.common.websearch;

import java.util.List;

/**
 * 一次 provider 搜索的产出。
 *
 * @param hits    去重后的结果，可能为空
 * @param summary provider 给出的摘要；为 null 表示该 provider 不提供
 */
public record SearchBundle(List<SearchHit> hits, String summary) {
}

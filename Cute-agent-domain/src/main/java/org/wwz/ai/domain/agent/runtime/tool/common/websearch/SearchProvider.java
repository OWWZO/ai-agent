package org.wwz.ai.domain.agent.runtime.tool.common.websearch;

import java.util.List;

/**
 * Web 搜索后端 SPI。
 * <p>
 * 每个 provider 只负责「给定 query 与域名过滤，返回 hits/summary」，
 * 不关心优先级编排、失败续试和结果序列化——那些由 {@code WebSearchTool} 负责。
 */
public interface SearchProvider {

    /** provider 标识，用于配置模式匹配和结果里的 provider 字段。 */
    String name();

    /**
     * 执行一次搜索。
     *
     * @param request 查询与域名过滤
     * @return 结果；不提供摘要的 provider 返回 {@code summary=null}
     * @throws Exception provider 侧任何失败，由调用方决定是否续试下一个
     */
    SearchBundle search(SearchRequest request) throws Exception;

    /**
     * 一次搜索请求。
     *
     * @param query          原始查询
     * @param allowedDomains 白名单，空表示不限
     * @param blockedDomains 黑名单，空表示不排除
     * @param config         provider 的运行配置（key / endpoint / model）
     */
    record SearchRequest(String query,
                         List<String> allowedDomains,
                         List<String> blockedDomains,
                         ProviderConfig config) {
    }

    /**
     * provider 运行配置。字段按 provider 需要填充，未用到的为 null。
     */
    record ProviderConfig(String apiKey, String baseUrl, String interfaceUrl, String model) {
    }
}

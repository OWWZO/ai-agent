package org.wwz.ai.domain.agent.runtime.tool.common.social;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only Xueqiu stock and community operations. */
public class XueqiuTool extends AbstractSocialTool {

    public static final String TOOL_NAME = "xueqiu";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Read authenticated Xueqiu stock quotes and community data. "
                + "Use operation=quote|search|hot_posts|hot_stocks. "
                + "No trading, portfolio mutation, or account writes are supported.";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", enumProp(
                "Read operation.", List.of("quote", "search", "hot_posts", "hot_stocks")));
        properties.put("symbol", stringProp("Stock symbol such as SH600519 or SZ000858 for quote."));
        properties.put("query", stringProp("Stock code or name for operation=search."));
        properties.put("stock_type", integerProp("Hot-stock ranking type, commonly 10 or 12.", 1, 100));
        properties.put("limit", integerProp("Maximum number of results.", 1, 50));
        return objectSchema(properties, List.of("operation"));
    }

    @Override
    public Object execute(Object input) {
        return executeRemote(input);
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/xueqiu";
    }

    @Override
    protected String platformLabel() {
        return "雪球";
    }
}

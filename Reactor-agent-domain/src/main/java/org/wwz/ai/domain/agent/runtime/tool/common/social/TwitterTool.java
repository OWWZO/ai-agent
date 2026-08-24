package org.wwz.ai.domain.agent.runtime.tool.common.social;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only Twitter/X operations backed by the reactor-tool adapter. */
public class TwitterTool extends AbstractSocialTool {

    public static final String TOOL_NAME = "twitter";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Read authenticated Twitter/X content. "
                + "Use operation=search|tweet|thread|article|feed|timeline|user_posts. "
                + "Read-only; credentials are configured in reactor-tool environment variables.";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", enumProp(
                "Read operation.",
                List.of("search", "tweet", "thread", "article", "feed", "timeline", "user_posts")));
        properties.put("query", stringProp("Search query for operation=search."));
        properties.put("tweet_id", stringProp("Tweet ID or x.com URL for tweet/thread/article."));
        properties.put("username", stringProp("Username for operation=timeline or user_posts."));
        properties.put("limit", integerProp("Maximum number of list results.", 1, 50));
        return objectSchema(properties, List.of("operation"));
    }

    @Override
    public Object execute(Object input) {
        return executeRemote(input);
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/twitter";
    }

    @Override
    protected String platformLabel() {
        return "Twitter";
    }
}

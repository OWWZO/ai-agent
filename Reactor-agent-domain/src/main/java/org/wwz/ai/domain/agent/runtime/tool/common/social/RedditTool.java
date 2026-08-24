package org.wwz.ai.domain.agent.runtime.tool.common.social;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only Reddit operations backed by the in-process Reactor HTTP client. */
public class RedditTool extends AbstractSocialTool {

    public static final String TOOL_NAME = "reddit";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Read authenticated Reddit content. "
                + "Use operation=search|subreddit|popular|read|user_posts. "
                + "Read-only; posting, voting, saving, and browser-cookie access are unavailable.";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", enumProp(
                "Read operation.", List.of("search", "subreddit", "popular", "read", "user_posts")));
        properties.put("query", stringProp("Search query for operation=search."));
        properties.put("subreddit", stringProp("Subreddit name, with or without the r/ prefix."));
        properties.put("post_id", stringProp("Reddit post ID or URL for operation=read."));
        properties.put("username", stringProp("Username for operation=user_posts."));
        properties.put("sort", enumProp(
                "Sort mode.", List.of("relevance", "hot", "new", "top", "rising", "controversial", "best")));
        properties.put("time_filter", enumProp(
                "Search time filter.", List.of("hour", "day", "week", "month", "year", "all")));
        properties.put("limit", integerProp("Maximum number of results/comments.", 1, 100));
        return objectSchema(properties, List.of("operation"));
    }

    @Override
    public Object execute(Object input) {
        return executeRemote(input);
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/reddit";
    }

    @Override
    protected String platformLabel() {
        return "Reddit";
    }
}

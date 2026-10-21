package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.WebSearchTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSearchTool 入参校验回归。
 * <p>
 * 锁住一个容易被"顺手简化"破坏的语义：query 缺失时必须判定为非法入参，
 * 不能把 null 转成字面量 "null" 后当成查询词发给 provider。
 */
public class WebSearchToolQueryValidationTest {

    @Test
    public void shouldRejectMissingQueryInsteadOfSearchingForLiteralNull() {
        // 未注入 AgentContext：校验通过后会因缺少 ReactorConfig 而抛错，
        // 因此只要拿到"query 至少 2 个字符"就证明校验拦住了。
        WebSearchTool tool = new WebSearchTool();

        Object result = tool.execute(new LinkedHashMap<String, Object>());

        Assert.assertTrue(result instanceof ToolResultPayload);
        ToolResultPayload payload = (ToolResultPayload) result;
        Assert.assertTrue("缺失 query 必须被入参校验拦住，实际: " + payload.getToolResult(),
                payload.getToolResult().contains("query 至少 2 个字符"));
    }

    @Test
    public void shouldRejectExplicitNullQuery() {
        WebSearchTool tool = new WebSearchTool();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", null);

        Object result = tool.execute(input);

        ToolResultPayload payload = (ToolResultPayload) result;
        Assert.assertTrue("query=null 必须被入参校验拦住，实际: " + payload.getToolResult(),
                payload.getToolResult().contains("query 至少 2 个字符"));
    }

    @Test
    public void shouldRejectSingleCharacterQuery() {
        WebSearchTool tool = new WebSearchTool();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", "a");

        Object result = tool.execute(input);

        ToolResultPayload payload = (ToolResultPayload) result;
        Assert.assertTrue(payload.getToolResult().contains("query 至少 2 个字符"));
    }
}

package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;

/**
 * visitor/executor 属性对象默认值测试。
 */
public class AgentExecutorPropertiesBindingTest {

    @Test
    public void shouldExposeVisitorCookieDefaults() {
        AgentExecutorProperties properties = new AgentExecutorProperties();

        Assert.assertNotNull(properties.getVisitorCookie());
        Assert.assertEquals("ai_agent_visitor_token", properties.getVisitorCookie().getName());
        Assert.assertTrue(properties.getVisitorCookie().isHttpOnly());
        Assert.assertEquals("Lax", properties.getVisitorCookie().getSameSite());
        Assert.assertEquals("/", properties.getVisitorCookie().getPath());
        Assert.assertEquals(Long.valueOf(365L), properties.getVisitorCookie().getMaxAgeDays());
    }
}

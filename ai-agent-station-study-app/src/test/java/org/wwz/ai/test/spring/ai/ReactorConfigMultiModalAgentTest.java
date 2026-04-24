package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

/**
 * 多模态工具配置绑定测试。
 */
public class ReactorConfigMultiModalAgentTest {

    @Test
    public void shouldBindMultiModalAgentConfig() {
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setMultiModalAgentParams("""
                {"type":"object","properties":{"question":{"type":"string","description":"查询问题"}},"required":["question"]}
                """);
        reactorConfig.setMessageInterval("{\"knowledge\":\"1,4\"}");
        reactorConfig.setMultiAgentToolList("{\"default\":\"search,code,report,multimodalagent\"}");
        ReflectionTestUtils.setField(reactorConfig, "multiModalAgentDesc", "多模态知识检索工具");
        ReflectionTestUtils.setField(reactorConfig, "multiModalAgentUrl", "http://127.0.0.1:1601");

        Assert.assertEquals("多模态知识检索工具", reactorConfig.getMultiModalAgentDesc());
        Assert.assertEquals("http://127.0.0.1:1601", reactorConfig.getMultiModalAgentUrl());
        Assert.assertEquals("1,4", reactorConfig.getMessageInterval().get("knowledge"));
        Assert.assertTrue(reactorConfig.getMultiAgentToolListMap().get("default").contains("multimodalagent"));
        java.util.Map<?, ?> properties = (java.util.Map<?, ?>) reactorConfig.getMultiModalAgentParams().get("properties");
        java.util.Map<?, ?> question = (java.util.Map<?, ?>) properties.get("question");
        Assert.assertEquals("查询问题", String.valueOf(question.get("description")));
    }

    @Test
    public void shouldKeepMultiModalAgentParamsEmptyWhenJsonBlank() {
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setMultiModalAgentParams("{}");

        Assert.assertTrue(reactorConfig.getMultiModalAgentParams().isEmpty());
    }
}

package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.llm.DomainMessageConverter;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * DomainMessageConverter 测试
 */
public class DomainMessageConverterTest {

    @Test
    public void test_convertMessagesWithToolReplayAndImage() {
        DomainMessageConverter converter = new DomainMessageConverter();
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setSensitivePatterns("{}");
        ReflectionTestUtils.setField(converter, "reactorConfig", reactorConfig);

        ToolCall toolCall = ToolCall.builder()
                .id("call-1")
                .type("function")
                .function(ToolCall.Function.builder()
                        .name("deep_search")
                        .arguments("{\"query\":\"spring ai\"}")
                        .build())
                .build();

        List<org.springframework.ai.chat.messages.Message> converted = converter.convert(List.of(
                Message.systemMessage("你是一个助手", null),
                Message.userMessage("请看图", Base64.getEncoder().encodeToString("img".getBytes(StandardCharsets.UTF_8))),
                Message.fromToolCalls("我先搜索资料", List.of(toolCall)),
                Message.toolMessage("搜索完成", "call-1", null)
        ));

        Assert.assertEquals(4, converted.size());
        Assert.assertTrue(converted.get(0) instanceof SystemMessage);
        Assert.assertTrue(converted.get(1) instanceof UserMessage);
        Assert.assertTrue(((UserMessage) converted.get(1)).getMedia().size() == 1);

        AssistantMessage assistantMessage = (AssistantMessage) converted.get(2);
        Assert.assertEquals("我先搜索资料", assistantMessage.getText());
        Assert.assertEquals(1, assistantMessage.getToolCalls().size());
        Assert.assertEquals("deep_search", assistantMessage.getToolCalls().get(0).name());

        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) converted.get(3);
        Assert.assertEquals(1, toolResponseMessage.getResponses().size());
        Assert.assertEquals("call-1", toolResponseMessage.getResponses().get(0).id());
        Assert.assertEquals("deep_search", toolResponseMessage.getResponses().get(0).name());
        Assert.assertEquals("搜索完成", toolResponseMessage.getResponses().get(0).responseData());
    }
}

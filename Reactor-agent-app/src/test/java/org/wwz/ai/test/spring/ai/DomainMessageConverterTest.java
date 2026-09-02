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
        DomainMessageConverter converter = newConverter();

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

    @Test
    public void test_toolResultWithImageExpandsToMediaUserMessage() {
        DomainMessageConverter converter = newConverter();
        String dataUrl = "data:image/png;base64,"
                + Base64.getEncoder().encodeToString("png-bytes".getBytes(StandardCharsets.UTF_8));
        String observation = "{\"type\":\"image\",\"path\":\"shot.png\",\"mimeType\":\"image/png\",\"size\":9}";

        ToolCall toolCall = ToolCall.builder()
                .id("call-img")
                .type("function")
                .function(ToolCall.Function.builder()
                        .name("workspace_read")
                        .arguments("{\"path\":\"shot.png\"}")
                        .build())
                .build();

        List<org.springframework.ai.chat.messages.Message> converted = converter.convert(List.of(
                Message.fromToolCalls("read image", List.of(toolCall)),
                Message.toolMessage(observation, "call-img", dataUrl)
        ));

        Assert.assertEquals(3, converted.size());
        Assert.assertTrue(converted.get(0) instanceof AssistantMessage);

        ToolResponseMessage toolResponse = (ToolResponseMessage) converted.get(1);
        Assert.assertEquals("call-img", toolResponse.getResponses().get(0).id());
        Assert.assertEquals("workspace_read", toolResponse.getResponses().get(0).name());
        Assert.assertEquals(observation, toolResponse.getResponses().get(0).responseData());
        Assert.assertFalse(toolResponse.getResponses().get(0).responseData().contains("data:image"));

        UserMessage imageMessage = (UserMessage) converted.get(2);
        Assert.assertEquals(1, imageMessage.getMedia().size());
        Assert.assertEquals("image/png", imageMessage.getMedia().get(0).getMimeType().toString());
        Assert.assertTrue(imageMessage.getText().contains("workspace_read"));
        Assert.assertTrue(imageMessage.getText().contains("call-img"));
    }

    @Test
    public void test_orphanToolResultIsDroppedInsteadOfThrowing() {
        DomainMessageConverter converter = newConverter();

        List<org.springframework.ai.chat.messages.Message> converted = converter.convert(List.of(
                Message.userMessage("continue after compact", null),
                Message.toolMessage("search done", "call_i5AJ4CBehK9w8ThWiNnoaD3V", null)
        ));

        Assert.assertEquals(1, converted.size());
        Assert.assertTrue(converted.get(0) instanceof UserMessage);
        Assert.assertEquals("continue after compact", converted.get(0).getText());
    }

    private DomainMessageConverter newConverter() {
        DomainMessageConverter converter = new DomainMessageConverter();
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setSensitivePatterns("{}");
        ReflectionTestUtils.setField(converter, "reactorConfig", reactorConfig);
        return converter;
    }
}

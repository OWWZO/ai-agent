package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolChoice;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.llm.LlmToolCallbackProvider;
import org.wwz.ai.domain.agent.runtime.llm.OpenAiChatOptionsFactory;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAiChatOptionsFactory 测试
 */
public class OpenAiChatOptionsFactoryTest {

    @Test
    public void test_buildTextOptionsMapsStandardFieldsAndExtraBody() {
        OpenAiChatOptionsFactory factory = new OpenAiChatOptionsFactory();
        ReflectionTestUtils.setField(factory, "toolCallbackProvider", new LlmToolCallbackProvider());

        Map<String, Object> extParams = new LinkedHashMap<>();
        extParams.put("temperature", 0.9D);
        extParams.put("top_p", 0.7D);
        extParams.put("parallel_tool_calls", true);
        extParams.put("stop", List.of("END"));
        extParams.put("custom_flag", "demo");

        LLMSettings settings = LLMSettings.builder()
                .model("gpt-4o")
                .maxTokens(2048)
                .temperature(0.2D)
                .extParams(extParams)
                .build();

        var options = factory.buildTextOptions(settings, 0.3D);

        Assert.assertEquals("gpt-4o", options.getModel());
        Assert.assertEquals(Integer.valueOf(2048), options.getMaxTokens());
        Assert.assertEquals(Double.valueOf(0.9D), options.getTemperature());
        Assert.assertEquals(Double.valueOf(0.7D), options.getTopP());
        Assert.assertEquals(Boolean.TRUE, options.getParallelToolCalls());
        Assert.assertEquals(List.of("END"), options.getStop());
        Assert.assertEquals(Map.of("custom_flag", "demo"), options.getExtraBody());
    }

    @Test
    public void test_buildToolOptionsDoesNotForceToolChoiceWhenNoTools() {
        OpenAiChatOptionsFactory factory = new OpenAiChatOptionsFactory();
        ReflectionTestUtils.setField(factory, "toolCallbackProvider", new LlmToolCallbackProvider());

        LLMSettings settings = LLMSettings.builder()
                .model("gpt-4o")
                .maxTokens(1024)
                .temperature(0.1D)
                .build();

        var options = factory.buildToolOptions(settings, null, new ToolCollection(), ToolChoice.AUTO);

        Assert.assertNull(options.getToolChoice());
        Assert.assertEquals(Boolean.FALSE, options.getInternalToolExecutionEnabled());
        Assert.assertTrue(options.getToolCallbacks() == null || options.getToolCallbacks().isEmpty());
    }
}

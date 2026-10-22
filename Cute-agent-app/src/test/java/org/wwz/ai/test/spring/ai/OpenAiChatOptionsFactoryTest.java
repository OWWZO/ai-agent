package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.llm.LlmRequest;
import org.wwz.ai.infrastructure.llm.springai.SpringAiOptionsMapper;
import org.wwz.ai.infrastructure.llm.springai.SpringAiToolCallbackProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAiChatOptionsFactory 测试
 */
public class OpenAiChatOptionsFactoryTest {

    @Test
    public void test_buildTextOptionsMapsStandardFieldsAndExtraBody() {
        SpringAiOptionsMapper mapper = new SpringAiOptionsMapper(new SpringAiToolCallbackProvider());

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

        var options = mapper.map(LlmRequest.builder().model("gpt-4o").settings(settings)
                .temperature(0.3D).build());

        Assert.assertEquals("gpt-4o", options.getModel());
        Assert.assertEquals(Integer.valueOf(2048), options.getMaxTokens());
        Assert.assertEquals(Double.valueOf(0.9D), options.getTemperature());
        Assert.assertEquals(Double.valueOf(0.7D), options.getTopP());
        Assert.assertEquals(Boolean.TRUE, options.getParallelToolCalls());
        Assert.assertEquals(List.of("END"), options.getStop());
        Assert.assertEquals(Map.of("custom_flag", "demo"), options.getExtraBody());
    }

    @Test
    public void test_buildTextOptionsUsesConfiguredReasoningEffort() {
        SpringAiOptionsMapper mapper = new SpringAiOptionsMapper(new SpringAiToolCallbackProvider());

        LLMSettings settings = LLMSettings.builder()
                .model("gpt-5")
                .maxTokens(2048)
                .temperature(0.2D)
                .reasoningEffort("high")
                .build();

        var options = mapper.map(LlmRequest.builder().model("gpt-5").settings(settings).build());

        Assert.assertEquals("high", options.getReasoningEffort());
    }

    @Test
    public void test_buildToolOptionsDoesNotForceToolChoiceWhenNoTools() {
        SpringAiOptionsMapper mapper = new SpringAiOptionsMapper(new SpringAiToolCallbackProvider());

        LLMSettings settings = LLMSettings.builder()
                .model("gpt-4o")
                .maxTokens(1024)
                .temperature(0.1D)
                .build();

        var options = mapper.map(LlmRequest.builder().model("gpt-4o").settings(settings)
                .tools(List.of()).toolChoice("auto").build());

        Assert.assertNull(options.getToolChoice());
        Assert.assertEquals(Boolean.FALSE, options.getInternalToolExecutionEnabled());
        Assert.assertTrue(options.getToolCallbacks() == null || options.getToolCallbacks().isEmpty());
    }
}

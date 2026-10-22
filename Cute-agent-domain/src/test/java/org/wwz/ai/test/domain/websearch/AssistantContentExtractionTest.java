package org.wwz.ai.test.domain.websearch;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.common.websearch.SearchSupport;

/**
 * OpenAI chat completion 形状的 assistant 正文提取。
 * <p>
 * GptSearchProvider 与 GrokSearchProvider 曾各自私藏一份逐字相同的
 * extractAssistantContent，现已收敛到 {@link SearchSupport}。这组测试锁住
 * content 的四种形状（string / parts / 缺 message / 非标准类型），
 * 防止两个 Provider 的解析行为漂移。
 */
public class AssistantContentExtractionTest {

    @Test
    public void returnsPlainStringContent() {
        JSONObject root = json("""
                {"choices":[{"message":{"role":"assistant","content":"直接正文"}}]}
                """);

        Assert.assertEquals("直接正文", SearchSupport.extractAssistantContent(root));
    }

    @Test
    public void concatenatesStringParts() {
        JSONObject root = json("""
                {"choices":[{"message":{"content":["第一段","第二段"]}}]}
                """);

        Assert.assertEquals("第一段第二段", SearchSupport.extractAssistantContent(root));
    }

    @Test
    public void concatenatesTextFieldsFromObjectPartsAndSkipsBlank() {
        JSONObject root = json("""
                {"choices":[{"message":{"content":[{"type":"text","text":"有内容"},{"type":"text","text":"  "},{"type":"image"}]}}]}
                """);

        Assert.assertEquals("有内容", SearchSupport.extractAssistantContent(root));
    }

    @Test
    public void fallsBackToChoiceTextWhenMessageAbsent() {
        JSONObject root = json("""
                {"choices":[{"text":"choice 级正文"}]}
                """);

        Assert.assertEquals("choice 级正文", SearchSupport.extractAssistantContent(root));
    }

    @Test
    public void returnsEmptyForMissingOrMalformedShapes() {
        Assert.assertEquals("", SearchSupport.extractAssistantContent(json("{}")));
        Assert.assertEquals("", SearchSupport.extractAssistantContent(json("{\"choices\":[]}")));
        Assert.assertEquals("", SearchSupport.extractAssistantContent(json("{\"choices\":[null]}")));
        // choice 无 message 且无 text：getString 返回 null，由调用方的 isBlank 兜住
        Assert.assertNull(SearchSupport.extractAssistantContent(json("{\"choices\":[{}]}")));
        // content 是 number 等非标准类型：走 message.getString("content") 兜底
        Assert.assertEquals("42", SearchSupport.extractAssistantContent(
                json("{\"choices\":[{\"message\":{\"content\":42}}]}")));
    }

    private static JSONObject json(String text) {
        return JSON.parseObject(text);
    }
}

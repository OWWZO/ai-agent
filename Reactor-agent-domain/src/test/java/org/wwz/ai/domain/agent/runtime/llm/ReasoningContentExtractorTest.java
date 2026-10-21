package org.wwz.ai.domain.agent.runtime.llm;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 流式 reasoning chunk 的空白保护：token 边界的前导空格/换行不能被逐 chunk 吃掉，
 * 否则前端思考正文会粘成一行（英文模型每个 delta 通常带前导空格）。
 */
public class ReasoningContentExtractorTest {

    @Test
    public void metadataReasoningPreservesLeadingWhitespace() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ReasoningContentExtractor.METADATA_REASONING_CONTENT, " User");

        Assert.assertEquals(" User",
                ReasoningContentExtractor.extractReasoningMetadata(metadata));
    }

    @Test
    public void metadataReasoningPreservesNewlineAndTrailingSpace() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ReasoningContentExtractor.METADATA_REASONING_CONTENT, "first\n ");

        Assert.assertEquals("first\n ",
                ReasoningContentExtractor.extractReasoningMetadata(metadata));
    }

    @Test
    public void metadataReasoningNormalizesLiteralNull() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ReasoningContentExtractor.METADATA_REASONING_CONTENT, "null");

        Assert.assertNull(ReasoningContentExtractor.extractReasoningMetadata(metadata));
    }

    @Test
    public void metadataReasoningFallsBackToAlternateKeys() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("reasoning_content", " thinking");

        Assert.assertEquals(" thinking",
                ReasoningContentExtractor.extractReasoningMetadata(metadata));
    }

    @Test
    public void deltaReasoningKeepsWhitespace() {
        LlmStreamEvent event = LlmStreamEvent.builder().reasoningContent(" wants an ").build();

        Assert.assertEquals(" wants an ",
                ReasoningContentExtractor.extractDeltaReasoning(event));
    }

    @Test
    public void finalSplitStillTrimsEnds() {
        ReasoningContentExtractor.SplitResult split =
                ReasoningContentExtractor.split(" answer ", " thinking ");

        Assert.assertEquals("answer", split.content());
        Assert.assertEquals("thinking", split.reasoningContent());
    }
}

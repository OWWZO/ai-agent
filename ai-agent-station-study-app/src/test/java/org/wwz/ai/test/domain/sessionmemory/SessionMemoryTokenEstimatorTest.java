package org.wwz.ai.test.domain.sessionmemory;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionTurnMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptBlockType;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptContextBlock;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryTokenEstimator;

import java.util.List;

public class SessionMemoryTokenEstimatorTest {

    @Test
    public void test_estimateTurnTokens_skipsReferenceOnlyPayload() {
        SessionMemoryTokenEstimator estimator = new SessionMemoryTokenEstimator();
        SessionTurnMemory turn = SessionTurnMemory.builder()
                .blocks(List.of(
                        TranscriptContextBlock.builder()
                                .blockType(TranscriptBlockType.USER_INPUT)
                                .text("帮我解释什么是上帝类")
                                .build(),
                        TranscriptContextBlock.builder()
                                .blockType(TranscriptBlockType.TOOL_RESULT)
                                .text("已生成或更新产物：上帝类解释展示报告.html")
                                .resultPayloadJson("x".repeat(12000))
                                .referenceOnly(true)
                                .build(),
                        TranscriptContextBlock.builder()
                                .blockType(TranscriptBlockType.ASSISTANT_ANSWER)
                                .text("已为你生成 HTML 展示报告。")
                                .build()))
                .build();

        int estimatedTokens = estimator.estimateTurnTokens(turn);

        Assert.assertTrue("引用型大 payload 不应把单轮 token 估算放大到异常值", estimatedTokens < 200);
    }
}

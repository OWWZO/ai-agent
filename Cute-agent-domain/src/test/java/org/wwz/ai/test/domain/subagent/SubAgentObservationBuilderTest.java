package org.wwz.ai.test.domain.subagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentObservationBuilder;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentResult;

import java.util.Map;

/**
 * 子 Agent 结果观察载荷的字段契约。
 * <p>
 * 前台派发与后台结算共用 {@link SubAgentObservationBuilder}。这组测试锁住
 * 两者的共同字段集合、可选字段的判空规则，以及唯一差异
 * （后台额外标记 run_in_background），防止两条路径给主 Agent 的
 * observation 漂移。
 */
public class SubAgentObservationBuilderTest {

    @Test
    public void foregroundBuildOmitsBackgroundFlag() {
        Map<String, Object> body = SubAgentObservationBuilder.build(result(), "Agent", false);

        Assert.assertEquals("Agent", body.get("tool"));
        Assert.assertEquals(Boolean.TRUE, body.get("ok"));
        Assert.assertEquals(SubAgentResult.STATUS_COMPLETED, body.get("status"));
        Assert.assertEquals("agent-1", body.get("agentId"));
        Assert.assertEquals("Explore", body.get("agentType"));
        Assert.assertEquals("探索代码库", body.get("description"));
        Assert.assertEquals("找到了入口", body.get("content"));
        Assert.assertEquals(3, body.get("totalToolUseCount"));
        Assert.assertEquals(1500L, body.get("totalDurationMs"));
        Assert.assertFalse("前台不得标记 run_in_background", body.containsKey("run_in_background"));
    }

    @Test
    public void backgroundBuildMarksRunInBackground() {
        Map<String, Object> body = SubAgentObservationBuilder.build(result(), "Agent", true);

        Assert.assertEquals(Boolean.TRUE, body.get("run_in_background"));
        // 其余字段与前台一致
        Assert.assertEquals("Agent", body.get("tool"));
        Assert.assertEquals(SubAgentResult.STATUS_COMPLETED, body.get("status"));
    }

    @Test
    public void blankChildSessionIdIsOmitted() {
        Map<String, Object> blank = SubAgentObservationBuilder.build(
                SubAgentResult.builder()
                        .status(SubAgentResult.STATUS_COMPLETED)
                        .childSessionId("   ")
                        .build(),
                "Agent", false);
        Assert.assertFalse(blank.containsKey("childSessionId"));

        Map<String, Object> present = SubAgentObservationBuilder.build(
                SubAgentResult.builder()
                        .status(SubAgentResult.STATUS_COMPLETED)
                        .childSessionId("sub_agent-1")
                        .build(),
                "Agent", false);
        Assert.assertEquals("sub_agent-1", present.get("childSessionId"));
    }

    @Test
    public void nullMemoryPersistedIsOmittedButFalseIsKept() {
        // null 表示未尝试写入，false 表示写入失败——两者对主 Agent 语义不同，必须区分
        Map<String, Object> unset = SubAgentObservationBuilder.build(
                SubAgentResult.builder().status(SubAgentResult.STATUS_COMPLETED).build(),
                "Agent", false);
        Assert.assertFalse(unset.containsKey("memoryPersisted"));

        Map<String, Object> failed = SubAgentObservationBuilder.build(
                SubAgentResult.builder()
                        .status(SubAgentResult.STATUS_COMPLETED)
                        .memoryPersisted(Boolean.FALSE)
                        .build(),
                "Agent", false);
        Assert.assertEquals(Boolean.FALSE, failed.get("memoryPersisted"));
    }

    @Test
    public void blankErrorMsgIsOmitted() {
        Map<String, Object> ok = SubAgentObservationBuilder.build(result(), "Agent", false);
        Assert.assertFalse(ok.containsKey("errorMsg"));

        Map<String, Object> failed = SubAgentObservationBuilder.build(
                SubAgentResult.builder()
                        .status(SubAgentResult.STATUS_FAILED)
                        .errorMsg("子 Agent 超时")
                        .build(),
                "Agent", false);
        Assert.assertEquals("子 Agent 超时", failed.get("errorMsg"));
        Assert.assertEquals(Boolean.FALSE, failed.get("ok"));
    }

    @Test
    public void fieldOrderIsStableForPromptCache() {
        // 观察载荷进 prompt，字段顺序不稳定会破坏 cache 命中
        Map<String, Object> body = SubAgentObservationBuilder.build(result(), "Agent", true);
        Assert.assertEquals(
                java.util.List.of("tool", "ok", "status", "agentId", "agentType", "childSessionId",
                        "description", "content", "totalToolUseCount", "totalDurationMs",
                        "run_in_background", "memoryPersisted"),
                new java.util.ArrayList<>(body.keySet()));
    }

    private static SubAgentResult result() {
        return SubAgentResult.builder()
                .status(SubAgentResult.STATUS_COMPLETED)
                .agentId("agent-1")
                .agentType("Explore")
                .description("探索代码库")
                .childSessionId("sub_agent-1")
                .content("找到了入口")
                .totalToolUseCount(3)
                .totalDurationMs(1500L)
                .memoryPersisted(Boolean.TRUE)
                .build();
    }
}

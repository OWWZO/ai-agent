package org.wwz.ai.test.domain.sessionmemory;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.entity.AgentSessionMemory;
import org.wwz.ai.domain.agent.reactor.service.support.SessionArtifactRestoreSupport;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryCompactionService;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemorySummaryBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SessionMemoryCompactionServiceTest {

    @Test
    public void test_compactionKeepsRecentWindowAndShrinksPayload() {
        SessionMemoryCompactionService service = new SessionMemoryCompactionService();
        ReflectionTestUtils.setField(service, "reactorConfig", buildConfig(200));
        ReflectionTestUtils.setField(service, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(service, "summaryBuilder", new SessionMemorySummaryBuilder());

        AgentConversation conversation = AgentConversation.builder()
                .id(SessionMemoryTestSupport.CONVERSATION_ID)
                .sessionId(SessionMemoryTestSupport.SESSION_ID)
                .agentType(2)
                .build();

        List<AgentMessage> completedMessages = new ArrayList<>();
        Map<Long, List<AgentMessageEvent>> eventMap = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            long messageId = 100L + i;
            completedMessages.add(SessionMemoryTestSupport.completedMessage(
                    messageId,
                    "req-compact-" + i,
                    i,
                    buildLongText("用户问题-" + i, 500),
                    buildLongText("助手回答-" + i, 700),
                    null));
            if (i == 0) {
                eventMap.put(messageId, List.of(SessionMemoryTestSupport.artifactEvent(
                        messageId,
                        1,
                        "compact-report.html",
                        "https://file.example.com/compact-report")));
            }
        }

        SessionMemoryCompactionService.CompactionResult result = service.compact(
                conversation,
                null,
                completedMessages,
                eventMap);

        Assert.assertNotNull(result);
        Assert.assertEquals(Integer.valueOf(7), result.getBoundarySortOrder());
        Assert.assertEquals(Integer.valueOf(8), result.getSourceTurnCount());
        Assert.assertTrue(result.getArtifactRefsJson().contains("compact-report.html"));

        int fullPayloadTokens = estimateTokens(completedMessages);
        int compactedPayloadTokens = estimateCompactedTokens(result.getSummaryText(), completedMessages.subList(8, 10));
        Assert.assertTrue(compactedPayloadTokens * 100 <= fullPayloadTokens * 40);
    }

    @Test
    public void test_compactionBoundaryMovesForwardFromExistingSnapshot() {
        SessionMemoryCompactionService service = new SessionMemoryCompactionService();
        ReflectionTestUtils.setField(service, "reactorConfig", buildConfig(120));
        ReflectionTestUtils.setField(service, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(service, "summaryBuilder", new SessionMemorySummaryBuilder());

        AgentConversation conversation = AgentConversation.builder()
                .id(SessionMemoryTestSupport.CONVERSATION_ID)
                .sessionId(SessionMemoryTestSupport.SESSION_ID)
                .agentType(2)
                .build();
        AgentSessionMemory snapshot = SessionMemoryTestSupport.snapshot(
                "已有摘要",
                3,
                103L,
                List.of(SessionMemoryTestSupport.fact("goal", "沿用之前目标")),
                null);

        List<AgentMessage> completedMessages = List.of(
                SessionMemoryTestSupport.completedMessage(104L, "req-4", 4, buildLongText("问题4", 300), buildLongText("回答4", 450), null),
                SessionMemoryTestSupport.completedMessage(105L, "req-5", 5, buildLongText("问题5", 300), buildLongText("回答5", 450), null),
                SessionMemoryTestSupport.completedMessage(106L, "req-6", 6, buildLongText("问题6", 300), buildLongText("回答6", 450), null),
                SessionMemoryTestSupport.completedMessage(107L, "req-7", 7, buildLongText("问题7", 300), buildLongText("回答7", 450), null)
        );

        SessionMemoryCompactionService.CompactionResult result = service.compact(
                conversation,
                snapshot,
                completedMessages,
                Map.of());

        Assert.assertNotNull(result);
        Assert.assertTrue(result.getBoundarySortOrder() > snapshot.getBoundarySortOrder());
        Assert.assertEquals(Integer.valueOf(5), result.getBoundarySortOrder());
        Assert.assertEquals(Integer.valueOf(6), result.getSourceTurnCount());
        Assert.assertTrue(result.getSummaryText().contains("已有摘要"));
    }

    private ReactorConfig buildConfig(int thresholdTokens) {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "sessionMemoryCompactionThresholdTokens", thresholdTokens);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowTurns", 2);
        ReflectionTestUtils.setField(config, "sessionMemorySummaryMaxLength", 1200);
        return config;
    }

    private int estimateTokens(List<AgentMessage> messages) {
        int totalLength = 0;
        for (AgentMessage message : messages) {
            totalLength += message.getQuery().length();
            totalLength += message.getResponse().length();
        }
        return totalLength / 3;
    }

    private int estimateCompactedTokens(String summaryText, List<AgentMessage> recentWindow) {
        int totalLength = summaryText.length();
        for (AgentMessage message : recentWindow) {
            totalLength += message.getQuery().length();
            totalLength += message.getResponse().length();
        }
        return totalLength / 3;
    }

    private String buildLongText(String prefix, int length) {
        StringBuilder builder = new StringBuilder(prefix);
        while (builder.length() < length) {
            builder.append(" ").append(prefix).append(" 扩展内容");
        }
        return builder.substring(0, length);
    }
}

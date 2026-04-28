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
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemorySummaryGenerator;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryTokenEstimator;
import org.wwz.ai.domain.agent.reactor.service.support.SessionTranscriptBlockAssembler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SessionMemoryCompactionServiceTest {

    @Test
    public void test_compactionKeepsRecentWindowAndShrinksPayload() throws Exception {
        SessionMemoryCompactionService service = new SessionMemoryCompactionService();
        ReflectionTestUtils.setField(service, "reactorConfig", buildConfig(200, 2500));
        ReflectionTestUtils.setField(service, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(service, "summaryGenerator", new StubSummaryGenerator());
        SessionTranscriptBlockAssembler transcriptBlockAssembler = new SessionTranscriptBlockAssembler();
        ReflectionTestUtils.setField(transcriptBlockAssembler, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(service, "transcriptBlockAssembler", transcriptBlockAssembler);
        ReflectionTestUtils.setField(service, "tokenEstimator", new SessionMemoryTokenEstimator());

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
        Assert.assertTrue(result.getPostCompactionTokens() < result.getEstimatedTokens());
        Assert.assertTrue(result.getPreservedTurnCount() >= 2);
        Assert.assertEquals(Integer.valueOf(completedMessages.size()),
                Integer.valueOf(result.getCompactedTurnCount() + result.getPreservedTurnCount()));
        Assert.assertEquals(completedMessages.get(result.getCompactedTurnCount() - 1).getSortOrder(),
                result.getBoundarySortOrder());
        Assert.assertEquals(result.getCompactedTurnCount(), result.getSourceTurnCount());
        Assert.assertTrue(result.getArtifactRefsJson().contains("compact-report.html"));

        int fullPayloadTokens = estimateTokens(completedMessages);
        int compactedPayloadTokens = estimateCompactedTokens(
                result.getSummaryText(),
                completedMessages.subList(result.getCompactedTurnCount(), completedMessages.size()));
        Assert.assertTrue(compactedPayloadTokens * 100 <= fullPayloadTokens * 70);
    }

    @Test
    public void test_compactionBoundaryMovesForwardFromExistingSnapshot() throws Exception {
        SessionMemoryCompactionService service = new SessionMemoryCompactionService();
        ReflectionTestUtils.setField(service, "reactorConfig", buildConfig(120, 600));
        ReflectionTestUtils.setField(service, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(service, "summaryGenerator", new StubSummaryGenerator());
        SessionTranscriptBlockAssembler transcriptBlockAssembler = new SessionTranscriptBlockAssembler();
        ReflectionTestUtils.setField(transcriptBlockAssembler, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(service, "transcriptBlockAssembler", transcriptBlockAssembler);
        ReflectionTestUtils.setField(service, "tokenEstimator", new SessionMemoryTokenEstimator());

        AgentConversation conversation = AgentConversation.builder()
                .id(SessionMemoryTestSupport.CONVERSATION_ID)
                .sessionId(SessionMemoryTestSupport.SESSION_ID)
                .agentType(2)
                .build();
        AgentSessionMemory snapshot = SessionMemoryTestSupport.snapshot(
                "已有摘要",
                3,
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
        Assert.assertTrue(result.getPostCompactionTokens() < result.getEstimatedTokens());
        Assert.assertTrue(result.getBoundarySortOrder() > snapshot.getBoundarySortOrder());
        Assert.assertTrue(result.getPreservedTurnCount() >= 2);
        Assert.assertEquals(Integer.valueOf(completedMessages.size()),
                Integer.valueOf(result.getCompactedTurnCount() + result.getPreservedTurnCount()));
        Assert.assertEquals(completedMessages.get(result.getCompactedTurnCount() - 1).getSortOrder(),
                result.getBoundarySortOrder());
        Assert.assertEquals(Integer.valueOf(snapshot.getSourceTurnCount() + result.getCompactedTurnCount()),
                result.getSourceTurnCount());
        Assert.assertTrue(result.getSummaryText().contains("已有摘要"));
    }

    private ReactorConfig buildConfig(int thresholdTokens, int recentWindowMaxTokens) {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "sessionMemoryCompactionThresholdTokens", thresholdTokens);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowTurns", 2);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowMinMessages", 2);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowMaxTokens", recentWindowMaxTokens);
        ReflectionTestUtils.setField(config, "sessionMemorySummaryMaxLength", 1200);
        return config;
    }

    private static class StubSummaryGenerator implements SessionMemorySummaryGenerator {
        @Override
        public String generate(GenerationRequest request) {
            String existingSummary = request.getExistingSummary() == null ? "" : request.getExistingSummary();
            return """
                    # Session Title
                    压缩测试

                    # Current State
                    %s

                    # Task specification

                    # Files and Functions

                    # Workflow

                    # Errors & Corrections

                    # Codebase and System Documentation

                    # Learnings

                    # Key results

                    # Worklog
                    compacted
                    """.formatted(existingSummary);
        }
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

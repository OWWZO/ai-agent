package org.wwz.ai.test.domain.sessionmemory;

import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.entity.AgentSessionMemory;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageEventDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentSessionMemoryDao;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentSessionMemoryServiceImpl;
import org.wwz.ai.domain.agent.reactor.service.support.SessionArtifactRestoreSupport;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryTokenEstimator;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryPromptFormatter;
import org.wwz.ai.domain.agent.reactor.service.support.SessionTranscriptBlockAssembler;
import org.wwz.ai.domain.agent.reactor.service.support.SessionWorkingMemoryAssembler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史会话重开后的工作记忆恢复测试
 */
public class SessionMemoryReopenResumeTest {

    @Test
    public void test_reopenConversationPrefersSnapshotAndRecentWindow() {
        AgentSessionMemoryServiceImpl service = buildService(
                SessionMemoryFixtureFactory.buildSnapshot(),
                SessionMemoryFixtureFactory.buildRecentWindowMessages(),
                SessionMemoryFixtureFactory.buildRecentWindowEvents());

        SessionWorkingMemory workingMemory = service.rebuildWorkingMemory(buildConversation());

        Assert.assertEquals("用户要求后续输出都使用中文表格。", workingMemory.getSummaryText());
        Assert.assertEquals(Integer.valueOf(2), Integer.valueOf(workingMemory.getRecentTurns().size()));
        Assert.assertEquals(Integer.valueOf(2), workingMemory.getBoundarySortOrder());
        Assert.assertTrue(workingMemory.getHistoryDialogue().contains("历史摘要"));
        Assert.assertTrue(workingMemory.getRecentTurns().get(0).getBlocks().stream()
                .anyMatch(block -> block != null && Boolean.TRUE.equals(block.getReferenceOnly())));
        SessionMemoryTestSupport.assertFileNames(
                workingMemory.getRestoredFiles(),
                "existing-report.html",
                "uploaded-spec.pdf",
                "summary-report.html");
    }

    @Test
    public void test_reopenConversationFallsBackToRecentCompletedTurnsWithoutSnapshot() {
        AgentMessage completedMessage = SessionMemoryTestSupport.completedMessage(
                301L,
                "req-reopen-001",
                0,
                "继续沿用上一轮结论",
                "我会沿用上一轮结论继续展开。",
                SessionMemoryTestSupport.filesJson(
                        SessionMemoryTestSupport.file(
                                "fallback-report.md",
                                "历史输出文件",
                                "https://file.example.com/fallback-report")));
        AgentMessageEvent artifactEvent = AgentMessageEvent.builder()
                .messageId(301L)
                .seqNo(1)
                .eventType("artifact_reference")
                .eventSubType("generated_file")
                .displayArea("workspace")
                .referenceOnly(true)
                .artifactRefsJson("""
                        [{"displayName":"legacy-report.html","resourceKey":"legacy-report","downloadUrl":"https://file.example.com/legacy-report","previewUrl":"https://file.example.com/legacy-report","missing":false}]
                        """)
                .status("completed")
                .build();

        AgentSessionMemoryServiceImpl service = buildService(
                null,
                List.of(completedMessage),
                List.of(artifactEvent));

        SessionWorkingMemory workingMemory = service.rebuildWorkingMemory(buildConversation());

        Assert.assertNull(workingMemory.getSummaryText());
        Assert.assertEquals(Integer.valueOf(-1), workingMemory.getBoundarySortOrder());
        Assert.assertEquals(Integer.valueOf(1), Integer.valueOf(workingMemory.getRecentTurns().size()));
        Assert.assertTrue(workingMemory.getHistoryDialogue().contains("可继续复用的历史文件"));
        SessionMemoryTestSupport.assertFileNames(
                workingMemory.getRestoredFiles(),
                "fallback-report.md",
                "legacy-report.html");
    }

    @Test
    public void test_reopenConversation_restoresSemanticToolChainAndGeneratedFiles() {
        AgentMessage completedMessage = SessionMemoryTestSupport.completedMessage(
                401L,
                "req-reopen-semantic-001",
                1,
                "继续沿用上一轮事实补完报告",
                "我已经沿用上一轮事实补完最终报告。",
                null);
        completedMessage.setGeneratedFilesJson("""
                [
                  {
                    "fileName":"semantic-report.md",
                    "domainUrl":"https://file.example.com/semantic-report.md",
                    "ossUrl":"https://file.example.com/download/semantic-report.md",
                    "fileType":"markdown",
                    "resourceKey":"semantic-report.md"
                  }
                ]
                """);

        AgentSessionMemoryServiceImpl service = buildService(
                null,
                List.of(completedMessage),
                List.of(
                        SessionEventPayloadFixtureBuilder.semanticAssistantThoughtEvent(
                                401L,
                                1,
                                "tool",
                                "tool-reopen-1",
                                "deep_search",
                                JSONObject.parseObject("{\"query\":\"Spring AI MCP\"}"),
                                "先恢复上一轮 deep_search 的搜索条件",
                                "task-reopen-1",
                                1),
                        SessionEventPayloadFixtureBuilder.semanticToolUseEvent(
                                401L,
                                2,
                                "tool-reopen-1",
                                "deep_search",
                                JSONObject.parseObject("{\"query\":\"Spring AI MCP\"}"),
                                "task-reopen-1",
                                1),
                        SessionEventPayloadFixtureBuilder.semanticToolResultEvent(
                                401L,
                                3,
                                "markdown",
                                "report",
                                "tool-reopen-1",
                                "deep_search",
                                JSONObject.parseObject("{\"query\":\"Spring AI MCP\"}"),
                                "已恢复最终 Markdown 报告，请按稳定引用读取。",
                                "task-reopen-1",
                                1,
                                List.of(SessionEventPayloadFixtureBuilder.artifactRef(
                                        "semantic-report.md",
                                        "https://file.example.com/semantic-report.md")))));

        SessionWorkingMemory workingMemory = service.rebuildWorkingMemory(buildConversation());

        Assert.assertEquals(1, workingMemory.getRecentTurns().size());
        Assert.assertEquals("tool-reopen-1", workingMemory.getRecentTurns().get(0).getBlocks().stream()
                .filter(block -> block != null && block.getBlockType() == org.wwz.ai.domain.agent.reactor.model.memory.TranscriptBlockType.TOOL_RESULT)
                .findFirst()
                .orElseThrow()
                .getToolUseId());
        Assert.assertEquals(1, workingMemory.getRecentTurns().get(0).getBlocks().stream()
                .filter(block -> block != null && block.getBlockType() == org.wwz.ai.domain.agent.reactor.model.memory.TranscriptBlockType.ARTIFACT_REFERENCE)
                .count());
        SessionMemoryTestSupport.assertFileNames(
                workingMemory.getRestoredFiles(),
                "semantic-report.md");
    }

    private AgentSessionMemoryServiceImpl buildService(AgentSessionMemory snapshot,
                                                       List<AgentMessage> recentMessages,
                                                       List<AgentMessageEvent> events) {
        SessionWorkingMemoryAssembler assembler = new SessionWorkingMemoryAssembler();
        ReflectionTestUtils.setField(assembler, "reactorConfig", buildConfig());
        ReflectionTestUtils.setField(assembler, "sessionMemoryDao", new StubSessionMemoryDao(snapshot));
        ReflectionTestUtils.setField(assembler, "messageDao", new StubMessageDao(recentMessages));
        ReflectionTestUtils.setField(assembler, "messageEventDao", new StubMessageEventDao(events));
        ReflectionTestUtils.setField(assembler, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(assembler, "promptFormatter", new SessionMemoryPromptFormatter());
        SessionTranscriptBlockAssembler transcriptBlockAssembler = new SessionTranscriptBlockAssembler();
        ReflectionTestUtils.setField(transcriptBlockAssembler, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(assembler, "transcriptBlockAssembler", transcriptBlockAssembler);
        ReflectionTestUtils.setField(assembler, "tokenEstimator", new SessionMemoryTokenEstimator());

        AgentSessionMemoryServiceImpl service = new AgentSessionMemoryServiceImpl();
        ReflectionTestUtils.setField(service, "reactorConfig", buildConfig());
        ReflectionTestUtils.setField(service, "workingMemoryAssembler", assembler);
        return service;
    }

    private ReactorConfig buildConfig() {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "sessionMemoryEnabled", true);
        ReflectionTestUtils.setField(config, "sessionMemoryCompactionThresholdTokens", 12000);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowTurns", 2);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowMinMessages", 2);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowMaxTokens", 4000);
        ReflectionTestUtils.setField(config, "sessionMemorySummaryMaxLength", 4000);
        return config;
    }

    private AgentConversation buildConversation() {
        return AgentConversation.builder()
                .id(SessionMemoryTestSupport.CONVERSATION_ID)
                .sessionId(SessionMemoryTestSupport.SESSION_ID)
                .agentType(2)
                .build();
    }

    private static class StubSessionMemoryDao implements IAgentSessionMemoryDao {

        private final AgentSessionMemory snapshot;

        private StubSessionMemoryDao(AgentSessionMemory snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public int insert(AgentSessionMemory sessionMemory) {
            return 0;
        }

        @Override
        public int updateById(AgentSessionMemory sessionMemory) {
            return 0;
        }

        @Override
        public AgentSessionMemory queryBySessionId(String sessionId) {
            return snapshot;
        }

        @Override
        public List<AgentSessionMemory> queryHistoryBySessionId(String sessionId) {
            return List.of();
        }

        @Override
        public int softDeleteBySessionId(String sessionId) {
            return 0;
        }
    }

    private static class StubMessageDao implements IAgentMessageDao {

        private final List<AgentMessage> recentMessages;

        private StubMessageDao(List<AgentMessage> recentMessages) {
            this.recentMessages = recentMessages;
        }

        @Override
        public int insert(AgentMessage message) {
            return 0;
        }

        @Override
        public int updateById(AgentMessage message) {
            return 0;
        }

        @Override
        public AgentMessage queryByRequestId(String requestId) {
            return null;
        }

        @Override
        public List<AgentMessage> queryByConversationId(Long conversationId) {
            return List.of();
        }

        @Override
        public List<AgentMessage> queryRecentCompleted(Long conversationId, int limit) {
            return List.of();
        }

        @Override
        public List<AgentMessage> queryCompletedAfterSortOrder(Long conversationId, Integer afterSortOrder, int limit) {
            return recentMessages;
        }

        @Override
        public List<AgentMessage> queryCompletedByConversationId(Long conversationId) {
            return recentMessages;
        }

        @Override
        public int countStreamingByConversationId(Long conversationId) {
            return 0;
        }

        @Override
        public Integer queryMaxSortOrder(Long conversationId) {
            return 0;
        }

        @Override
        public int softDeleteByConversationId(Long conversationId) {
            return 0;
        }
    }

    private static class StubMessageEventDao implements IAgentMessageEventDao {

        private final Map<Long, List<AgentMessageEvent>> eventMap = new LinkedHashMap<>();

        private StubMessageEventDao(List<AgentMessageEvent> events) {
            for (AgentMessageEvent event : events) {
                eventMap.computeIfAbsent(event.getMessageId(), key -> new ArrayList<>()).add(event);
            }
        }

        @Override
        public int batchInsert(List<AgentMessageEvent> events) {
            return 0;
        }

        @Override
        public List<AgentMessageEvent> queryByMessageId(Long messageId) {
            return eventMap.getOrDefault(messageId, List.of());
        }

        @Override
        public List<AgentMessageEvent> queryByMessageIds(List<Long> messageIds) {
            List<AgentMessageEvent> events = new ArrayList<>();
            for (Long messageId : messageIds) {
                events.addAll(eventMap.getOrDefault(messageId, List.of()));
            }
            return events;
        }

        @Override
        public List<AgentMessageEvent> queryArtifactEventsByMessageIds(List<Long> messageIds) {
            return queryByMessageIds(messageIds);
        }

        @Override
        public List<AgentMessageEvent> queryFinalEventsByMessageIds(List<Long> messageIds) {
            return queryByMessageIds(messageIds);
        }

        @Override
        public int deleteByMessageId(Long messageId) {
            return 0;
        }
    }
}

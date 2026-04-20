package org.wwz.ai.test.domain.sessionmemory;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.entity.AgentSessionMemory;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentConversationDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageEventDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentSessionMemoryDao;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryPreparationResult;
import org.wwz.ai.domain.agent.reactor.service.IAgentSessionMemoryService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 会话压缩手工触发夹具。
 * <p>
 * 说明：
 * 1. 该类名称故意不以 Test 结尾，避免进入默认全量回归。
 * 2. 通过 -Dtest=SessionMemoryManualTriggerDebug 显式运行。
 * 3. 默认读取 classpath:manual/session-memory-manual-input.json，也支持通过
 *    -Dsession.memory.manual.fixture=D:\\path\\to\\fixture.json 指定外部 JSON。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SessionMemoryManualTriggerDebug {

    private static final DateTimeFormatter SESSION_ID_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private ReactorConfig reactorConfig;
    @Resource
    private IAgentConversationDao conversationDao;
    @Resource
    private IAgentMessageDao messageDao;
    @Resource
    private IAgentMessageEventDao messageEventDao;
    @Resource
    private IAgentSessionMemoryDao sessionMemoryDao;
    @Resource
    private IAgentSessionMemoryService sessionMemoryService;

    @Test
    public void runManualFixtureAndTriggerCompaction() throws Exception {
        ManualFixture fixture = loadFixture();
        applyManualConfig(fixture);

        String sessionId = resolveSessionId(fixture);
        ensureSessionAbsent(sessionId);

        AgentConversation conversation = createConversation(fixture, sessionId);
        int currentSortOrder = 0;
        int phaseIndex = 0;
        SessionMemoryPreparationResult latestPreparationResult = null;

        for (ManualPhase phase : fixture.getPhases()) {
            phaseIndex++;
            currentSortOrder = insertPhaseTurns(conversation, fixture, phase, phaseIndex, currentSortOrder);
            updateConversationProgress(conversation, currentSortOrder, phase);
            if (shouldTriggerPrepare(phase)) {
                latestPreparationResult = sessionMemoryService.prepareForRequest(conversation);
                logPreparationResult(sessionId, phaseIndex, phase, latestPreparationResult);
            }
        }

        List<AgentSessionMemory> snapshotHistory = sessionMemoryDao.queryHistoryBySessionId(sessionId);
        AgentSessionMemory latestSnapshot = sessionMemoryDao.queryBySessionId(sessionId);
        int expectedSnapshotCount = fixture.getExpectedSnapshotCount() == null
                ? 1
                : Math.max(1, fixture.getExpectedSnapshotCount());

        Assert.assertNotNull("未生成任何 session memory 快照，请检查 fixture 内容或阈值配置", latestSnapshot);
        Assert.assertTrue(
                "生成的快照数量少于预期，sessionId=" + sessionId + "，historySize=" + snapshotHistory.size(),
                snapshotHistory.size() >= expectedSnapshotCount);

        log.info("手工压缩验证完成 sessionId={}, latestSnapshotId={}, snapshotHistoryCount={}, latestBoundarySortOrder={}, latestSourceTurnCount={}, latestPreparationDecision={}",
                sessionId,
                latestSnapshot.getId(),
                snapshotHistory.size(),
                latestSnapshot.getBoundarySortOrder(),
                latestSnapshot.getSourceTurnCount(),
                latestPreparationResult == null ? null : latestPreparationResult.getDecisionType());
        log.info("latest summary preview:\n{}", abbreviate(latestSnapshot.getSummaryText(), 1200));
        log.info("建议查询 SQL:\nSELECT id, session_id, boundary_sort_order, source_turn_count, last_compacted_at\nFROM ai_agent_session_memory\nWHERE session_id = '{}'\nORDER BY id DESC;", sessionId);
    }

    private ManualFixture loadFixture() throws IOException {
        String externalFixturePath = System.getProperty("session.memory.manual.fixture");
        String json;
        if (StringUtils.hasText(externalFixturePath)) {
            json = Files.readString(Path.of(externalFixturePath), StandardCharsets.UTF_8);
            log.info("使用外部手工压缩夹具 fixture={}", externalFixturePath);
        } else {
            ClassPathResource resource = new ClassPathResource("manual/session-memory-manual-input.json");
            json = Files.readString(resource.getFile().toPath(), StandardCharsets.UTF_8);
            log.info("使用默认手工压缩夹具 fixture=classpath:manual/session-memory-manual-input.json");
        }

        ManualFixture fixture = JSON.parseObject(json, ManualFixture.class);
        Assert.assertNotNull("fixture 解析失败", fixture);
        Assert.assertFalse("fixture.phases 不能为空", CollectionUtils.isEmpty(fixture.getPhases()));
        return fixture;
    }

    private void applyManualConfig(ManualFixture fixture) {
        // 手工夹具直接覆盖本次测试需要的阈值，避免再改 application-dev.yml。
        ReflectionTestUtils.setField(reactorConfig, "sessionMemoryEnabled",
                fixture.getSessionMemoryEnabled() == null || fixture.getSessionMemoryEnabled());
        ReflectionTestUtils.setField(reactorConfig, "sessionMemoryCompactionThresholdTokens",
                defaultValue(fixture.getCompactionThresholdTokens(), 1));
        ReflectionTestUtils.setField(reactorConfig, "sessionMemoryHardLimitTokens",
                defaultValue(fixture.getHardLimitTokens(), 8000));
        ReflectionTestUtils.setField(reactorConfig, "sessionMemoryRecentWindowMaxTokens",
                defaultValue(fixture.getRecentWindowMaxTokens(), 1200));
        ReflectionTestUtils.setField(reactorConfig, "sessionMemoryRecentWindowMinMessages",
                defaultValue(fixture.getRecentWindowMinMessages(), 2));
        ReflectionTestUtils.setField(reactorConfig, "sessionMemorySummaryMaxLength",
                defaultValue(fixture.getSummaryMaxLength(), 4000));
        ReflectionTestUtils.setField(reactorConfig, "sessionMemoryMaxConsecutiveFailures",
                defaultValue(fixture.getMaxConsecutiveFailures(), 3));
        ReflectionTestUtils.setField(reactorConfig, "sessionMemoryCircuitOpenSeconds",
                defaultValue(fixture.getCircuitOpenSeconds(), 600));
        log.info("手工压缩配置已应用 thresholdTokens={}, hardLimitTokens={}, recentWindowMaxTokens={}, recentWindowMinMessages={}",
                fixture.getCompactionThresholdTokens(),
                fixture.getHardLimitTokens(),
                fixture.getRecentWindowMaxTokens(),
                fixture.getRecentWindowMinMessages());
    }

    private String resolveSessionId(ManualFixture fixture) {
        if (StringUtils.hasText(fixture.getSessionId())) {
            return fixture.getSessionId();
        }
        String sessionIdPrefix = StringUtils.hasText(fixture.getSessionIdPrefix())
                ? fixture.getSessionIdPrefix()
                : "manual-session-memory";
        return sessionIdPrefix + "-" + SESSION_ID_TIME_FORMATTER.format(LocalDateTime.now());
    }

    private void ensureSessionAbsent(String sessionId) {
        AgentConversation existingConversation = conversationDao.queryBySessionId(sessionId);
        if (existingConversation != null) {
            throw new IllegalStateException("sessionId 已存在，请更换 fixture.sessionId 或删除旧数据: " + sessionId);
        }
    }

    private AgentConversation createConversation(ManualFixture fixture, String sessionId) {
        AgentConversation conversation = AgentConversation.builder()
                .sessionId(sessionId)
                .deviceId(StringUtils.hasText(fixture.getDeviceId()) ? fixture.getDeviceId() : "manual-compaction-device")
                .title(StringUtils.hasText(fixture.getTitle()) ? fixture.getTitle() : "手工压缩验证会话")
                .agentType(defaultValue(fixture.getAgentType(), 1))
                .productType(StringUtils.hasText(fixture.getProductType()) ? fixture.getProductType() : "html")
                .messageCount(0)
                .pinned(0)
                .deleted(0)
                .build();
        conversationDao.insert(conversation);
        log.info("创建手工压缩验证会话 sessionId={}, conversationId={}, agentType={}, productType={}",
                sessionId,
                conversation.getId(),
                conversation.getAgentType(),
                conversation.getProductType());
        return conversation;
    }

    private int insertPhaseTurns(AgentConversation conversation,
                                 ManualFixture fixture,
                                 ManualPhase phase,
                                 int phaseIndex,
                                 int currentSortOrder) {
        if (CollectionUtils.isEmpty(phase.getTurns())) {
            return currentSortOrder;
        }

        int turnIndex = 0;
        for (ManualTurn turn : phase.getTurns()) {
            turnIndex++;
            AgentMessage message = AgentMessage.builder()
                    .conversationId(conversation.getId())
                    .requestId(resolveRequestId(conversation.getSessionId(), phaseIndex, turnIndex, turn))
                    .sortOrder(currentSortOrder)
                    .query(Objects.requireNonNullElse(turn.getQuery(), ""))
                    .filesJson(turn.getFilesJson())
                    .agentType(conversation.getAgentType())
                    .response(Objects.requireNonNullElse(turn.getResponse(), ""))
                    .status(1)
                    .forceStop(0)
                    .startedAt(LocalDateTime.now())
                    .finishedAt(LocalDateTime.now())
                    .deleted(0)
                    .build();
            messageDao.insert(message);
            insertTurnEvents(message, turn);
            log.info("插入手工验证消息 sessionId={}, messageId={}, sortOrder={}, requestId={}",
                    conversation.getSessionId(),
                    message.getId(),
                    message.getSortOrder(),
                    message.getRequestId());
            currentSortOrder++;
        }

        return currentSortOrder;
    }

    private void insertTurnEvents(AgentMessage message, ManualTurn turn) {
        if (CollectionUtils.isEmpty(turn.getEvents())) {
            return;
        }

        List<AgentMessageEvent> events = new ArrayList<>();
        int fallbackSeqNo = 0;
        for (ManualEvent event : turn.getEvents()) {
            fallbackSeqNo++;
            events.add(AgentMessageEvent.builder()
                    .messageId(message.getId())
                    .seqNo(event.getSeqNo() == null ? fallbackSeqNo : event.getSeqNo())
                    .eventType(StringUtils.hasText(event.getEventType()) ? event.getEventType() : "task")
                    .eventSubType(event.getEventSubType())
                    .displayArea(StringUtils.hasText(event.getDisplayArea()) ? event.getDisplayArea() : "timeline")
                    .taskId(event.getTaskId())
                    .taskOrder(event.getTaskOrder())
                    .title(event.getTitle())
                    .contentText(event.getContentText())
                    .payloadJson(event.getPayloadJson())
                    .status(StringUtils.hasText(event.getStatus()) ? event.getStatus() : "completed")
                    .deleted(0)
                    .build());
        }
        messageEventDao.batchInsert(events);
    }

    private void updateConversationProgress(AgentConversation conversation,
                                            int currentSortOrder,
                                            ManualPhase phase) {
        String lastPreview = resolveLastPreview(phase);
        conversationDao.updateById(AgentConversation.builder()
                .id(conversation.getId())
                .messageCount(currentSortOrder)
                .lastMessagePreview(lastPreview)
                .build());
    }

    private boolean shouldTriggerPrepare(ManualPhase phase) {
        return phase.getTriggerPrepare() == null || phase.getTriggerPrepare();
    }

    private void logPreparationResult(String sessionId,
                                      int phaseIndex,
                                      ManualPhase phase,
                                      SessionMemoryPreparationResult preparationResult) {
        AgentSessionMemory latestSnapshot = sessionMemoryDao.queryBySessionId(sessionId);
        List<AgentSessionMemory> history = sessionMemoryDao.queryHistoryBySessionId(sessionId);
        log.info("phase={}({}) 压缩结果 decision={}, estimatedTokens={}, postCompactionTokens={}, snapshotVersionId={}, snapshotHistoryCount={}, latestBoundarySortOrder={}, reason={}",
                phaseIndex,
                StringUtils.hasText(phase.getName()) ? phase.getName() : "unnamed",
                preparationResult == null ? null : preparationResult.getDecisionType(),
                preparationResult == null ? null : preparationResult.getEstimatedTokens(),
                preparationResult == null ? null : preparationResult.getPostCompactionTokens(),
                preparationResult == null ? null : preparationResult.getSnapshotVersionId(),
                history.size(),
                latestSnapshot == null ? null : latestSnapshot.getBoundarySortOrder(),
                preparationResult == null ? null : preparationResult.getReason());
    }

    private String resolveRequestId(String sessionId,
                                    int phaseIndex,
                                    int turnIndex,
                                    ManualTurn turn) {
        if (StringUtils.hasText(turn.getRequestId())) {
            return turn.getRequestId();
        }
        return sessionId + "-p" + phaseIndex + "-t" + turnIndex;
    }

    private String resolveLastPreview(ManualPhase phase) {
        if (CollectionUtils.isEmpty(phase.getTurns())) {
            return null;
        }
        ManualTurn lastTurn = phase.getTurns().get(phase.getTurns().size() - 1);
        return abbreviate(StringUtils.hasText(lastTurn.getResponse()) ? lastTurn.getResponse() : lastTurn.getQuery(), 180);
    }

    private String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private int defaultValue(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    @Data
    @NoArgsConstructor
    private static class ManualFixture {
        private String sessionId;
        private String sessionIdPrefix;
        private String deviceId;
        private String title;
        private Integer agentType;
        private String productType;
        private Boolean sessionMemoryEnabled;
        private Integer compactionThresholdTokens;
        private Integer hardLimitTokens;
        private Integer recentWindowMaxTokens;
        private Integer recentWindowMinMessages;
        private Integer summaryMaxLength;
        private Integer maxConsecutiveFailures;
        private Integer circuitOpenSeconds;
        private Integer expectedSnapshotCount;
        private List<ManualPhase> phases;
    }

    @Data
    @NoArgsConstructor
    private static class ManualPhase {
        private String name;
        private Boolean triggerPrepare;
        private List<ManualTurn> turns;
    }

    @Data
    @NoArgsConstructor
    private static class ManualTurn {
        private String requestId;
        private String query;
        private String response;
        private String filesJson;
        private List<ManualEvent> events;
    }

    @Data
    @NoArgsConstructor
    private static class ManualEvent {
        private Integer seqNo;
        private String eventType;
        private String eventSubType;
        private String displayArea;
        private String taskId;
        private Integer taskOrder;
        private String title;
        private String contentText;
        private String payloadJson;
        private String status;
    }
}

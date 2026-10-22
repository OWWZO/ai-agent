package org.wwz.ai.test.application.agent.execute;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.execute.WorkingMemoryHydration;
import org.wwz.ai.domain.agent.memory.SessionContextMemoryService;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.dto.Message;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * React / PlanSolve 共用的跨轮工作记忆 hydrate。
 * <p>
 * 两个应用层执行策略此前逐字重复这段逻辑：优先读 working_memory 投影，
 * 空则回退 ledger hydrate，再按 resume 标记追加让步观测。这组测试锁住优先级与
 * 回退顺序，防止收敛共用逻辑时改掉任一链路看到的消息序列。
 */
public class WorkingMemoryHydrationTest {

    @Test
    public void prefersWorkingMemoryProjectionOverLedgerHydrate() {
        StubWorkingMemoryService projection = new StubWorkingMemoryService(
                List.of(Message.userMessage("来自投影", null)));
        StubContextMemoryService ledger = new StubContextMemoryService(
                List.of(Message.userMessage("来自账本", null)));
        AgentExecutionCommand request = request("req-hydrate-1", "session-hydrate-1");

        List<Message> result = WorkingMemoryHydration.enrich(request, projection, ledger, null, null, null);

        Assert.assertEquals(1, result.size());
        Assert.assertEquals("来自投影", result.get(0).getContent());
        Assert.assertTrue("投影有数据时不得回退账本", ledger.calls.isEmpty());
    }

    @Test
    public void fallsBackToLedgerHydrateWhenProjectionEmpty() {
        StubWorkingMemoryService projection = new StubWorkingMemoryService(List.of());
        StubContextMemoryService ledger = new StubContextMemoryService(
                List.of(Message.userMessage("来自账本", null)));

        List<Message> result = WorkingMemoryHydration.enrich(
                request("req-hydrate-2", "session-hydrate-2"), projection, ledger, null, null, null);

        Assert.assertEquals(1, result.size());
        Assert.assertEquals("来自账本", result.get(0).getContent());
        Assert.assertEquals(1, ledger.calls.size());
    }

    @Test
    public void fallsBackToLedgerHydrateWhenProjectionNull() {
        StubWorkingMemoryService projection = new StubWorkingMemoryService(null);
        StubContextMemoryService ledger = new StubContextMemoryService(
                List.of(Message.userMessage("来自账本", null)));

        List<Message> result = WorkingMemoryHydration.enrich(
                request("req-hydrate-3", "session-hydrate-3"), projection, ledger, null, null, null);

        Assert.assertEquals(1, result.size());
        Assert.assertEquals("来自账本", result.get(0).getContent());
    }

    @Test
    public void emptyResultIsNormalizedToEmptyListNotNull() {
        List<Message> result = WorkingMemoryHydration.enrich(
                request("req-hydrate-4", "session-hydrate-4"),
                new StubWorkingMemoryService(null),
                new StubContextMemoryService(null),
                null, null, null);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void resumeQuestionAppendsAskUserObservation() {
        // resume 一个 AskUserQuestion：答案观测必须追加到工作记忆尾部
        StubUserQuestionRepository questions = new StubUserQuestionRepository(
                org.wwz.ai.domain.agent.runtime.askuser.UserQuestionRecord.builder()
                        .questionId("uq_1")
                        .toolCallId("call_1")
                        .questions(List.of(Map.of("question", "要查哪个订单？")))
                        .answers(Map.of("0", "订单 12345"))
                        .build());

        AgentExecutionCommand request = AgentExecutionCommand.builder()
                .requestId("req-hydrate-5")
                .sessionId("session-hydrate-5")
                .userId("user-1")
                .query("继续")
                .resumeQuestionId("uq_1")
                .build();

        List<Message> result = WorkingMemoryHydration.enrich(
                request,
                new StubWorkingMemoryService(List.of(Message.userMessage("要查哪个订单？", null))),
                new StubContextMemoryService(null),
                questions, null, null);

        Assert.assertEquals(2, result.size());
        Message observation = result.get(1);
        Assert.assertNotNull(observation.getToolCallId());
        Assert.assertTrue(observation.getContent().contains("12345"));
    }

    @Test
    public void resumeApprovalAppendsDecisionObservation() {
        StubPlanApprovalRepository approvals = new StubPlanApprovalRepository(
                org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRecord.builder()
                        .approvalId("pa_1")
                        .toolCallId("call_exit")
                        .planContent("## Approved plan")
                        .decision(org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalDecision.builder()
                                .approved(true)
                                .build())
                        .build());

        AgentExecutionCommand request = AgentExecutionCommand.builder()
                .requestId("req-hydrate-6")
                .sessionId("session-hydrate-6")
                .userId("user-1")
                .query("继续")
                .resumeApprovalId("pa_1")
                .build();

        List<Message> result = WorkingMemoryHydration.enrich(
                request,
                new StubWorkingMemoryService(List.of(Message.userMessage("提交计划", null))),
                new StubContextMemoryService(null),
                null, null, approvals);

        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.get(1).getContent().contains("Approved Plan"));
    }

    @Test
    public void writesHydratedMessagesBackToRequest() {
        AgentExecutionCommand request = request("req-hydrate-7", "session-hydrate-7");

        WorkingMemoryHydration.enrich(
                request,
                new StubWorkingMemoryService(List.of(Message.userMessage("上一轮", null))),
                new StubContextMemoryService(null),
                null, null, null);

        Assert.assertNotNull("hydrate 结果必须写回 request，后续节点才读得到",
                request.getWorkingMemoryMessages());
        Assert.assertEquals(1, request.getWorkingMemoryMessages().size());
        Assert.assertEquals("上一轮", request.getWorkingMemoryMessages().get(0).getContent());
    }

    // ---------- fixtures ----------

    private static AgentExecutionCommand request(String requestId, String sessionId) {
        return AgentExecutionCommand.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .userId("user-1")
                .query("帮我查一下订单")
                .build();
    }

    private static final class StubWorkingMemoryService implements SessionWorkingMemoryService {
        private final List<Message> messages;

        private StubWorkingMemoryService(List<Message> messages) {
            this.messages = messages;
        }

        @Override
        public List<Message> loadReadyMessages(String sessionId, String memoryScope, String currentRequestId) {
            return messages;
        }

        @Override
        public void persistTurn(String sessionId, String memoryScope, String requestId, Long runId,
                                String entryAgent, List<Message> turnMessages) {
        }

        @Override
        public void replaceReadyProjection(String sessionId, String memoryScope, String compactRequestId,
                                           List<Message> compactedMessages) {
        }
    }

    private static final class StubContextMemoryService implements SessionContextMemoryService {
        private final List<Message> messages;
        private final List<String> calls = new java.util.ArrayList<>();

        private StubContextMemoryService(List<Message> messages) {
            this.messages = messages;
        }

        @Override
        public List<Message> hydrateWorkingMessages(String sessionId, String currentRequestId) {
            calls.add(sessionId);
            return messages;
        }

        @Override
        public String buildHistoryDialogue(String sessionId, String currentRequestId) {
            return "";
        }
    }

    private static final class StubUserQuestionRepository
            implements org.wwz.ai.domain.agent.runtime.askuser.IUserQuestionRepository {
        private final org.wwz.ai.domain.agent.runtime.askuser.UserQuestionRecord record;

        private StubUserQuestionRepository(org.wwz.ai.domain.agent.runtime.askuser.UserQuestionRecord record) {
            this.record = record;
        }

        @Override
        public void insert(org.wwz.ai.domain.agent.runtime.askuser.UserQuestionRecord record) {
        }

        @Override
        public Optional<org.wwz.ai.domain.agent.runtime.askuser.UserQuestionRecord> findByQuestionId(
                String questionId) {
            return Optional.ofNullable(record);
        }

        @Override
        public Optional<org.wwz.ai.domain.agent.runtime.askuser.UserQuestionRecord> findByResumeRequestId(
                String resumeRequestId) {
            return Optional.empty();
        }

        @Override
        public List<org.wwz.ai.domain.agent.runtime.askuser.UserQuestionRecord> listOpenBySessionId(
                String sessionId) {
            return List.of();
        }

        @Override
        public boolean hasOpenBySessionId(String sessionId) {
            return false;
        }

        @Override
        public boolean casAnswerPending(String questionId, String userId, Map<String, String> answers,
                                        String resumeRequestId) {
            return false;
        }

        @Override
        public boolean casClaimResume(String resumeRequestId, String userId) {
            return false;
        }

        @Override
        public boolean markAnswered(String questionId) {
            return false;
        }

        @Override
        public boolean markStatus(String questionId, String status) {
            return false;
        }

        @Override
        public boolean casCancel(String questionId, String userId) {
            return false;
        }
    }

    private static final class StubPlanApprovalRepository
            implements org.wwz.ai.domain.agent.runtime.planmode.IPlanApprovalRepository {
        private final org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRecord record;

        private StubPlanApprovalRepository(org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRecord record) {
            this.record = record;
        }

        @Override
        public void insert(org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRecord record) {
        }

        @Override
        public Optional<org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRecord> findByApprovalId(
                String approvalId) {
            return Optional.ofNullable(record);
        }

        @Override
        public Optional<org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRecord> findByResumeRequestId(
                String resumeRequestId) {
            return Optional.empty();
        }

        @Override
        public List<org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRecord> listOpenBySessionId(
                String sessionId) {
            return List.of();
        }

        @Override
        public boolean hasOpenBySessionId(String sessionId) {
            return false;
        }

        @Override
        public boolean casDecidePending(String approvalId, String userId,
                                        org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalDecision decision,
                                        String resumeRequestId) {
            return false;
        }

        @Override
        public boolean casClaimResume(String resumeRequestId, String userId) {
            return false;
        }

        @Override
        public boolean markAnswered(String approvalId) {
            return false;
        }

        @Override
        public boolean markStatus(String approvalId, String status) {
            return false;
        }

        @Override
        public boolean casCancel(String approvalId, String userId) {
            return false;
        }

        @Override
        public int cancelBySourceRequestId(String sourceRequestId, String reason) {
            return 0;
        }
    }
}

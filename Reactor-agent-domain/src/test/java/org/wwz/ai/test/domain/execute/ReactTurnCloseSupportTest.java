package org.wwz.ai.test.domain.execute;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder;
import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.memory.ltm.LtmTurnSyncSupport;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.ReActAgent;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.service.execute.support.ReactTurnCloseSupport;
import org.wwz.ai.domain.agent.runtime.dto.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * React / PlanSolve 成功轮的收口顺序。
 * <p>
 * closeSuccessfulTurn 是成功轮的必经之路：发 result 事件、结束 ledger run、
 * 投影 working memory、同步 LTM、落 workspace read-state。任何一步顺序错乱
 * 都会造成前端拿不到终答或下一轮上下文缺失，但它此前零测试覆盖。
 */
public class ReactTurnCloseSupportTest {

    @Test
    public void sendsResultEventWithTaskSummaryAndArtifactPayload() {
        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = contextWith(printer);

        new ReactTurnCloseSupport().closeSuccessfulTurn(
                context, null, "任务完成：已生成报告", ExecutionLedgerConstants.ENTRY_AGENT_REACT);

        Assert.assertEquals("result", printer.messageType);
        Map<String, Object> payload = (Map<String, Object>) printer.message;
        Assert.assertEquals("任务完成：已生成报告", payload.get("taskSummary"));
        // 终答 payload 只带 taskSummary 与点名 keys，不附 fileList
        Assert.assertFalse(payload.containsKey("fileList"));
    }

    @Test
    public void finishesLedgerRunAsSuccessWithSummary() {
        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = contextWith(printer);
        context.activateLedgerRun(77L, "req-close-1");

        new ReactTurnCloseSupport().closeSuccessfulTurn(
                context, null, "已完成", ExecutionLedgerConstants.ENTRY_AGENT_REACT);

        Assert.assertTrue("收口必须结束 ledger run", context.isTurnClosed());
    }

    @Test
    public void blankFinalAnswerIsNormalizedToEmptyString() {
        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = contextWith(printer);

        new ReactTurnCloseSupport().closeSuccessfulTurn(context, null, null, ExecutionLedgerConstants.ENTRY_AGENT_REACT);

        Map<String, Object> payload = (Map<String, Object>) printer.message;
        Assert.assertNotNull(payload);
        Assert.assertEquals("", payload.get("taskSummary"));
    }

    @Test
    public void nullAgentContextFailsFast() {
        try {
            new ReactTurnCloseSupport().closeSuccessfulTurn(
                    null, null, "x", ExecutionLedgerConstants.ENTRY_AGENT_REACT);
            Assert.fail("agentContext 为 null 必须立即失败，不能静默跳过收口");
        } catch (IllegalStateException expected) {
            // 期望：收口缺上下文是编程错误
        }
    }

    @Test
    public void persistsWorkingMemoryWhenExecutorPresent() {
        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = contextWith(printer);
        context.activateLedgerRun(88L, "req-close-1");
        RecordingWorkingMemoryService memory = new RecordingWorkingMemoryService();
        ReactTurnCloseSupport support = supportWith(memory);

        StubExecutor executor = new StubExecutor();
        executor.getMemory().addMessage(Message.userMessage("问题", null));

        support.closeSuccessfulTurn(context, executor, "答复", ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE);

        Assert.assertEquals(1, memory.persistedScopes.size());
        Assert.assertEquals("req-close-1", memory.persistedScopes.get(0)[1]);
        Assert.assertEquals(ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE, memory.persistedScopes.get(0)[3]);
    }

    @Test
    public void skipsWorkingMemoryWhenExecutorAbsent() {
        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = contextWith(printer);
        RecordingWorkingMemoryService memory = new RecordingWorkingMemoryService();
        ReactTurnCloseSupport support = supportWith(memory);

        support.closeSuccessfulTurn(context, null, "答复", ExecutionLedgerConstants.ENTRY_AGENT_REACT);

        Assert.assertTrue("无 executor 时不得写空 delta", memory.persistedScopes.isEmpty());
    }

    @Test
    public void ltmSyncToleratesMissingRuntimeDependencies() {
        // LTM 同步失败不应阻断收口：result 与 ledger 终态已经发出
        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = AgentContext.builder()
                .requestId("req-close-3")
                .sessionId("session-close-3")
                .printer(printer)
                .build();
        new ReactTurnCloseSupport().closeSuccessfulTurn(
                context, new StubExecutor(), "答复", ExecutionLedgerConstants.ENTRY_AGENT_REACT);

        Assert.assertEquals("result", printer.messageType);
        Assert.assertTrue(context.isTurnClosed());
    }

    @Test
    public void finishesLedgerRunBeforePersistingWorkingMemory() {
        // 顺序很重要：finishRun 先落 run 终态，working memory 再作为下一轮 prompt 投影。
        // 反过来会让 delta 挂在尚未结束的 run 上，下一轮 hydrate 读到半轮上下文。
        RecordingWorkingMemoryService memory = new RecordingWorkingMemoryService();
        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = contextWith(printer);
        context.activateLedgerRun(99L, "req-close-1");
        memory.observe(context);
        ReactTurnCloseSupport support = new ReactTurnCloseSupport();
        org.springframework.test.util.ReflectionTestUtils.setField(
                support, "sessionWorkingMemoryService", memory);

        StubExecutor executor = new StubExecutor();
        executor.getMemory().addMessage(Message.userMessage("问题", null));
        support.closeSuccessfulTurn(context, executor, "答复", ExecutionLedgerConstants.ENTRY_AGENT_REACT);

        Assert.assertEquals(1, memory.turnClosedAtPersist.size());
        Assert.assertTrue("persistWorkingMemory 必须发生在 finishRun 之后",
                memory.turnClosedAtPersist.get(0));
    }

    // ---------- fixtures ----------

    private static ReactTurnCloseSupport supportWith(SessionWorkingMemoryService memory) {
        ReactTurnCloseSupport support = new ReactTurnCloseSupport();
        org.springframework.test.util.ReflectionTestUtils.setField(
                support, "sessionWorkingMemoryService", memory);
        return support;
    }

    private static AgentContext contextWith(Printer printer) {
        return AgentContext.builder()
                .requestId("req-close-1")
                .sessionId("session-close-1")
                .userId("user-1")
                .query("q")
                .printer(printer)
                .build();
    }

    private static final class RecordingPrinter implements Printer {
        private String messageType;
        private Object message;
        private Map<String, Object> extraResultMap;

        @Override
        public void send(String messageId, String messageType, Object message,
                         String digitalEmployee, Boolean isFinal) {
            this.messageType = messageType;
            this.message = message;
        }

        @Override
        public void send(String messageId, String messageType, Object message,
                         Map<String, Object> extraResultMap, String digitalEmployee,
                         Boolean isFinal) {
            this.messageType = messageType;
            this.message = message;
            this.extraResultMap = extraResultMap;
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(org.wwz.ai.domain.agent.runtime.enums.AgentType agentType) {
        }
    }

    /** 只提供 closeSuccessfulTurn 用到的东西：memory delta 与类型身份。 */
    private static final class StubExecutor extends ReActAgent {
        private StubExecutor() {
        }

        @Override
        public boolean think() {
            return false;
        }

        @Override
        public String act() {
            return "";
        }
    }

    private static final class RecordingWorkingMemoryService implements SessionWorkingMemoryService {
        private final List<Object[]> persistedScopes = new ArrayList<>();
        private final List<Boolean> turnClosedAtPersist = new ArrayList<>();
        private AgentContext observed;

        private void observe(AgentContext context) {
            this.observed = context;
        }

        @Override
        public List<Message> loadReadyMessages(String sessionId, String memoryScope, String currentRequestId) {
            return List.of();
        }

        @Override
        public void persistTurn(String sessionId, String memoryScope, String requestId, Long runId,
                                String entryAgent, List<Message> turnMessages) {
            persistedScopes.add(new Object[]{sessionId, requestId, runId, entryAgent});
            turnClosedAtPersist.add(observed != null && observed.isTurnClosed());
        }

        @Override
        public void replaceReadyProjection(String sessionId, String memoryScope, String compactRequestId,
                                           List<Message> compactedMessages) {
        }
    }
}

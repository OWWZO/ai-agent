package org.wwz.ai.test.domain.yieldcard;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.yieldcard.AfterCommitCardSender;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HITL 让步卡片的提交后发送契约。
 * <p>
 * 三个让步收口（AskUserQuestion / DesktopControl / PlanApproval）此前各自私藏
 * 一份逐字相同的 scheduleCardAfterCommit，现已收敛到
 * {@link AfterCommitCardSender}。这组测试锁住两条关键语义：有事务时延迟到
 * afterCommit、无事务时立即发；以及无 Printer / 空 context 时静默跳过。
 */
public class AfterCommitCardSenderTest {

    private final AfterCommitCardSender sender = new AfterCommitCardSender();

    @Test
    public void sendsImmediatelyWhenNoTransactionActive() {
        RecordingPrinter printer = new RecordingPrinter();
        AgentContext context = contextWith(printer);

        sender.send(context, "uq_1", "ask_user_question", () -> Map.of("k", "v"));

        Assert.assertEquals(1, printer.events);
        Assert.assertEquals("ask_user_question", printer.messageType);
        Assert.assertEquals("uq_1", printer.messageId);
        Assert.assertFalse("让步卡片不是终态帧", printer.isFinal);
    }

    @Test
    public void defersUntilAfterCommitInsideTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            RecordingPrinter printer = new RecordingPrinter();
            AgentContext context = contextWith(printer);

            sender.send(context, "dc_1", "desktop_control", () -> Map.of("k", "v"));
            Assert.assertEquals("事务提交前不得推卡片", 0, printer.events);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            Assert.assertEquals(1, printer.events);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    public void skipsSilentlyWhenPrinterAbsent() {
        AgentContext context = AgentContext.builder()
                .requestId("req-no-printer")
                .sessionId("session-no-printer")
                .build();

        sender.send(context, "pa_1", "plan_approval", () -> Map.of("k", "v"));

        // 后台执行没有 Printer，卡片静默跳过而不应炸掉让步流程
        Assert.assertTrue(true);
    }

    @Test
    public void skipsSilentlyWhenContextNull() {
        sender.send(null, "uq_1", "ask_user_question", () -> Map.of("k", "v"));
    }

    @Test
    public void payloadIsLazilyBuiltSoAbsentPrinterCostsNothing() {
        AtomicInteger builds = new AtomicInteger();
        AgentContext context = AgentContext.builder()
                .requestId("req-lazy")
                .sessionId("session-lazy")
                .build();

        sender.send(context, "pa_1", "plan_approval", builds::incrementAndGet);

        Assert.assertEquals("无 Printer 时不应构造载荷", 0, builds.get());
    }

    private static AgentContext contextWith(Printer printer) {
        return AgentContext.builder()
                .requestId("req-card")
                .sessionId("session-card")
                .printer(printer)
                .build();
    }

    private static final class RecordingPrinter implements Printer {
        private int events;
        private String messageType;
        private String messageId;
        private Boolean isFinal;

        @Override
        public void send(String messageId, String messageType, Object message,
                         String digitalEmployee, Boolean isFinal) {
            this.events++;
            this.messageId = messageId;
            this.messageType = messageType;
            this.isFinal = isFinal;
        }

        @Override
        public void send(String messageId, String messageType, Object message,
                         Map<String, Object> extraResultMap, String digitalEmployee,
                         Boolean isFinal) {
            this.events++;
            this.messageId = messageId;
            this.messageType = messageType;
            this.isFinal = isFinal;
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(org.wwz.ai.domain.agent.runtime.enums.AgentType agentType) {
        }
    }
}

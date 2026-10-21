package org.wwz.ai.domain.agent.runtime.yieldcard;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.printer.Printer;

import java.util.function.Supplier;

/**
 * HITL 让步卡片的提交后发送。
 * <p>
 * AskUserQuestion / DesktopControl / PlanApproval 三个让步收口都在事务内落库，
 * 卡片必须等事务提交后再推给前端——否则前端可能对着一条被回滚的记录发起续跑。
 * 无活动事务时立即发送，保证非事务路径（后台执行、测试）不丢卡片。
 */
@Component
public class AfterCommitCardSender {

    /**
     * @param messageId   卡片关联的业务 id（questionId / controlId / approvalId）
     * @param messageType 卡片事件类型
     * @param payload     客户端载荷，懒加载以免无 Printer 时白拼一次
     */
    public void send(AgentContext agentContext,
                     String messageId,
                     String messageType,
                     Supplier<Object> payload) {
        if (agentContext == null) {
            return;
        }
        Printer printer = agentContext.getPrinter();
        if (printer == null) {
            return;
        }
        Runnable send = () -> printer.send(messageId, messageType, payload.get(), false);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }
}

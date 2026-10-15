package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionUpsertRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.SessionKind;
import org.wwz.ai.infrastructure.adapter.port.SubAgentSessionPortAdapter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 子会话身份落库适配器测试：写入隐藏会话行、幂等、不进侧栏列表。
 */
public class SubAgentSessionPortAdapterTest {

    @Test
    public void shouldCreateHiddenSubAgentSession() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx =
                ExecutionLedgerFixtureFactory.newLedgerTestContext();
        SubAgentSessionPortAdapter adapter = new SubAgentSessionPortAdapter(ctx.writeRepository);

        adapter.ensureChildSession("sub_agent-1", "parent-1", "user-1", "探索前端");

        DialogueSession row = ctx.readRepository.querySessionEntity("sub_agent-1");
        Assert.assertNotNull(row);
        Assert.assertEquals(SessionKind.SUBAGENT.code(), row.getSessionKind());
        Assert.assertEquals("parent-1", row.getParentSessionId());
        Assert.assertEquals("user-1", row.getUserId());
        Assert.assertEquals("探索前端", row.getTitle());
    }

    @Test
    public void shouldBeIdempotentAndNotResetRunCount() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx =
                ExecutionLedgerFixtureFactory.newLedgerTestContext();
        SubAgentSessionPortAdapter adapter = new SubAgentSessionPortAdapter(ctx.writeRepository);

        adapter.ensureChildSession("sub_agent-2", "parent-1", "user-1", "首次");
        // 模拟子 run 启动：run_count 原子 +1
        LocalDateTime now = LocalDateTime.now();
        ctx.writeRepository.upsertSessionOnRunStart(DialogueSessionUpsertRecord.builder()
                .sessionId("sub_agent-2")
                .userId("user-1")
                .title("首次")
                .status(ExecutionLedgerConstants.STATUS_RUNNING)
                .startedAt(now)
                .lastActiveAt(now)
                .build());
        Assert.assertEquals(Integer.valueOf(1),
                ctx.readRepository.querySessionEntity("sub_agent-2").getRunCount());

        // 续跑再次确保身份：必须 no-op，不得重置计数或改写身份列
        adapter.ensureChildSession("sub_agent-2", "parent-1", "user-1", "改名尝试");

        DialogueSession row = ctx.readRepository.querySessionEntity("sub_agent-2");
        Assert.assertEquals(Integer.valueOf(1), row.getRunCount());
        Assert.assertEquals("首次", row.getTitle());
        Assert.assertEquals(SessionKind.SUBAGENT.code(), row.getSessionKind());
        Assert.assertEquals("parent-1", row.getParentSessionId());
    }

    @Test
    public void subAgentSessionShouldNotAppearInSidebarList() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx =
                ExecutionLedgerFixtureFactory.newLedgerTestContext();
        SubAgentSessionPortAdapter adapter = new SubAgentSessionPortAdapter(ctx.writeRepository);

        // 一个普通用户会话 + 一个子会话
        LocalDateTime now = LocalDateTime.now();
        ctx.writeRepository.upsertSession(DialogueSessionUpsertRecord.builder()
                .sessionId("session-user-1")
                .userId("user-1")
                .sessionKind(SessionKind.CONVERSATION.code())
                .title("用户会话")
                .status(ExecutionLedgerConstants.STATUS_RUNNING)
                .startedAt(now)
                .lastActiveAt(now)
                .build());
        adapter.ensureChildSession("sub_agent-3", "session-user-1", "user-1", "子任务");

        List<DialogueSessionView> listed = ctx.readRepository.queryRecentSessions("user-1", 10);

        Assert.assertEquals(1, listed.size());
        Assert.assertEquals("session-user-1", listed.get(0).getSessionId());
    }
}

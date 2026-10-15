package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.ExecutionLedgerRunSupport;
import org.wwz.ai.domain.agent.ledger.entity.DialogueRun;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentContextFactory;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 2 执行隔离：子 Agent 的账本记账落到子会话自己的 run，父 run 计数不含子代理调用。
 * <p>
 * recorder 仍共享同一实例（无状态账本写入服务），run 身份由 AgentRunState 隔离。
 */
public class SubAgentRunIsolationTest {

    @Test
    public void childLlmInvocationShouldLandOnChildSessionRunOnly() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx =
                ExecutionLedgerFixtureFactory.newLedgerTestContext();

        // 父：先按正常 react 入口激活一个 run
        org.wwz.ai.domain.agent.runtime.agent.AgentContext parent =
                ExecutionLedgerFixtureFactory.newAgentContext("req-parent", "session-parent", ctx.recorder);
        Long parentRunId = ExecutionLedgerFixtureFactory.activateRun(
                parent, ctx.recorder, ExecutionLedgerConstants.ENTRY_AGENT_REACT);
        Assert.assertNotNull(parentRunId);

        // 子：走工厂构造（内部即完成 sessionId 与独立 AgentRunState 隔离）
        org.wwz.ai.domain.agent.runtime.agent.AgentContext child = SubAgentContextFactory.create(
                parent, "子任务", "子任务描述", new ToolCollection(), "agent-fixed", "general-purpose");

        ExecutionLedgerRunSupport.initializeSubAgentRun(
                child.getExecutionRecorder(),
                child,
                ExecutionLedgerConstants.ENTRY_AGENT_SUBAGENT,
                "子任务 prompt");

        // 子 run 已激活且 runId 与父 run 不同
        Assert.assertTrue(child.hasActiveLedgerRun());
        Assert.assertNotEquals(parentRunId, child.getAgentRunState().getRunId());

        // 子侧记一条 LLM 调用
        child.markExecutionPosition("subagent", 1);
        ctx.recorder.createLlmInvocation(LlmInvocationStartRecord.builder()
                .runId(child.getAgentRunState().getRunId())
                .requestId(child.getRequestId())
                .invocationSeq(child.getAgentRunState().nextInvocationSeq())
                .agentName("subagent")
                .stepNo(1)
                .callKind(ExecutionLedgerConstants.CALL_KIND_ASK)
                .build());

        // 子会话下确实出现了 run 行
        List<DialogueRunView> childRuns = ctx.readRepository.queryRunsBySessionId("sub_agent-fixed");
        Assert.assertEquals(1, childRuns.size());
        Assert.assertEquals(ExecutionLedgerConstants.ENTRY_AGENT_SUBAGENT, childRuns.get(0).getEntryAgent());

        // 父 run 计数不含子代理调用
        DialogueRun parentRun = ctx.readRepository.queryRunByRequestId("req-parent");
        Assert.assertNotNull(parentRun);
        Assert.assertEquals(Integer.valueOf(0), parentRun.getLlmCallCount());
        Assert.assertEquals(Integer.valueOf(0), parentRun.getToolCallCount());
    }

    @Test
    public void childRunFinishShouldNotDisturbParentRunStatus() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx =
                ExecutionLedgerFixtureFactory.newLedgerTestContext();

        org.wwz.ai.domain.agent.runtime.agent.AgentContext parent =
                ExecutionLedgerFixtureFactory.newAgentContext("req-parent-2", "session-parent-2", ctx.recorder);
        ExecutionLedgerFixtureFactory.activateRun(
                parent, ctx.recorder, ExecutionLedgerConstants.ENTRY_AGENT_REACT);

        org.wwz.ai.domain.agent.runtime.agent.AgentContext child = SubAgentContextFactory.create(
                parent, "子任务", "子任务描述", new ToolCollection(), "agent-9", "general-purpose");
        ExecutionLedgerRunSupport.initializeSubAgentRun(
                child.getExecutionRecorder(),
                child,
                ExecutionLedgerConstants.ENTRY_AGENT_SUBAGENT,
                "子任务 prompt");

        ExecutionLedgerRunSupport.finishRun(
                child,
                ExecutionLedgerConstants.STATUS_SUCCESS,
                "子任务结论",
                null,
                null);

        // 子 run 收口为 SUCCESS，父 run 仍是 RUNNING
        DialogueRun childRun = ctx.readRepository.queryRunByRequestId(child.getRequestId());
        DialogueRun parentRun = ctx.readRepository.queryRunByRequestId("req-parent-2");
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_SUCCESS), childRun.getStatus());
        Assert.assertEquals("子任务结论", childRun.getFinalSummaryText());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_RUNNING), parentRun.getStatus());
    }

    @Test
    public void childSessionRunStartShouldBumpRunCountIsolatedFromParent() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx =
                ExecutionLedgerFixtureFactory.newLedgerTestContext();

        // 父会话一行
        LocalDateTime now = LocalDateTime.now();
        ctx.writeRepository.upsertSession(org.wwz.ai.domain.agent.ledger.model.DialogueSessionUpsertRecord.builder()
                .sessionId("session-parent-3")
                .userId("user-1")
                .title("父会话")
                .status(ExecutionLedgerConstants.STATUS_RUNNING)
                .startedAt(now)
                .lastActiveAt(now)
                .build());
        // 子会话一行（Phase 1 落库）
        ctx.writeRepository.upsertSession(org.wwz.ai.domain.agent.ledger.model.DialogueSessionUpsertRecord.builder()
                .sessionId("sub_agent-3")
                .userId("user-1")
                .sessionKind(org.wwz.ai.domain.agent.ledger.model.SessionKind.SUBAGENT.code())
                .parentSessionId("session-parent-3")
                .title("子任务")
                .status(ExecutionLedgerConstants.STATUS_RUNNING)
                .startedAt(now)
                .lastActiveAt(now)
                .build());

        org.wwz.ai.domain.agent.runtime.agent.AgentContext parent = org.wwz.ai.domain.agent.runtime.agent.AgentContext.builder()
                .requestId("req-parent-3")
                .sessionId("session-parent-3")
                .userId("user-1")
                .query("父问题")
                .toolCollection(new ToolCollection())
                .executionRecorder(ctx.recorder)
                .build();
        // 父 run 也正常启动一次，父会话 run_count 应 +1
        ExecutionLedgerFixtureFactory.activateRun(
                parent, ctx.recorder, ExecutionLedgerConstants.ENTRY_AGENT_REACT, "user-1");

        org.wwz.ai.domain.agent.runtime.agent.AgentContext child = SubAgentContextFactory.create(
                parent, "子任务", "子任务描述", new ToolCollection(), "agent-3", "general-purpose");
        ExecutionLedgerRunSupport.initializeSubAgentRun(
                child.getExecutionRecorder(), child,
                ExecutionLedgerConstants.ENTRY_AGENT_SUBAGENT, "子任务 prompt");

        // run_count 各自独立：父 +1，子 +1（子会话身份列不被父 run 改写）
        Assert.assertEquals(Integer.valueOf(1),
                ctx.readRepository.querySessionEntity("session-parent-3").getRunCount());
        Assert.assertEquals(Integer.valueOf(1),
                ctx.readRepository.querySessionEntity("sub_agent-3").getRunCount());
        Assert.assertEquals(org.wwz.ai.domain.agent.ledger.model.SessionKind.SUBAGENT.code(),
                ctx.readRepository.querySessionEntity("sub_agent-3").getSessionKind());
        Assert.assertEquals("session-parent-3",
                ctx.readRepository.querySessionEntity("sub_agent-3").getParentSessionId());
    }

    @Test
    public void registryShouldExposeSubAgentEntryAgent() {
        Assert.assertEquals("subagent", ExecutionLedgerConstants.ENTRY_AGENT_SUBAGENT);
        Assert.assertNotNull(new SubAgentRegistry().resolveOrDefault("general-purpose"));
    }
}

package org.wwz.ai.test.domain.subagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.adapter.port.SubAgentSessionPort;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentContextFactory;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentResult;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRunner;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 子会话身份落库契约：确定性 ID + 先落库后运行 + 落库失败即中止。
 * <p>
 * 刻意不真正启动 child run：端口抛错即证明「落库发生在子 Agent 运行之前」。
 */
public class SubAgentChildSessionPersistenceTest {

    @Test
    public void childSessionIdShouldBeDeterministicFromAgentId() {
        String id = SubAgentContextFactory.newChildSessionId("abc123");
        Assert.assertEquals("sub_abc123", id);
        // 确定性：同一 agentId 反复派生同一子会话 ID（续跑必须复用，不得新建）
        Assert.assertEquals(id, SubAgentContextFactory.newChildSessionId("abc123"));
        Assert.assertTrue(SubAgentContextFactory.newChildSessionId(null).startsWith("sub_"));
        Assert.assertTrue(SubAgentContextFactory.newChildSessionId("x".repeat(200)).length() <= 64);
    }

    @Test
    public void factoryShouldStampDeterministicChildSessionId() {
        AgentContext parent = AgentContext.builder()
                .requestId("req-parent")
                .sessionId("parent-session")
                .userId("user-1")
                .query("q")
                .toolCollection(new ToolCollection())
                .build();

        AgentContext child = SubAgentContextFactory.create(
                parent, "prompt", "desc", new ToolCollection(), "agent-9", "general-purpose");

        Assert.assertEquals("sub_agent-9", child.getSubAgentChildSessionId());
        // Phase 2：子 Agent 记账必须落到子会话，而不是继续共享父 sessionId
        Assert.assertEquals("sub_agent-9", child.getSessionId());
        // Phase 2：run 级状态必须是全新实例，否则子调用会计进父 run 计数
        Assert.assertNotSame(parent.getAgentRunState(), child.getAgentRunState());
        Assert.assertNull("子 run 尚未初始化", child.getAgentRunState().getRunId());
        // working memory 侧链仍是父会话键（resume 寻址），由 runner 显式传父 sessionId
        Assert.assertEquals("parent-session", parent.getSessionId());
    }

    @Test
    public void shouldAbortChildRunWhenIdentityPersistFails() {
        RecordingFailingPort port = new RecordingFailingPort();
        SubAgentRunner runner = new SubAgentRunner(new SubAgentRegistry());

        SubAgentResult result = runner.run(
                newParentContext(port), "探索前端", "任务 prompt",
                SubAgentRegistry.TYPE_GENERAL_PURPOSE, null, null, "agent-fixed");

        // 落库先于 child run：端口已被调用，且参数为确定性子会话身份
        Assert.assertEquals(1, port.calls.size());
        PersistCall call = port.calls.get(0);
        Assert.assertEquals("sub_agent-fixed", call.childSessionId);
        Assert.assertEquals("parent-session", call.parentSessionId);
        Assert.assertEquals("user-1", call.userId);
        Assert.assertEquals("探索前端", call.title);

        // 落库失败即中止，不启动无主运行
        Assert.assertEquals(SubAgentResult.STATUS_FAILED, result.getStatus());
        Assert.assertTrue(result.getErrorMsg().contains("落库失败"));
        Assert.assertEquals("sub_agent-fixed", result.getChildSessionId());
    }

    private static AgentContext newParentContext(SubAgentSessionPort port) {
        return AgentContext.builder()
                .requestId("req-parent")
                .sessionId("parent-session")
                .userId("user-1")
                .query("q")
                .printer(new NoopPrinter())
                .toolCollection(new ToolCollection())
                .runtimeDependencies(ReactorRuntimeDependencies.builder()
                        .subAgentSessionPort(port)
                        .build())
                .build();
    }

    private record PersistCall(String childSessionId, String parentSessionId, String userId, String title) {
    }

    private static final class RecordingFailingPort implements SubAgentSessionPort {
        private final List<PersistCall> calls = new ArrayList<>();

        @Override
        public void ensureChildSession(String childSessionId,
                                       String parentSessionId,
                                       String userId,
                                       String title) {
            calls.add(new PersistCall(childSessionId, parentSessionId, userId, title));
            throw new IllegalStateException("db unavailable");
        }
    }

    private static final class NoopPrinter implements Printer {
        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        }

        @Override
        public void send(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, String digitalEmployee, Boolean isFinal) {
        }

        @Override
        public void send(String messageType, Object message) {
        }

        @Override
        public void send(String messageType, Object message, String digitalEmployee) {
        }

        @Override
        public void send(String messageId, String messageType, Object message, Boolean isFinal) {
        }

        @Override
        public void sendWithResultMap(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, Boolean isFinal) {
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(AgentType agentType) {
        }
    }
}

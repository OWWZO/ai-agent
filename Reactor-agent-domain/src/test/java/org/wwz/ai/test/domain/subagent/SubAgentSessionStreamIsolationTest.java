package org.wwz.ai.test.domain.subagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.adapter.port.SubAgentSessionPort;
import org.wwz.ai.domain.agent.adapter.port.SubAgentSessionStreamPort;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRegistry;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentResult;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentRunner;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Phase 3 SSE 隔离开关的行为契约。
 * <p>
 * 只验证「子帧是否改道 + 父流摘要帧」，不真正启动 child run：
 * 端口抛错即可证明 open/close 的调用时机与参数，无需 LLM。
 */
public class SubAgentSessionStreamIsolationTest {

    @Test
    public void flagOnShouldOpenChildStreamBeforeRunAndCloseAfter() {
        RecordingStreamPort streamPort = new RecordingStreamPort();
        RecordingPrinter parentPrinter = new RecordingPrinter();
        SubAgentRunner runner = new SubAgentRunner(new SubAgentRegistry());

        SubAgentResult result = runner.run(
                newParentContext(new NoopSessionPort(), streamPort, Boolean.TRUE, parentPrinter),
                "探索前端", "任务 prompt",
                SubAgentRegistry.TYPE_GENERAL_PURPOSE, null, null, "agent-fixed");

        // 子流按确定性 childSessionId + 同一次派生的 childRequestId 打开一次
        Assert.assertEquals(1, streamPort.calls.size());
        StreamCall call = streamPort.calls.get(0);
        Assert.assertEquals("sub_agent-fixed", call.childSessionId);
        Assert.assertEquals("user-1", call.userId);
        Assert.assertNotNull("子 run requestId 必须与投影一致", call.requestId);
        Assert.assertTrue(call.requestId.length() <= 64);

        // 终态收口：子流被关闭一次
        Assert.assertEquals(1, streamPort.closes.size());
        Assert.assertEquals("sub_agent-fixed", streamPort.closes.get(0));

        // 父流只留下两条自包含摘要帧，且先 start 后 end
        Assert.assertEquals(List.of("subagent_start", "subagent_end"), parentPrinter.messageTypes);
        Assert.assertEquals("sub_agent-fixed", parentPrinter.childSessionIds.get(0));
        Assert.assertEquals("sub_agent-fixed", parentPrinter.childSessionIds.get(1));

        // 结果仍带回 childSessionId（隔离流端口不可用时回退透传，不阻断子 Agent）
        Assert.assertEquals("sub_agent-fixed", result.getChildSessionId());
    }

    @Test
    public void flagOffShouldNotEmitSubAgentSummaryFrames() {
        RecordingStreamPort streamPort = new RecordingStreamPort();
        RecordingPrinter parentPrinter = new RecordingPrinter();
        SubAgentRunner runner = new SubAgentRunner(new SubAgentRegistry());

        // 开关缺省（reactorConfig 为 null）→ 不隔离，子流端口一次都不该被调用
        runner.run(newParentContext(new NoopSessionPort(), streamPort, null, parentPrinter),
                "探索前端", "任务 prompt",
                SubAgentRegistry.TYPE_GENERAL_PURPOSE, null, null, "agent-fixed");

        Assert.assertEquals("flag 关闭时不得打开子会话流", 0, streamPort.calls.size());
        Assert.assertEquals("flag 关闭时不得关闭子会话流", 0, streamPort.closes.size());
        Assert.assertTrue("flag 关闭时父流不得出现子代理摘要帧",
                parentPrinter.messageTypes.isEmpty());
    }

    @Test
    public void streamPortFailureShouldFallBackNotAbortRun() {
        FailingStreamPort streamPort = new FailingStreamPort();
        RecordingPrinter parentPrinter = new RecordingPrinter();
        SubAgentRunner runner = new SubAgentRunner(new SubAgentRegistry());

        SubAgentResult result = runner.run(
                newParentContext(new NoopSessionPort(), streamPort, Boolean.TRUE, parentPrinter),
                "探索前端", "任务 prompt",
                SubAgentRegistry.TYPE_GENERAL_PURPOSE, null, null, "agent-fixed");

        Assert.assertEquals(1, streamPort.calls.size());
        // 端口失败必须回退透传父流，不得中止子 Agent，也不得留下半截摘要帧
        Assert.assertFalse("会话流失败不应导致子 Agent 中止",
                result.getErrorMsg() != null && result.getErrorMsg().contains("会话流"));
        Assert.assertEquals("sub_agent-fixed", result.getChildSessionId());
        Assert.assertTrue("回退时父流不得残留子代理摘要帧",
                parentPrinter.messageTypes.isEmpty());
    }

    private static AgentContext newParentContext(SubAgentSessionPort sessionPort,
                                                 SubAgentSessionStreamPort streamPort,
                                                 Boolean isolatedFlag,
                                                 Printer printer) {
        org.wwz.ai.domain.agent.reactor.config.ReactorConfig config = null;
        if (isolatedFlag != null) {
            config = new org.wwz.ai.domain.agent.reactor.config.ReactorConfig();
            try {
                java.lang.reflect.Field field = org.wwz.ai.domain.agent.reactor.config.ReactorConfig.class
                        .getDeclaredField("subagentIsolatedSession");
                field.setAccessible(true);
                field.set(config, isolatedFlag);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        ReactorRuntimeDependencies dependencies = ReactorRuntimeDependencies.builder()
                .reactorConfig(config)
                .subAgentSessionPort(sessionPort)
                .subAgentSessionStreamPort(streamPort)
                .build();

        return AgentContext.builder()
                .requestId("req-parent")
                .sessionId("parent-session")
                .userId("user-1")
                .query("q")
                .printer(printer)
                .toolCollection(new ToolCollection())
                .runtimeDependencies(dependencies)
                .build();
    }

    private record StreamCall(String childSessionId, String userId, String requestId, String queryText) {
    }

    private static final class NoopSessionPort implements SubAgentSessionPort {
        @Override
        public void ensureChildSession(String childSessionId,
                                       String parentSessionId,
                                       String userId,
                                       String title) {
            // 身份落库成功路径
        }
    }

    private static final class RecordingStreamPort implements SubAgentSessionStreamPort {
        private final List<StreamCall> calls = new ArrayList<>();
        private final List<String> closes = new ArrayList<>();

        @Override
        public Printer openChildSessionStream(String childSessionId,
                                              String userId,
                                              String requestId,
                                              String queryText) {
            calls.add(new StreamCall(childSessionId, userId, requestId, queryText));
            return new RecordingPrinter();
        }

        @Override
        public void closeChildSessionStream(String childSessionId, Printer printer) {
            closes.add(childSessionId);
        }
    }

    private static final class FailingStreamPort implements SubAgentSessionStreamPort {
        private final List<StreamCall> calls = new ArrayList<>();

        @Override
        public Printer openChildSessionStream(String childSessionId,
                                              String userId,
                                              String requestId,
                                              String queryText) {
            calls.add(new StreamCall(childSessionId, userId, requestId, queryText));
            throw new IllegalStateException("stream bus down");
        }

        @Override
        public void closeChildSessionStream(String childSessionId, Printer printer) {
        }
    }

    private static final class RecordingPrinter implements Printer {
        private final List<String> messageTypes = new ArrayList<>();
        private final List<String> childSessionIds = new ArrayList<>();

        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
            record(messageType, message);
        }

        @Override
        public void send(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, String digitalEmployee, Boolean isFinal) {
            record(messageType, message);
            if (extraResultMap != null && extraResultMap.get("childSessionId") != null) {
                childSessionIds.add(String.valueOf(extraResultMap.get("childSessionId")));
            }
        }

        private void record(String messageType, Object message) {
            messageTypes.add(messageType);
            if (message instanceof Map<?, ?> map && map.get("childSessionId") != null) {
                childSessionIds.add(String.valueOf(map.get("childSessionId")));
            }
        }

        @Override
        public void send(String messageType, Object message) {
            record(messageType, message);
        }

        @Override
        public void send(String messageType, Object message, String digitalEmployee) {
            record(messageType, message);
        }

        @Override
        public void send(String messageId, String messageType, Object message, Boolean isFinal) {
            record(messageType, message);
        }

        @Override
        public void sendWithResultMap(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, Boolean isFinal) {
            record(messageType, message);
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
            record(messageType, message);
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(AgentType agentType) {
        }
    }
}

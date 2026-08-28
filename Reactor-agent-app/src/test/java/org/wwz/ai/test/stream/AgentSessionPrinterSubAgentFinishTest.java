package org.wwz.ai.test.stream;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.stream.AgentResponseProjectionStream;
import org.wwz.ai.application.agent.stream.AgentSessionPrinter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentPrinter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 子 Agent result 不得把主会话投影流标成 finished。
 */
public class AgentSessionPrinterSubAgentFinishTest {

    @Test
    public void nestedSubAgentResultMustNotFinishMainStream() throws Exception {
        CapturingStream stream = new CapturingStream();
        AgentRequest request = new AgentRequest();
        request.setRequestId("req-subagent-finish");
        AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, 1);

        Map<String, Object> extra = new HashMap<>();
        extra.put(SubAgentPrinter.KEY_PARENT_TOOL_USE_ID, "parent-tool-1");
        extra.put(SubAgentPrinter.KEY_SUB_AGENT_ID, "sub-1");
        printer.sendWithResultMap("result", "子 Agent 报告", extra);

        Assert.assertEquals(1, stream.payloads.size());
        AgentResponse response = (AgentResponse) stream.payloads.get(0);
        Assert.assertEquals("result", response.getMessageType());
        Assert.assertFalse(Boolean.TRUE.equals(response.getFinish()));
        Assert.assertFalse(stream.completed.get());
    }

    @Test
    public void rootResultStillFinishesMainStream() throws Exception {
        CapturingStream stream = new CapturingStream();
        AgentRequest request = new AgentRequest();
        request.setRequestId("req-root-finish");
        AgentSessionPrinter printer = new AgentSessionPrinter(stream, request, 1);

        printer.send("result", "主 Agent 终答");

        Assert.assertEquals(1, stream.payloads.size());
        AgentResponse response = (AgentResponse) stream.payloads.get(0);
        Assert.assertEquals("result", response.getMessageType());
        Assert.assertTrue(Boolean.TRUE.equals(response.getFinish()));
    }

    @Test
    public void isNestedSubAgentEventDetectsTagsInMessageMap() {
        Map<String, Object> message = new HashMap<>();
        message.put(SubAgentPrinter.KEY_SUB_AGENT_ID, "sub-2");
        Assert.assertTrue(AgentSessionPrinter.isNestedSubAgentEvent(message, null));
        Assert.assertFalse(AgentSessionPrinter.isNestedSubAgentEvent("plain text", null));
    }

    @Test
    public void projectionStreamStaysOpenAfterNestedResult() throws Exception {
        CapturingStream downstream = new CapturingStream();
        AgentRequest request = new AgentRequest();
        request.setRequestId("req-projection-nested");
        request.setAgentType(5);

        AgentResponseProjectionStream projection = new AgentResponseProjectionStream(
                downstream,
                request,
                Map.of(AgentType.REACT, (req, agentResponse, agentRespList, eventResult) -> {
                    GptProcessResult result = new GptProcessResult();
                    result.setFinished(Boolean.TRUE.equals(agentResponse.getFinish()));
                    result.setStatus(Boolean.TRUE.equals(agentResponse.getFinish()) ? "success" : "running");
                    result.setReqId(req.getRequestId());
                    result.setResultMap(new HashMap<>());
                    return result;
                })
        );

        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put(SubAgentPrinter.KEY_PARENT_TOOL_USE_ID, "parent-tool-1");
        nestedMap.put("agentType", 5);
        AgentResponse nested = AgentResponse.builder()
                .requestId(request.getRequestId())
                .messageId("m1")
                .messageType("result")
                .result("子 Agent 报告")
                .finish(false)
                .resultMap(nestedMap)
                .build();
        projection.send(nested);

        Assert.assertFalse(downstream.completed.get());
        Assert.assertEquals(1, downstream.payloads.size());

        Map<String, Object> rootMap = new HashMap<>();
        rootMap.put("agentType", 5);
        AgentResponse root = AgentResponse.builder()
                .requestId(request.getRequestId())
                .messageId("m2")
                .messageType("result")
                .result("主 Agent 终答")
                .finish(true)
                .resultMap(rootMap)
                .build();
        projection.send(root);

        // 根 result 的 finished 只收口业务态，不在投影层自动关流；
        // 由 GptQuery / HITL resume 在 finishRun、markAnswered 后显式 complete。
        Assert.assertFalse(downstream.completed.get());
        Assert.assertEquals(2, downstream.payloads.size());

        AgentResponse settle = AgentResponse.builder()
                .requestId(request.getRequestId())
                .messageId("m3")
                .messageType("stream_settle")
                .finish(true)
                .resultMap(rootMap)
                .build();
        projection.send(settle);
        Assert.assertTrue(downstream.completed.get());
    }

    private static final class CapturingStream implements AgentSessionStream {
        private final List<Object> payloads = new ArrayList<>();
        private final AtomicBoolean completed = new AtomicBoolean(false);

        @Override
        public void send(Object payload) {
            payloads.add(payload);
        }

        @Override
        public void complete() {
            completed.set(true);
        }

        @Override
        public void completeWithError(Throwable throwable) {
            completed.set(true);
        }
    }
}

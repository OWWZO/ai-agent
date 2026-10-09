package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.application.agent.stream.AgentStreamProjection;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.application.agent.stream.AgentSessionStreamFrame;
import org.wwz.ai.application.agent.query.mapper.AgentExecutionCommandMapper;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionFile;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.application.agent.query.GptQueryCommand;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamEvent;
import org.wwz.ai.domain.agent.runtime.stream.PlanStreamPayload;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamResult;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.handler.AgentStreamEventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主聊天请求翻译与进程内投影回归。
 */
public class MultiAgentServiceImplTest {

    @Test
    public void shouldCarrySessionFilesIntoAgentRequestForReactMode() {
        AgentExecutionCommandMapper mapper = mapper();

        List<AgentExecutionFile> sessionFiles = List.of(AgentExecutionFile.builder()
                .fileName("source-image.png")
                .domainUrl("https://file.example.com/preview/source-image.png")
                .ossUrl("https://file.example.com/download/source-image.png")
                .mimeType("image/png")
                .resourceKey("session-1:source-image.png:hash")
                .originFileName("原图.png")
                .build());
        GptQueryCommand request = GptQueryCommand.builder()
                .traceId("trace-session-1:req-1")
                .sessionId("session-1")
                .requestId("req-1")
                .query("基于上传图片改成赛博朋克风")
                .deepThink(0)
                .user("reactor")
                .sessionFiles(sessionFiles)
                .build();

        AgentExecutionCommand agentRequest = mapper.toExecutionCommand(request, null);

        Assert.assertNotNull(agentRequest);
         Assert.assertEquals("req-1", agentRequest.getRequestId());
        Assert.assertEquals(AgentType.REACT.getValue(), agentRequest.getAgentType());
        Assert.assertEquals(sessionFiles, agentRequest.getSessionFiles());
        Assert.assertEquals("react-base-prompt", agentRequest.getBasePrompt());
    }

    @Test
    public void shouldSelectPlanSolveForDeepThinkRequest() {
        AgentExecutionCommandMapper mapper = mapper();
        AgentExecutionCommand agentRequest = mapper.toExecutionCommand(GptQueryCommand.builder()
                .requestId("req-plan-solve-1")
                .sessionId("session-plan-solve-1")
                .query("请先制定执行计划")
                .deepThink(1)
                .build(), null);

        Assert.assertEquals(AgentType.PLAN_SOLVE.getValue(), agentRequest.getAgentType());
    }

    @Test
    public void shouldCompleteDownstreamWhenProjectedResultIsFinished() throws Exception {
        RecordingAgentSessionStream stream = new RecordingAgentSessionStream();
        AtomicInteger completeCount = new AtomicInteger();
        stream.onCompleteCallback = completeCount::incrementAndGet;

        AgentStreamEventHandler handler = (request, response, agentRespList, eventResult) -> AgentStreamResult.builder()
                .complete(true)
                .status("success")
                .eventData(Map.of())
                .build();

        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-finished-1");
        request.setAgentType(AgentType.REACT.getValue());

        AgentStreamProjection projecting = new AgentStreamProjection(
                stream,
                request,
                Map.of(AgentType.REACT, handler)
        );

        projecting.send(AgentStreamEvent.builder()
                .requestId("req-finished-1")
                 .messageType("stream_settle")
                .finish(true)
                .resultMap(Map.of("agentType", 5))
                .build());

        Assert.assertTrue("终态后应关闭下游输出流", stream.completed);
        Assert.assertEquals(1, stream.payloads.size());
        Assert.assertTrue(stream.payloads.get(0) instanceof AgentSessionStreamFrame);
        Assert.assertTrue(((AgentSessionStreamFrame) stream.payloads.get(0)).isFinished());

        // complete 应幂等，避免与 dispatch finally 双重关闭出问题
        projecting.complete();
        Assert.assertEquals(1, completeCount.get());
    }

    @Test
    public void shouldPropagateAbortFromDownstream() {
        AbortableAgentSessionStream stream = new AbortableAgentSessionStream();
        AtomicBoolean abortedObserved = new AtomicBoolean(false);

        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-abort-1");
        request.setAgentType(AgentType.REACT.getValue());

        AgentStreamProjection projecting = new AgentStreamProjection(
                stream,
                request,
                Map.of()
        );
        projecting.onAbort(() -> abortedObserved.set(true));
        stream.abort();

        Assert.assertTrue("下游断开后投影流应可见 aborted", projecting.isAborted());
        Assert.assertTrue("下游断开后应触发 abort 回调（供 ActiveAgentRunRegistry 解绑观察流）", abortedObserved.get());
    }

    @Test
    public void shouldResumeSendingAfterRebindDownstream() throws Exception {
        AbortableAgentSessionStream first = new AbortableAgentSessionStream();
        RecordingAgentSessionStream second = new RecordingAgentSessionStream();
        AtomicBoolean firstAbortSeen = new AtomicBoolean(false);

        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-rebind-1");
        request.setAgentType(AgentType.REACT.getValue());

        AgentStreamEventHandler handler = (req, response, agentRespList, eventResult) -> AgentStreamResult.builder()
                .complete(false)
                .status("success")
                .frameType("result")
                .eventData(Map.of())
                .build();

        AgentStreamProjection projecting = new AgentStreamProjection(
                first,
                request,
                Map.of(AgentType.REACT, handler)
        );
        projecting.onAbort(() -> firstAbortSeen.set(true));
        first.abort();

        Assert.assertTrue(projecting.isAborted());
        Assert.assertTrue(firstAbortSeen.get());

        projecting.rebindDownstream(second);
        Assert.assertFalse("续绑后投影流应恢复可写", projecting.isAborted());

        projecting.send(AgentStreamEvent.builder()
                .requestId("req-rebind-1")
                .messageType("tool_thought")
                .finish(false)
                .resultMap(Map.of("agentType", 5))
                .build());

        Assert.assertEquals(1, second.payloads.size());
        Assert.assertTrue(second.payloads.get(0) instanceof AgentSessionStreamFrame);
    }

    @Test
    public void shouldBufferAndReplayFramesWhileDownstreamDisconnected() throws Exception {
        AbortableAgentSessionStream first = new AbortableAgentSessionStream();
        RecordingAgentSessionStream second = new RecordingAgentSessionStream();

        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-buffer-1");
        request.setAgentType(AgentType.REACT.getValue());

        AtomicInteger seq = new AtomicInteger();
        AgentStreamEventHandler handler = (req, response, agentRespList, eventResult) -> AgentStreamResult.builder()
                .complete(false)
                .status("success")
                .frameType("result")
                .requestId("frame-" + seq.incrementAndGet())
                .eventData(Map.of())
                .build();

        AgentStreamProjection projecting = new AgentStreamProjection(
                first,
                request,
                Map.of(AgentType.REACT, handler)
        );
        first.abort();

        projecting.send(AgentStreamEvent.builder()
                .requestId("req-buffer-1")
                .messageType("tool_thought")
                .finish(false)
                .resultMap(Map.of("agentType", 5))
                .build());
        projecting.send(AgentStreamEvent.builder()
                .requestId("req-buffer-1")
                .messageType("tool_thought")
                .finish(false)
                .resultMap(Map.of("agentType", 5))
                .build());

        Assert.assertTrue(second.payloads.isEmpty());

        projecting.rebindDownstream(second);

        Assert.assertEquals("断流期间投影帧应在 rebind 时补发", 2, second.payloads.size());
    }

    @Test
    public void shouldFanOutToMultipleLiveObservers() throws Exception {
        RecordingAgentSessionStream first = new RecordingAgentSessionStream();
        RecordingAgentSessionStream second = new RecordingAgentSessionStream();

        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-fanout-1");
        request.setAgentType(AgentType.REACT.getValue());

        AgentStreamEventHandler handler = (req, response, agentRespList, eventResult) -> AgentStreamResult.builder()
                .complete(false)
                .status("success")
                .frameType("result")
                .eventData(Map.of())
                .build();

        AgentStreamProjection projecting = new AgentStreamProjection(
                first,
                request,
                Map.of(AgentType.REACT, handler)
        );
        projecting.rebindDownstream(second);

        projecting.send(AgentStreamEvent.builder()
                .requestId("req-fanout-1")
                .messageType("tool_thought")
                .finish(false)
                .resultMap(Map.of("agentType", 5))
                .build());

        Assert.assertEquals(1, first.payloads.size());
        Assert.assertEquals(1, second.payloads.size());
        Assert.assertFalse(projecting.isAborted());
    }

    @Test
    public void shouldKeepProjectionAliveWhenOneObserverDisconnects() throws Exception {
        AbortableAgentSessionStream first = new AbortableAgentSessionStream();
        RecordingAgentSessionStream second = new RecordingAgentSessionStream();
        AtomicBoolean projectionAbortSeen = new AtomicBoolean(false);

        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-keep-alive-1");
        request.setAgentType(AgentType.REACT.getValue());

        AgentStreamEventHandler handler = (req, response, agentRespList, eventResult) -> AgentStreamResult.builder()
                .complete(false)
                .status("success")
                .frameType("result")
                .eventData(Map.of())
                .build();

        AgentStreamProjection projecting = new AgentStreamProjection(
                first,
                request,
                Map.of(AgentType.REACT, handler)
        );
        projecting.onAbort(() -> projectionAbortSeen.set(true));
        projecting.rebindDownstream(second);
        first.abort();

        Assert.assertFalse("仍有旁观连接时投影不能因 POST 断开而 aborted", projecting.isAborted());
        Assert.assertFalse(projectionAbortSeen.get());

        projecting.send(AgentStreamEvent.builder()
                .requestId("req-keep-alive-1")
                .messageType("tool_thought")
                .finish(false)
                .resultMap(Map.of("agentType", 5))
                .build());

        Assert.assertEquals(1, second.payloads.size());
    }

    @Test
    public void shouldPublishProjectedFramesToSessionEventBus() throws Exception {
        List<Object> published = new ArrayList<>();
        RecordingAgentSessionStream local = new RecordingAgentSessionStream();

        AgentExecutionCommand request = new AgentExecutionCommand();
        request.setRequestId("req-bus-1");
        request.setSessionId("sess-bus-1");
        request.setAgentType(AgentType.REACT.getValue());

        AgentStreamEventHandler handler = (req, response, agentRespList, eventResult) -> AgentStreamResult.builder()
                .complete(false)
                .status("success")
                .frameType("result")
                .eventData(Map.of())
                .build();

        AgentStreamProjection projecting = new AgentStreamProjection(
                local,
                request,
                Map.of(AgentType.REACT, handler),
                (sessionId, frame) -> published.add(frame)
        );

        projecting.send(AgentStreamEvent.builder()
                .requestId("req-bus-1")
                .messageType("tool_thought")
                .finish(false)
                .resultMap(Map.of("agentType", 5))
                .build());

        Assert.assertEquals(1, published.size());
        Assert.assertEquals(1, local.payloads.size());
        Assert.assertEquals(1, projecting.replayAfter(0).size());
        Assert.assertTrue(projecting.replayAfter(1).isEmpty());
    }

    private ReactorConfig buildReactorConfig() {
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "reactorBasePrompt", "react-base-prompt");
        ReflectionTestUtils.setField(reactorConfig, "sseClientReadTimeout", 300);
        ReflectionTestUtils.setField(reactorConfig, "sseClientConnectTimeout", 60);
        return reactorConfig;
    }

    private AgentExecutionCommandMapper mapper() {
        AgentExecutionCommandMapper mapper = new AgentExecutionCommandMapper();
        ReflectionTestUtils.setField(mapper, "reactorConfig", buildReactorConfig());
        return mapper;
    }

    private static class RecordingAgentSessionStream implements AgentSessionStream {
        private final List<Object> payloads = new ArrayList<>();
        private final CountDownLatch completedSignal = new CountDownLatch(1);
        private boolean completed;
        Runnable onCompleteCallback;

        @Override
        public void send(Object payload) {
            payloads.add(payload);
        }

        @Override
        public void complete() {
            completed = true;
            if (onCompleteCallback != null) {
                onCompleteCallback.run();
            }
            completedSignal.countDown();
        }

        @Override
        public void completeWithError(Throwable throwable) {
            completedSignal.countDown();
        }

        private boolean awaitCompleted() {
            try {
                return completedSignal.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private static class AbortableAgentSessionStream implements AgentSessionStream {
        private Runnable abortHandler;
        private final AtomicBoolean aborted = new AtomicBoolean(false);

        @Override
        public void send(Object payload) {
        }

        @Override
        public void complete() {
        }

        @Override
        public void completeWithError(Throwable throwable) {
        }

        @Override
        public void onAbort(Runnable abortHandler) {
            this.abortHandler = abortHandler;
            if (aborted.get() && this.abortHandler != null) {
                this.abortHandler.run();
            }
        }

        @Override
        public boolean isAborted() {
            return aborted.get();
        }

        private void abort() {
            aborted.set(true);
            if (abortHandler != null) {
                abortHandler.run();
            }
        }
    }
}

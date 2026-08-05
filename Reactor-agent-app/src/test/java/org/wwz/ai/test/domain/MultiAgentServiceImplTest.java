package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.application.agent.stream.AgentResponseProjectionStream;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.runtime.GptQueryAgentRequestFactory;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.handler.AgentResponseHandler;

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
        GptQueryAgentRequestFactory factory = new GptQueryAgentRequestFactory(buildReactorConfig());

        List<FileInformation> sessionFiles = List.of(FileInformation.builder()
                .fileName("source-image.png")
                .domainUrl("https://file.example.com/preview/source-image.png")
                .ossUrl("https://file.example.com/download/source-image.png")
                .mimeType("image/png")
                .resourceKey("session-1:source-image.png:hash")
                .originFileName("原图.png")
                .build());
        GptQueryReq request = GptQueryReq.builder()
                .traceId("trace-session-1:req-1")
                .sessionId("session-1")
                .requestId("req-1")
                .query("基于上传图片改成赛博朋克风")
                .deepThink(0)
                .outputStyle("html")
                .user("reactor")
                .sessionFiles(sessionFiles)
                .build();

        AgentRequest agentRequest = factory.build(request);

        Assert.assertNotNull(agentRequest);
        Assert.assertEquals("trace-session-1:req-1", agentRequest.getRequestId());
        Assert.assertEquals(AgentType.REACT.getValue(), agentRequest.getAgentType());
        Assert.assertEquals(sessionFiles, agentRequest.getSessionFiles());
        Assert.assertEquals("react-base-prompt", agentRequest.getBasePrompt());
    }

    @Test
    public void shouldCompleteDownstreamWhenProjectedResultIsFinished() throws Exception {
        RecordingAgentSessionStream stream = new RecordingAgentSessionStream();
        AtomicInteger completeCount = new AtomicInteger();
        stream.onCompleteCallback = completeCount::incrementAndGet;

        AgentResponseHandler handler = (request, response, agentRespList, eventResult) -> GptProcessResult.builder()
                .finished(true)
                .status("success")
                .resultMap(Map.of())
                .build();

        AgentRequest request = new AgentRequest();
        request.setRequestId("req-finished-1");
        request.setAgentType(AgentType.REACT.getValue());

        AgentResponseProjectionStream projecting = new AgentResponseProjectionStream(
                stream,
                request,
                Map.of(AgentType.REACT, handler)
        );

        projecting.send(AgentResponse.builder()
                .requestId("req-finished-1")
                .messageType("result")
                .finish(true)
                .resultMap(Map.of("agentType", 5))
                .build());

        Assert.assertTrue("终态后应关闭下游输出流", stream.completed);
        Assert.assertEquals(1, stream.payloads.size());
        Assert.assertTrue(stream.payloads.get(0) instanceof GptProcessResult);
        Assert.assertTrue(((GptProcessResult) stream.payloads.get(0)).isFinished());

        // complete 应幂等，避免与 dispatch finally 双重关闭出问题
        projecting.complete();
        Assert.assertEquals(1, completeCount.get());
    }

    @Test
    public void shouldPropagateAbortFromDownstream() {
        AbortableAgentSessionStream stream = new AbortableAgentSessionStream();
        AtomicBoolean abortedObserved = new AtomicBoolean(false);

        AgentRequest request = new AgentRequest();
        request.setRequestId("req-abort-1");
        request.setAgentType(AgentType.REACT.getValue());

        AgentResponseProjectionStream projecting = new AgentResponseProjectionStream(
                stream,
                request,
                Map.of()
        );
        projecting.onAbort(() -> abortedObserved.set(true));
        stream.abort();

        Assert.assertTrue("下游断开后投影流应可见 aborted", projecting.isAborted());
        Assert.assertTrue("下游断开后应触发 abort 回调（供 ActiveAgentRunRegistry 解绑观察流）", abortedObserved.get());
    }

    private ReactorConfig buildReactorConfig() {
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "reactorBasePrompt", "react-base-prompt");
        ReflectionTestUtils.setField(reactorConfig, "reactorSopPrompt", "plan-sop-prompt");
        ReflectionTestUtils.setField(reactorConfig, "sseClientReadTimeout", 300);
        ReflectionTestUtils.setField(reactorConfig, "sseClientConnectTimeout", 60);
        return reactorConfig;
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

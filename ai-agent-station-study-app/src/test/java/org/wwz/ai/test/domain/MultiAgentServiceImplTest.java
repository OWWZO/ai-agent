package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamListener;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamRequest;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamSession;
import org.wwz.ai.domain.agent.runtime.AgentQueryServiceImpl;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.handler.AgentResponseHandler;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * AgentQueryService 请求组装回归。
 */
public class MultiAgentServiceImplTest {

    @Test
    public void shouldCarrySessionFilesIntoAgentRequestForReactMode() {
        AgentQueryServiceImpl service = new AgentQueryServiceImpl(buildReactorConfig(), Map.of(), null);

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

        AgentRequest agentRequest = ReflectionTestUtils.invokeMethod(service, "buildAgentRequest", request);

        Assert.assertNotNull(agentRequest);
        Assert.assertEquals("trace-session-1:req-1", agentRequest.getRequestId());
        Assert.assertEquals(AgentType.REACT.getValue(), agentRequest.getAgentType());
        Assert.assertEquals(sessionFiles, agentRequest.getSessionFiles());
        Assert.assertEquals("react-base-prompt", agentRequest.getBasePrompt());
    }

    @Test
    public void shouldCompleteDownstreamWithoutCancelingRemoteStreamWhenProjectedResultIsFinished() {
        AtomicBoolean canceled = new AtomicBoolean(false);
        RecordingRemoteStreamPort remoteStreamPort = new RecordingRemoteStreamPort(canceled);
        RecordingAgentMessageStream stream = new RecordingAgentMessageStream();

        AgentResponseHandler handler = (request, response, agentRespList, eventResult) -> GptProcessResult.builder()
                .finished(true)
                .status("success")
                .resultMap(Map.of())
                .build();
        AgentQueryServiceImpl service = new AgentQueryServiceImpl(
                buildReactorConfig(),
                Map.of(AgentType.REACT, handler),
                remoteStreamPort
        );

        AgentRequest request = new AgentRequest();
        request.setRequestId("req-finished-1");
        request.setAgentType(AgentType.REACT.getValue());

        ReflectionTestUtils.invokeMethod(service, "handleMultiAgentRequest", request, stream);

        Assert.assertTrue("应等待下游流被关闭", stream.awaitCompleted());
        Assert.assertFalse("终态后不应主动取消上游远端流，避免内层 SSE 被强制打断", canceled.get());
        Assert.assertTrue("终态后应关闭下游输出流", stream.completed);
    }

    @Test
    public void shouldCancelRemoteStreamWhenDownstreamAborts() {
        AtomicBoolean canceled = new AtomicBoolean(false);
        AgentQueryServiceImpl service = new AgentQueryServiceImpl(
                buildReactorConfig(),
                Map.of(AgentType.REACT, (request, response, agentRespList, eventResult) -> GptProcessResult.builder()
                        .finished(false)
                        .status("success")
                        .resultMap(Map.of())
                        .build()),
                new SilentRemoteStreamPort(canceled)
        );
        AbortableAgentMessageStream stream = new AbortableAgentMessageStream();

        AgentRequest request = new AgentRequest();
        request.setRequestId("req-abort-1");
        request.setAgentType(AgentType.REACT.getValue());

        ReflectionTestUtils.invokeMethod(service, "handleMultiAgentRequest", request, stream);
        stream.abort();

        Assert.assertTrue("下游断开后应主动取消上游远端流", canceled.get());
    }

    private ReactorConfig buildReactorConfig() {
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "reactorBasePrompt", "react-base-prompt");
        ReflectionTestUtils.setField(reactorConfig, "reactorSopPrompt", "plan-sop-prompt");
        ReflectionTestUtils.setField(reactorConfig, "sseClientReadTimeout", 300);
        ReflectionTestUtils.setField(reactorConfig, "sseClientConnectTimeout", 60);
        return reactorConfig;
    }

    private static class RecordingRemoteStreamPort implements RemoteStreamPort {
        private final AtomicBoolean canceled;

        private RecordingRemoteStreamPort(AtomicBoolean canceled) {
            this.canceled = canceled;
        }

        @Override
        public RemoteStreamSession openStream(RemoteStreamRequest request, RemoteStreamListener listener) throws IOException {
            Thread callbackThread = new Thread(() -> {
                listener.onOpen();
                try {
                    listener.onLine("data:{\"requestId\":\"req-finished-1\",\"messageId\":\"msg-1\",\"messageType\":\"result\",\"messageTime\":\"1\",\"result\":\"done\",\"finish\":true,\"isFinal\":true,\"resultMap\":{\"agentType\":20}}");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            callbackThread.setDaemon(true);
            callbackThread.start();
            return () -> canceled.set(true);
        }
    }

    private static class RecordingAgentMessageStream implements AgentMessageStream {
        private final List<Object> payloads = new ArrayList<>();
        private final CountDownLatch completedSignal = new CountDownLatch(1);
        private boolean completed;
        private Throwable error;

        @Override
        public void send(Object payload) {
            payloads.add(payload);
        }

        @Override
        public void complete() {
            completed = true;
            completedSignal.countDown();
        }

        @Override
        public void completeWithError(Throwable throwable) {
            error = throwable;
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

    private static class SilentRemoteStreamPort implements RemoteStreamPort {
        private final AtomicBoolean canceled;

        private SilentRemoteStreamPort(AtomicBoolean canceled) {
            this.canceled = canceled;
        }

        @Override
        public RemoteStreamSession openStream(RemoteStreamRequest request, RemoteStreamListener listener) {
            return () -> canceled.set(true);
        }
    }

    private static class AbortableAgentMessageStream implements AgentMessageStream {
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

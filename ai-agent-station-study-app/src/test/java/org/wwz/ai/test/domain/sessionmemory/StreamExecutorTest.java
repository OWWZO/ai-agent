package org.wwz.ai.test.domain.sessionmemory;

import okhttp3.Request;
import okio.Buffer;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.reactor.service.support.StreamExecutor;

/**
 * StreamExecutor 技术职责单测。
 */
public class StreamExecutorTest {

    @Test
    public void test_buildHttpRequest_postsCanonicalAgentPayload() throws Exception {
        StreamExecutor executor = new StreamExecutor();
        AgentRequest request = new AgentRequest();
        request.setRequestId("req-stream-001");
        request.setSessionId("sess-stream-001");
        request.setQuery("继续输出压缩后的上下文");
        request.setAgentType(2);

        Request httpRequest = (Request) ReflectionTestUtils.invokeMethod(executor, "buildHttpRequest", request);

        Assert.assertNotNull(httpRequest);
        Assert.assertEquals("POST", httpRequest.method());
        Assert.assertEquals("http://127.0.0.1:8100/AutoAgent", httpRequest.url().toString());
        Assert.assertNotNull(httpRequest.body());

        Buffer buffer = new Buffer();
        httpRequest.body().writeTo(buffer);
        String bodyJson = buffer.readUtf8();
        Assert.assertTrue(bodyJson.contains("\"requestId\":\"req-stream-001\""));
        Assert.assertTrue(bodyJson.contains("\"sessionId\":\"sess-stream-001\""));
        Assert.assertTrue(bodyJson.contains("\"query\":\"继续输出压缩后的上下文\""));
    }

    @Test
    public void test_buildHeartbeatData_returnsCanonicalHeartbeatEnvelope() {
        StreamExecutor executor = new StreamExecutor();

        GptProcessResult heartbeat = (GptProcessResult) ReflectionTestUtils.invokeMethod(
                executor,
                "buildHeartbeatData",
                "req-stream-002");

        Assert.assertNotNull(heartbeat);
        Assert.assertFalse(heartbeat.isFinished());
        Assert.assertEquals("success", heartbeat.getStatus());
        Assert.assertEquals("heartbeat", heartbeat.getPackageType());
        Assert.assertEquals("req-stream-002", heartbeat.getReqId());
        Assert.assertEquals("", heartbeat.getResponse());
        Assert.assertEquals("", heartbeat.getResponseAll());
    }
}

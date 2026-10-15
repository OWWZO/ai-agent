package org.wwz.ai.test.trigger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.wwz.ai.trigger.http.agent.AgentSessionStreamController;
import org.wwz.ai.trigger.http.reactor.support.AgentSessionStreamHub;
import org.wwz.ai.trigger.http.reactor.support.AgentStreamExceptionHandler;
import org.wwz.ai.trigger.http.reactor.support.AgentStreamLimitException;
import org.wwz.ai.types.agent.user.UserRequestContext;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 并发流上限的 HTTP 契约。
 * <p>
 * 回归背景：上限触发时客户端拿到的是 <b>500</b> 而不是 429。原因是流端点的请求头是
 * {@code Accept: text/event-stream}，而 {@link AgentStreamExceptionHandler} 返回
 * {@code ResponseEntity<Response<Void>>}（JSON）—— 没预设 Content-Type 时 Spring 会先做
 * 内容协商，协商不出 {@code text/event-stream} 就抛 {@code HttpMediaTypeNotAcceptableException}，
 * 该异常在异常处理阶段被吞掉，原始异常继续冒泡 → dispatcherServlet 记 ERROR 并回 500。
 * <p>
 * 这里用 standalone MockMvc（不启 Spring 容器）跑真实异常解析链，锁定「必须 429」。
 */
public class AgentSessionStreamLimitContractTest {

    private static final String USER_ID = "user-limit-contract";

    private final List<AgentSessionStreamHub.StreamReservation> held = new ArrayList<>();

    private AgentSessionStreamHub hub;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        hub = new AgentSessionStreamHub();
        AgentSessionStreamController controller = new AgentSessionStreamController();
        ReflectionTestUtils.setField(controller, "agentSessionStreamHub", hub);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AgentStreamExceptionHandler())
                .build();
        UserRequestContext.bind(USER_ID);
    }

    @After
    public void tearDown() {
        UserRequestContext.clear();
        held.forEach(AgentSessionStreamHub.StreamReservation::close);
        held.clear();
    }

    private void saturate(AgentSessionStreamHub.StreamKind kind) {
        for (int i = 0; i < kind.limit(); i++) {
            held.add(hub.reserve(USER_ID, kind));
        }
    }

    @Test
    public void conversationLimitReturns429WithJsonBodyInsteadOf500() throws Exception {
        saturate(AgentSessionStreamHub.StreamKind.CONVERSATION);

        MvcResult result = mockMvc.perform(get("/api/agent/session/session-1/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        Assert.assertEquals("并发上限必须是 429，不能是 500",
                429, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        Assert.assertTrue("响应体应带上限原因，实际=" + body, body.contains("并发对话流已达上限"));
    }

    @Test
    public void subAgentLimitReturns429WithItsOwnMessage() throws Exception {
        saturate(AgentSessionStreamHub.StreamKind.SUB_AGENT);

        MvcResult result = mockMvc.perform(get("/api/agent/session/sub_agent-1/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        Assert.assertEquals(429, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        Assert.assertTrue("子会话应报自己的配额文案，实际=" + body,
                body.contains("并发子会话观察流已达上限"));
    }

    @Test
    public void saturatedConversationBucketDoesNotBlockChildSessionStream() throws Exception {
        saturate(AgentSessionStreamHub.StreamKind.CONVERSATION);

        // 对话桶打满时，子会话观察流仍然可用：这是「打开子 Agent 详情不该被对话页挤掉」的核心断言。
        Assert.assertNotNull(hub.reserve(USER_ID, AgentSessionStreamHub.StreamKind.SUB_AGENT));
    }

    @Test(expected = AgentStreamLimitException.class)
    public void subAgentBucketHasItsOwnHardCap() {
        saturate(AgentSessionStreamHub.StreamKind.SUB_AGENT);
        hub.reserve(USER_ID, AgentSessionStreamHub.StreamKind.SUB_AGENT);
    }

    @Test
    public void releasingReservationReturnsTheSlot() {
        saturate(AgentSessionStreamHub.StreamKind.CONVERSATION);
        AgentSessionStreamHub.StreamReservation extra =
                hub.reserve(USER_ID, AgentSessionStreamHub.StreamKind.SUB_AGENT);
        extra.close();
        // 释放后同桶可再次获取
        Assert.assertNotNull(hub.reserve(USER_ID, AgentSessionStreamHub.StreamKind.SUB_AGENT));
    }

    @Test
    public void unknownUserIsNotQuotaTracked() {
        UserRequestContext.clear();
        for (int i = 0; i < AgentSessionStreamHub.MAX_STREAMS_PER_OWNER + 5; i++) {
            Assert.assertNotNull(hub.reserve(null, AgentSessionStreamHub.StreamKind.CONVERSATION));
        }
    }
}

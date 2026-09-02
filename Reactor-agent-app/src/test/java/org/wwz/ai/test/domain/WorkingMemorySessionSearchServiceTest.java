package org.wwz.ai.test.domain;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.WorkingMemoryProjector;
import org.wwz.ai.domain.agent.memory.WorkingMemorySearchMessage;
import org.wwz.ai.domain.agent.memory.WorkingMemoryCompactor;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.memory.ltm.SessionSearchRequest;
import org.wwz.ai.infrastructure.dao.reactor.IWorkingMemoryMessageDao;
import org.wwz.ai.infrastructure.memory.WorkingMemorySessionSearchService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorkingMemorySessionSearchServiceTest {

    @Test
    public void projectorAssignsStableOriginKey() {
        var rows = new WorkingMemoryProjector().project(
                List.of(org.wwz.ai.domain.agent.runtime.dto.Message.userMessage("hello", null)),
                "session-1", "main", "request-7", 10L);

        Assert.assertEquals("request-7:0", rows.get(0).getOriginMessageKey());
    }

    @Test
    public void compactionKeepsOriginKeyOnRetainedMessages() {
        Message retained = Message.builder()
                .role(org.wwz.ai.domain.agent.runtime.enums.RoleType.USER)
                .content("retained")
                .originMessageKey("request-1:4")
                .build();

        List<Message> compacted = new WorkingMemoryCompactor().buildPostCompactMessages(
                "summary", List.of(retained));

        Assert.assertEquals("request-1:4", compacted.get(1).getOriginMessageKey());
        Assert.assertNull(compacted.get(0).getOriginMessageKey());
    }

    @Test
    public void discoveryReturnsLightweightHitWithoutSessionDump() {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);
        WorkingMemorySearchMessage hit = message(2L, "request-1", 1, "Remember the billing decision", "USER", 2);
        when(dao.searchFullTextByVisitor(eq("visitor-1"), eq("billing"), eq(4), anyList())).thenReturn(List.of(hit));
        when(dao.selectSessionSummary("session-1")).thenReturn(ownedSummary());

        String result = new WorkingMemorySessionSearchService(dao).search(SessionSearchRequest.builder()
                .visitorId("visitor-1")
                .query("billing")
                .limit(1)
                .scope("user")
                .build());

        JSONObject payload = JSON.parseObject(result);
        Assert.assertTrue(payload.getBooleanValue("success"));
        Assert.assertEquals("discover", payload.getString("mode"));
        Assert.assertEquals("USER,ASSISTANT", payload.getString("role_filter"));
        verify(dao).searchFullTextByVisitor(eq("visitor-1"), eq("billing"), eq(4), eq(List.of("USER", "ASSISTANT")));
        verify(dao, never()).selectHistoryBySession(any());
        JSONObject entry = payload.getJSONArray("results").getJSONObject(0);
        Assert.assertEquals(2L, entry.getLongValue("match_message_id"));
        Assert.assertEquals("request-1:1", entry.getString("origin_message_key"));
        Assert.assertEquals("session-1:request-1:1", entry.getString("stable_key"));
        Assert.assertEquals("billing", entry.getJSONArray("matched_terms").getString(0));
        Assert.assertTrue(entry.getString("snippet").toLowerCase().contains("billing"));
        Assert.assertFalse(entry.containsKey("messages"));
        Assert.assertFalse(entry.containsKey("bookend_start"));
        Assert.assertFalse(entry.containsKey("bookend_end"));
    }

    @Test
    public void discoveryAcceptsExplicitToolRoleFilter() {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);
        WorkingMemorySearchMessage toolHit = message(9L, "request-9", 0, "GenUI HtmlFrame is a separate path", "TOOL", 1);
        when(dao.searchFullTextByVisitor(eq("visitor-1"), eq("GenUI"), eq(4), eq(List.of("TOOL"))))
                .thenReturn(List.of(toolHit));

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .query("GenUI")
                        .limit(1)
                        .roleFilter("tool")
                        .build()));

        Assert.assertEquals("discover", payload.getString("mode"));
        Assert.assertEquals("TOOL", payload.getString("role_filter"));
        Assert.assertEquals(1, payload.getIntValue("count"));
    }

    @Test
    public void discoverySkipsLiveCurrentSessionReadyHits() {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);
        WorkingMemorySearchMessage currentReady = message("session-current", 1L, "r1", 0, "GenUI in current", "USER", 1);
        WorkingMemorySearchMessage other = message("session-other", 2L, "r2", 0, "GenUI elsewhere", "USER", 1);
        when(dao.searchFullTextByVisitor(eq("visitor-1"), eq("GenUI"), eq(8), anyList()))
                .thenReturn(List.of(currentReady, other));

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .currentSessionId("session-current")
                        .query("GenUI")
                        .limit(2)
                        .scope("user")
                        .build()));

        Assert.assertEquals(1, payload.getIntValue("count"));
        Assert.assertEquals("session-other",
                payload.getJSONArray("results").getJSONObject(0).getString("session_id"));
        verify(dao, never()).selectHistoryBySession(any());
    }

    @Test
    public void discoveryKeepsInvalidHitsFromCurrentSession() {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);
        WorkingMemorySearchMessage currentInvalid = message("session-current", 3L, "r3", 0, "old GenUI before compact", "USER", 2);
        when(dao.searchFullTextByVisitor(eq("visitor-1"), eq("GenUI"), eq(4), anyList()))
                .thenReturn(List.of(currentInvalid));

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .currentSessionId("session-current")
                        .query("GenUI")
                        .limit(1)
                        .scope("user")
                        .build()));

        Assert.assertEquals(1, payload.getIntValue("count"));
        Assert.assertEquals("session-current",
                payload.getJSONArray("results").getJSONObject(0).getString("session_id"));
    }

    @Test
    public void discoveryScopeSessionStillSearchesCurrent() {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);
        WorkingMemorySearchMessage currentReady = message("session-current", 4L, "r4", 0, "GenUI local", "USER", 1);
        when(dao.searchFullTextBySession(eq("session-current"), eq("GenUI"), eq(4), anyList()))
                .thenReturn(List.of(currentReady));
        when(dao.selectSessionSummary("session-current")).thenReturn(Map.of(
                "visitorId", "visitor-1", "title", "Current"));

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .currentSessionId("session-current")
                        .query("GenUI")
                        .limit(1)
                        .scope("session")
                        .build()));

        Assert.assertEquals(1, payload.getIntValue("count"));
        Assert.assertEquals("session-current",
                payload.getJSONArray("results").getJSONObject(0).getString("session_id"));
    }

    @Test
    public void discoveryDropsFullTextHitsThatDoNotContainQuery() {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);
        WorkingMemorySearchMessage junk = message(1L, "r", 0, "unrelated popular session about spring", "USER", 1);
        when(dao.searchFullTextByVisitor(eq("visitor-1"), eq("zqv9_no_such_session_token_71x"), eq(40), anyList()))
                .thenReturn(List.of(junk));

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .query("zqv9_no_such_session_token_71x")
                        .limit(10)
                        .build()));

        Assert.assertEquals("discover", payload.getString("mode"));
        Assert.assertEquals(0, payload.getIntValue("count"));
        Assert.assertEquals(0, payload.getJSONArray("results").size());
        verify(dao, never()).selectHistoryBySession(any());
    }

    @Test
    public void queryPlusSessionIdSearchesInsideSessionInsteadOfRead() {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);
        WorkingMemorySearchMessage hit = message(2L, "r", 0, "Talk about Spring AI here", "USER", 1);
        when(dao.selectSessionSummary("session-1")).thenReturn(ownedSummary());
        when(dao.searchFullTextBySession(eq("session-1"), eq("Spring AI"), eq(4), anyList()))
                .thenReturn(List.of(hit));

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .sessionId("session-1")
                        .query("Spring AI")
                        .limit(1)
                        .build()));

        Assert.assertEquals("discover", payload.getString("mode"));
        Assert.assertEquals(1, payload.getIntValue("count"));
        Assert.assertEquals("Spring", payload.getJSONArray("results").getJSONObject(0)
                .getJSONArray("matched_terms").getString(0));
        verify(dao, never()).selectHistoryBySession(any());
    }

    @Test
    public void queryPlusSessionIdPlusAnchorIsRejected() {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .sessionId("session-1")
                        .query("Spring AI")
                        .aroundMessageId(3L)
                        .build()));

        Assert.assertFalse(payload.getBooleanValue("success"));
        Assert.assertTrue(payload.getString("error").contains("cannot be combined"));
        verify(dao, never()).selectHistoryBySession(any());
    }

    @Test
    public void aroundMessageIdWithoutSessionIdIsRejected() {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .aroundMessageId(3L)
                        .build()));

        Assert.assertFalse(payload.getBooleanValue("success"));
        Assert.assertTrue(payload.getString("error").contains("requires session_id"));
    }

    @Test
    public void explicitZeroLimitIsRejected() {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .query("billing")
                        .limit(0)
                        .build()));

        Assert.assertFalse(payload.getBooleanValue("success"));
        Assert.assertTrue(payload.getString("error").contains("limit"));
        verify(dao, never()).searchFullTextByVisitor(any(), any(), anyInt(), anyList());
    }

    @Test
    public void explicitZeroWindowIsRejectedOnScroll() {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);
        when(dao.selectSessionSummary("session-1")).thenReturn(ownedSummary());

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .sessionId("session-1")
                        .aroundMessageId(1L)
                        .window(0)
                        .build()));

        Assert.assertFalse(payload.getBooleanValue("success"));
        Assert.assertTrue(payload.getString("error").contains("window"));
        verify(dao, never()).selectHistoryBySession(any());
    }

    @Test
    public void scrollReturnsAnchorWindowAndBoundaryCounts() {
        IWorkingMemoryMessageDao dao = mockOwnedHistory(List.of(
                message(1L, "r", 0, "one", "USER", 1),
                message(2L, "r", 1, "two", "ASSISTANT", 1),
                message(3L, "r", 2, "three", "USER", 1),
                message(4L, "r", 3, "four", "ASSISTANT", 1),
                message(5L, "r", 4, "five", "USER", 1)));

        String result = new WorkingMemorySessionSearchService(dao).search(SessionSearchRequest.builder()
                .visitorId("visitor-1")
                .sessionId("session-1")
                .aroundMessageId(3L)
                .window(1)
                .build());

        JSONObject payload = JSON.parseObject(result);
        Assert.assertEquals("scroll", payload.getString("mode"));
        JSONArray messages = payload.getJSONArray("messages");
        Assert.assertEquals(3, messages.size());
        Assert.assertEquals(2L, messages.getJSONObject(0).getLongValue("id"));
        Assert.assertTrue(messages.getJSONObject(1).getBooleanValue("anchor"));
        Assert.assertFalse(messages.getJSONObject(0).containsKey("request_id"));
        Assert.assertEquals(1, payload.getIntValue("messages_before"));
        Assert.assertEquals(1, payload.getIntValue("messages_after"));
    }

    @Test
    public void scrollAtStartReportsShortMessagesBefore() {
        IWorkingMemoryMessageDao dao = mockOwnedHistory(List.of(
                message(1L, "r", 0, "one", "USER", 1),
                message(2L, "r", 1, "two", "ASSISTANT", 1),
                message(3L, "r", 2, "three", "USER", 1),
                message(4L, "r", 3, "four", "ASSISTANT", 1),
                message(5L, "r", 4, "five", "USER", 1)));

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .sessionId("session-1")
                        .aroundMessageId(1L)
                        .window(2)
                        .build()));

        Assert.assertEquals("scroll", payload.getString("mode"));
        Assert.assertEquals(0, payload.getIntValue("messages_before"));
        Assert.assertEquals(2, payload.getIntValue("messages_after"));
        Assert.assertEquals(3, payload.getJSONArray("messages").size());
    }

    @Test
    public void scrollHidesToolMessagesUnlessRoleFilterIncludesThem() {
        IWorkingMemoryMessageDao dao = mockOwnedHistory(List.of(
                message(1L, "r", 0, "user ask", "USER", 1),
                message(2L, "r", 1, "tool dump /tmp/secret.json", "TOOL", 1),
                message(3L, "r", 2, "assistant answer", "ASSISTANT", 1)));

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .sessionId("session-1")
                        .aroundMessageId(3L)
                        .window(5)
                        .build()));

        JSONArray messages = payload.getJSONArray("messages");
        Assert.assertEquals(2, messages.size());
        for (int i = 0; i < messages.size(); i++) {
            Assert.assertNotEquals("TOOL", messages.getJSONObject(i).getString("role"));
        }
    }

    @Test
    public void scrollRejectsToolAnchorWhenRoleFilterHidesTool() {
        IWorkingMemoryMessageDao dao = mockOwnedHistory(List.of(
                message(1L, "r", 0, "user ask", "USER", 1),
                message(2L, "r", 1, "tool dump", "TOOL", 1),
                message(3L, "r", 2, "assistant answer", "ASSISTANT", 1)));

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .sessionId("session-1")
                        .aroundMessageId(2L)
                        .window(2)
                        .build()));

        Assert.assertFalse(payload.getBooleanValue("success"));
        Assert.assertTrue(payload.getString("error").contains("hidden by role_filter"));
    }

    @Test
    public void readIgnoresWindowAndHintsScrollWhenTruncated() {
        List<WorkingMemorySearchMessage> history = new java.util.ArrayList<>();
        for (int i = 1; i <= 35; i++) {
            history.add(message((long) i, "r", i - 1, "msg-" + i, i % 2 == 0 ? "ASSISTANT" : "USER", 1));
        }
        IWorkingMemoryMessageDao dao = mockOwnedHistory(history);

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .sessionId("session-1")
                        .window(2)
                        .build()));

        Assert.assertEquals("read", payload.getString("mode"));
        Assert.assertTrue(payload.getBooleanValue("truncated"));
        Assert.assertEquals(35, payload.getIntValue("message_count"));
        Assert.assertEquals(30, payload.getJSONArray("messages").size());
        Assert.assertTrue(payload.getString("message").contains("around_message_id"));
    }

    @Test
    public void readHidesToolAndCompactionSummary() {
        IWorkingMemoryMessageDao dao = mockOwnedHistory(List.of(
                message(1L, "r", 0, "[CONTEXT SUMMARY]: old machine summary", "USER", 1),
                message(2L, "r", 1, "user ask", "USER", 1),
                message(3L, "r", 2, "tool dump /tmp/secret.json", "TOOL", 1),
                message(4L, "r", 3, "assistant answer", "ASSISTANT", 1)));

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .sessionId("session-1")
                        .roleFilter("USER")
                        .build()));

        JSONArray messages = payload.getJSONArray("messages");
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals("USER", messages.getJSONObject(0).getString("role"));
        Assert.assertEquals("user ask", messages.getJSONObject(0).getString("content"));
        Assert.assertFalse(messages.getJSONObject(0).containsKey("request_id"));
    }

    @Test
    public void readRejectsForeignSession() {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);
        when(dao.selectSessionSummary("session-1")).thenReturn(Map.of("visitorId", "someone-else"));

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder()
                        .visitorId("visitor-1")
                        .sessionId("session-1")
                        .build()));

        Assert.assertFalse(payload.getBooleanValue("success"));
        Assert.assertTrue(payload.getString("error").contains("not found"));
        verify(dao, never()).selectHistoryBySession(any());
    }

    @Test
    public void browseReturnsRecentSessionsForVisitor() {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);
        when(dao.selectRecentSessions("visitor-1", 3)).thenReturn(List.of(
                Map.of("sessionId", "session-2", "title", "Recent", "latestQueryText", "latest question")));

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder().visitorId("visitor-1").limit(2).build()));

        Assert.assertEquals("browse", payload.getString("mode"));
        Assert.assertEquals("session-2", payload.getJSONArray("results").getJSONObject(0).getString("session_id"));
        Assert.assertEquals("latest question", payload.getJSONArray("results").getJSONObject(0).getString("preview"));
    }

    @Test
    public void browseWithoutVisitorReturnsEmpty() {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);

        JSONObject payload = JSON.parseObject(new WorkingMemorySessionSearchService(dao).search(
                SessionSearchRequest.builder().limit(2).build()));

        Assert.assertEquals("browse", payload.getString("mode"));
        Assert.assertEquals(0, payload.getIntValue("count"));
        verify(dao, never()).selectRecentSessions(any(), anyInt());
    }

    private static IWorkingMemoryMessageDao mockOwnedHistory(List<WorkingMemorySearchMessage> history) {
        IWorkingMemoryMessageDao dao = mock(IWorkingMemoryMessageDao.class);
        when(dao.selectSessionSummary("session-1")).thenReturn(ownedSummary());
        when(dao.selectHistoryBySession("session-1")).thenReturn(history);
        return dao;
    }

    private static Map<String, Object> ownedSummary() {
        return Map.of("visitorId", "visitor-1", "title", "Billing", "lastActiveAt", "2026-08-31T00:00");
    }

    private static WorkingMemorySearchMessage message(Long id,
                                                       String requestId,
                                                       int seq,
                                                       String content,
                                                       String role,
                                                       int status) {
        return message("session-1", id, requestId, seq, content, role, status);
    }

    private static WorkingMemorySearchMessage message(String sessionId,
                                                       Long id,
                                                       String requestId,
                                                       int seq,
                                                       String content,
                                                       String role,
                                                       int status) {
        return WorkingMemorySearchMessage.builder()
                .id(id)
                .sessionId(sessionId)
                .memoryScope("main")
                .requestId(requestId)
                .originMessageKey(requestId + ":" + seq)
                .turnSeq(1)
                .seqNo(seq)
                .content(content)
                .role(role)
                .turnStatus(status)
                .build();
    }
}

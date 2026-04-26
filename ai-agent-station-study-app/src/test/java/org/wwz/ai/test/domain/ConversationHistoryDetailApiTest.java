package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.agent.model.valobj.ConversationRoleVO;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.model.history.ConversationEventDetail;
import org.wwz.ai.domain.agent.reactor.model.history.ConversationTurnDetail;
import org.wwz.ai.domain.agent.reactor.service.IAgentConversationService;
import org.wwz.ai.domain.agent.service.IFixRoleService;
import org.wwz.ai.trigger.http.agent.AgentConversationController;
import org.wwz.ai.trigger.http.agent.vo.ArtifactReferenceRespVO;
import org.wwz.ai.trigger.http.agent.vo.ConversationDetailRespVO;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ConversationHistoryDetailApiTest {

    @Test
    public void test_detailReturnsCanonicalProjectedPayloadAndGeneratedFiles() {
        AgentConversationController controller = new AgentConversationController();
        ReflectionTestUtils.setField(controller, "conversationService", new StubConversationService());
        ReflectionTestUtils.setField(controller, "fixRoleService", new StubFixRoleService());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Device-Id", "device-001");

        Response<ConversationDetailRespVO> response = controller.detail(request, "session-001");

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertNotNull(response.getData());
        Assert.assertEquals(1, response.getData().getTurns().size());
        Assert.assertEquals(1, response.getData().getTurns().get(0).getEvents().size());
        Assert.assertNotNull(response.getData().getTurns().get(0).getFiles());
        Assert.assertNotNull(response.getData().getTurns().get(0).getGeneratedFiles());
        Assert.assertEquals("markdown", response.getData().getTurns().get(0).getEvents().get(0).getEventType());
        Assert.assertEquals("report", response.getData().getTurns().get(0).getEvents().get(0).getEventSubType());
        Assert.assertEquals(Integer.valueOf(1), response.getData().getTurns().get(0).getEvents().get(0).getIsFinal());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> generatedFiles = (List<Map<String, Object>>) response.getData().getTurns().get(0).getGeneratedFiles();
        Assert.assertEquals("report-html", generatedFiles.get(0).get("resourceKey"));

        Map<String, Object> payload = response.getData().getTurns().get(0).getEvents().get(0).getPayload();
        Assert.assertEquals("task", payload.get("messageType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) payload.get("resultMap");
        Assert.assertEquals("markdown", resultMap.get("messageType"));
        @SuppressWarnings("unchecked")
        List<ArtifactReferenceRespVO> artifactRefs = (List<ArtifactReferenceRespVO>) payload.get("artifactRefs");
        Assert.assertEquals("report-html", artifactRefs.get(0).getResourceKey());
    }

    @Test
    public void test_detailRejectsMissingConversation() {
        AgentConversationController controller = new AgentConversationController();
        ReflectionTestUtils.setField(controller, "conversationService", new EmptyConversationService());
        ReflectionTestUtils.setField(controller, "fixRoleService", new StubFixRoleService());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Device-Id", "device-002");

        Response<ConversationDetailRespVO> response = controller.detail(request, "missing");

        Assert.assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
    }

    private static class StubConversationService extends EmptyConversationService {
        @Override
        public AgentConversation getAccessibleConversation(String sessionId, String deviceId, Long userId) {
            return AgentConversation.builder()
                    .id(1L)
                    .sessionId(sessionId)
                    .deviceId(deviceId)
                    .title("测试会话")
                    .agentType(1)
                    .productType("html")
                    .messageCount(1)
                    .pinned(0)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
        }

        @Override
        public List<ConversationTurnDetail> getConversationTurns(String sessionId, String deviceId, Long userId) {
            return List.of(
                    ConversationTurnDetail.builder()
                            .requestId("req-001")
                            .sortOrder(0)
                            .query("分析销量")
                            .files(List.of(java.util.Map.of(
                                    "name", "销量原始数据.xlsx",
                                    "url", "https://file.example.com/input.xlsx",
                                    "type", "xlsx"
                            )))
                            .generatedFiles(List.of(java.util.Map.of(
                                    "fileName", "销量分析报告.md",
                                    "previewUrl", "https://file.example.com/report.html",
                                    "downloadUrl", "https://file.example.com/download/report.html",
                                    "fileType", "markdown",
                                    "resourceKey", "report-html"
                            )))
                            .agentType(1)
                            .status(1)
                            .forceStop(0)
                            .response("已完成分析")
                            .events(List.of(
                                    ConversationEventDetail.builder()
                                            .seqNo(1)
                                            .eventType("markdown")
                                            .eventSubType("report")
                                            .displayArea("workspace")
                                            .title("销量分析报告")
                                            .contentText("已生成最终 Markdown 报告，请通过稳定引用打开。")
                                            .status("completed")
                                            .isFinal(1)
                                            .payload(java.util.Map.of(
                                                    "messageType", "task",
                                                    "messageId", "semantic-tool-result-1",
                                                    "taskId", "task-1",
                                                    "taskOrder", 1,
                                                    "resultMap", java.util.Map.of(
                                                            "messageType", "markdown",
                                                            "answer", "已生成最终 Markdown 报告，请通过稳定引用打开。",
                                                            "isFinal", true
                                                    ),
                                                    "artifactRefs", List.of(java.util.Map.of(
                                                            "displayName", "销量分析报告.md",
                                                            "resourceKey", "report-html",
                                                            "previewUrl", "https://file.example.com/report.html",
                                                            "downloadUrl", "https://file.example.com/download/report.html",
                                                            "missing", false
                                                    ))))
                                            .build()
                            ))
                            .build()
            );
        }
    }

    private static class EmptyConversationService implements IAgentConversationService {
        @Override
        public AgentConversation createConversation(String sessionId, String deviceId, String title, Integer agentType, String productType, String aiAgentId, String aiAgentNameSnapshot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentConversation getBySessionId(String sessionId) {
            return null;
        }

        @Override
        public AgentConversation getAccessibleConversation(String sessionId, String deviceId, Long userId) {
            return null;
        }

        @Override
        public void renameConversation(String sessionId, String deviceId, String newTitle) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteConversation(String sessionId, String deviceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AgentConversation> listConversations(String deviceId, Long userId, int pageNo, int pageSize) {
            return List.of();
        }

        @Override
        public int countConversations(String deviceId, Long userId) {
            return 0;
        }

        @Override
        public List<ConversationTurnDetail> getConversationTurns(String sessionId, String deviceId, Long userId) {
            return List.of();
        }

        @Override
        public void togglePin(String sessionId, String deviceId, boolean pinned) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int migrateToUser(String deviceId, Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentConversation bindChatRole(AgentConversation conversation, String aiAgentId, String aiAgentNameSnapshot) {
            return conversation;
        }

        @Override
        public ConversationRoleVO buildConversationRole(AgentConversation conversation) {
            return null;
        }
    }

    private static class StubFixRoleService implements IFixRoleService {
        @Override
        public List<org.wwz.ai.domain.agent.model.valobj.FixRoleVO> queryAvailableRoles() {
            return List.of();
        }

        @Override
        public org.wwz.ai.domain.agent.model.valobj.FixRoleVO queryDefaultRole() {
            return null;
        }

        @Override
        public org.wwz.ai.domain.agent.model.valobj.FixRoleVO queryRole(String agentId) {
            return null;
        }

        @Override
        public ConversationRoleVO buildConversationRole(AgentConversation conversation) {
            return null;
        }
    }
}

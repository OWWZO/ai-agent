package org.wwz.ai.test.domain.sessionmemory;

import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.agent.dto.Message;
import org.wwz.ai.domain.agent.reactor.agent.enums.RoleType;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionTurnMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptBlockType;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptContextBlock;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentStreamPersistCoordinator;
import org.wwz.ai.domain.agent.reactor.service.support.SessionArtifactRestoreSupport;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.Step1SopRecallAndPrepareNode;
import org.wwz.ai.domain.agent.service.execute.react.step.RootNode;

import java.util.List;

/**
 * richer working memory message 预装测试。
 */
public class AgentStreamPersistWorkingMemoryMessagesTest {

    @Test
    public void test_buildWorkingMemoryMessages_preservesStructuredTranscriptBlocks() {
        AgentStreamPersistCoordinator service = new AgentStreamPersistCoordinator();
        ReflectionTestUtils.setField(service, "sessionArtifactRestoreSupport", new SessionArtifactRestoreSupport());

        JSONObject artifactRef = SessionEventPayloadFixtureBuilder.artifactRef(
                "summary-report.html",
                "https://file.example.com/summary-report");
        SessionTurnMemory turn = SessionTurnMemory.builder()
                .sortOrder(3)
                .blocks(List.of(
                        block(TranscriptBlockType.USER_INPUT, "user", "继续基于上次结果补充", null, null, null, null, false),
                        block(TranscriptBlockType.ASSISTANT_THOUGHT, "assistant", "先复用上一轮 deep_search 结果", null, null, null, null, false),
                        block(TranscriptBlockType.TOOL_USE, "assistant", "准备调用 deep_search，参数：{\"query\":\"Spring AI MCP\"}",
                                "tool-search-1", "deep_search", "{\"query\":\"Spring AI MCP\"}", null, false),
                        block(TranscriptBlockType.TOOL_RESULT, "tool", "已生成 MCP 对比摘要，详见稳定引用",
                                "tool-search-1", "deep_search", null, List.of(artifactRef), true),
                        block(TranscriptBlockType.ARTIFACT_REFERENCE, "tool", "历史产物引用：summary-report.html",
                                null, null, null, List.of(artifactRef), true),
                        block(TranscriptBlockType.ASSISTANT_ANSWER, "assistant", "我补充了三条和 MCP 接入最相关的差异",
                                null, null, null, null, false)))
                .build();

        List<AgentRequest.Message> messages = buildWorkingMemoryMessages(service, SessionWorkingMemory.builder()
                .recentTurns(List.of(turn))
                .build());

        Assert.assertEquals(6, messages.size());

        AgentRequest.Message toolUseMessage = messages.get(2);
        Assert.assertEquals("assistant", toolUseMessage.getRole());
        Assert.assertEquals("tool_use", toolUseMessage.getMessageType());
        Assert.assertEquals("tool-search-1", toolUseMessage.getToolCalls().get(0).getId());
        Assert.assertEquals("deep_search", toolUseMessage.getToolCalls().get(0).getFunction().getName());
        Assert.assertEquals("{\"query\":\"Spring AI MCP\"}", toolUseMessage.getToolCalls().get(0).getFunction().getArguments());

        AgentRequest.Message toolResultMessage = messages.get(3);
        Assert.assertEquals("tool", toolResultMessage.getRole());
        Assert.assertEquals("tool_result", toolResultMessage.getMessageType());
        Assert.assertEquals("tool-search-1", toolResultMessage.getToolCallId());
        Assert.assertTrue(Boolean.TRUE.equals(toolResultMessage.getReferenceOnly()));
        Assert.assertEquals("summary-report.html", toolResultMessage.getFiles().get(0).getFileName());

        AgentRequest.Message artifactMessage = messages.get(4);
        Assert.assertEquals("artifact_reference", artifactMessage.getMessageType());
        Assert.assertEquals("summary-report.html", artifactMessage.getFiles().get(0).getFileName());
    }

    @Test
    public void test_buildWorkingMemoryMessages_skipsTurnWhenBlocksMissing() {
        AgentStreamPersistCoordinator service = new AgentStreamPersistCoordinator();
        ReflectionTestUtils.setField(service, "sessionArtifactRestoreSupport", new SessionArtifactRestoreSupport());

        SessionTurnMemory emptyTurn = SessionTurnMemory.builder().build();

        List<AgentRequest.Message> messages = buildWorkingMemoryMessages(service, SessionWorkingMemory.builder()
                .recentTurns(List.of(emptyTurn))
                .build());

        Assert.assertTrue(messages.isEmpty());
    }

    @Test
    public void test_applyStructuredWorkingMemory_usesSameRestoredLedgerForHistoryMessagesAndFiles() {
        AgentStreamPersistCoordinator service = new AgentStreamPersistCoordinator();
        ReflectionTestUtils.setField(service, "sessionArtifactRestoreSupport", new SessionArtifactRestoreSupport());

        JSONObject artifactRef = SessionEventPayloadFixtureBuilder.artifactRef(
                "summary-report.html",
                "https://file.example.com/summary-report");
        SessionWorkingMemory workingMemory = SessionWorkingMemory.builder()
                .historyDialogue("历史摘要：已经完成 deep_search 并生成 summary-report.html")
                .restoredFiles(List.of(FileInformation.builder()
                        .fileName("summary-report.html")
                        .domainUrl("https://file.example.com/summary-report")
                        .ossUrl("https://file.example.com/download/summary-report")
                        .resourceKey("summary-report-html")
                        .fileType("html")
                        .build()))
                .recentTurns(List.of(SessionTurnMemory.builder()
                        .blocks(List.of(
                                block(TranscriptBlockType.USER_INPUT, "user", "继续完善总结", null, null, null, null, false),
                                block(TranscriptBlockType.TOOL_RESULT, "tool", "已生成稳定报告引用",
                                        "tool-search-1", "deep_search", null, List.of(artifactRef), true)))
                        .build()))
                .build();
        List<FileInformation> currentRequestFiles = List.of(FileInformation.builder()
                .fileName("input.pdf")
                .domainUrl("https://file.example.com/input.pdf")
                .ossUrl("https://file.example.com/download/input.pdf")
                .resourceKey("input-pdf")
                .fileType("pdf")
                .build());

        AgentRequest request = new AgentRequest();
        ReflectionTestUtils.invokeMethod(service,
                "applyStructuredWorkingMemory",
                request,
                workingMemory,
                currentRequestFiles);

        Assert.assertEquals("历史摘要：已经完成 deep_search 并生成 summary-report.html", request.getHistoryDialogue());
        Assert.assertEquals(2, request.getMessages().size());
        Assert.assertEquals("user_input", request.getMessages().get(0).getMessageType());
        Assert.assertEquals("tool_result", request.getMessages().get(1).getMessageType());
        Assert.assertEquals(2, request.getSessionFiles().size());
        Assert.assertEquals("summary-report.html", request.getSessionFiles().get(0).getFileName());
        Assert.assertEquals("input.pdf", request.getSessionFiles().get(1).getFileName());
    }

    @Test
    public void test_rootNode_degradesStructuredToolHistoryToPlainAssistantMessages() {
        AgentRequest.Message toolUseMessage = AgentRequest.Message.builder()
                .role("assistant")
                .messageType("tool_use")
                .content("准备调用 deep_search")
                .toolCalls(List.of(org.wwz.ai.domain.agent.reactor.agent.dto.tool.ToolCall.builder()
                        .id("tool-search-1")
                        .type("function")
                        .function(org.wwz.ai.domain.agent.reactor.agent.dto.tool.ToolCall.Function.builder()
                                .name("deep_search")
                                .arguments("{\"query\":\"Spring AI MCP\"}")
                                .build())
                        .build()))
                .build();
        AgentRequest.Message toolResultMessage = AgentRequest.Message.builder()
                .role("tool")
                .messageType("tool_result")
                .content("已返回工具结果")
                .toolCallId("tool-search-1")
                .build();
        AgentRequest.Message artifactMessage = AgentRequest.Message.builder()
                .role("tool")
                .messageType("artifact_reference")
                .content("历史产物引用：summary-report.html")
                .build();

        RootNode rootNode = new RootNode();
        List<Message> reactMessages = convertMessages(rootNode, List.of(toolUseMessage, toolResultMessage, artifactMessage));

        Assert.assertEquals(3, reactMessages.size());
        Assert.assertEquals(RoleType.ASSISTANT, reactMessages.get(0).getRole());
        Assert.assertNull(reactMessages.get(0).getToolCalls());
        Assert.assertTrue(reactMessages.get(0).getContent().contains("准备调用 deep_search"));
        Assert.assertTrue(reactMessages.get(0).getContent().contains("历史工具调用：deep_search"));
        Assert.assertTrue(reactMessages.get(0).getContent().contains("参数：{\"query\":\"Spring AI MCP\"}"));

        Assert.assertEquals(RoleType.ASSISTANT, reactMessages.get(1).getRole());
        Assert.assertNull(reactMessages.get(1).getToolCallId());
        Assert.assertTrue(reactMessages.get(1).getContent().contains("历史工具结果"));
        Assert.assertTrue(reactMessages.get(1).getContent().contains("已返回工具结果"));

        Assert.assertEquals(RoleType.ASSISTANT, reactMessages.get(2).getRole());
        Assert.assertEquals("历史产物引用：summary-report.html", reactMessages.get(2).getContent());
    }

    @Test
    public void test_planNode_stillPreservesStructuredToolChainSemantics() {
        AgentRequest.Message toolUseMessage = AgentRequest.Message.builder()
                .role("assistant")
                .messageType("tool_use")
                .content("准备调用 deep_search")
                .toolCalls(List.of(org.wwz.ai.domain.agent.reactor.agent.dto.tool.ToolCall.builder()
                        .id("tool-search-1")
                        .type("function")
                        .function(org.wwz.ai.domain.agent.reactor.agent.dto.tool.ToolCall.Function.builder()
                                .name("deep_search")
                                .arguments("{\"query\":\"Spring AI MCP\"}")
                                .build())
                        .build()))
                .build();
        AgentRequest.Message toolResultMessage = AgentRequest.Message.builder()
                .role("tool")
                .messageType("tool_result")
                .content("已返回工具结果")
                .toolCallId("tool-search-1")
                .build();

        Step1SopRecallAndPrepareNode planNode = new Step1SopRecallAndPrepareNode();
        List<Message> planMessages = convertMessages(planNode, List.of(toolUseMessage, toolResultMessage));

        Assert.assertEquals(2, planMessages.size());
        Assert.assertEquals(RoleType.ASSISTANT, planMessages.get(0).getRole());
        Assert.assertEquals("tool-search-1", planMessages.get(0).getToolCalls().get(0).getId());
        Assert.assertEquals(RoleType.TOOL, planMessages.get(1).getRole());
        Assert.assertEquals("tool-search-1", planMessages.get(1).getToolCallId());
    }

    @SuppressWarnings("unchecked")
    private List<AgentRequest.Message> buildWorkingMemoryMessages(AgentStreamPersistCoordinator service,
                                                                  SessionWorkingMemory workingMemory) {
        return (List<AgentRequest.Message>) ReflectionTestUtils.invokeMethod(
                service,
                "buildWorkingMemoryMessages",
                workingMemory);
    }

    @SuppressWarnings("unchecked")
    private List<Message> convertMessages(Object node, List<AgentRequest.Message> messages) {
        return (List<Message>) ReflectionTestUtils.invokeMethod(node, "convertMessages", messages);
    }

    private TranscriptContextBlock block(TranscriptBlockType blockType,
                                         String role,
                                         String text,
                                         String toolUseId,
                                         String toolName,
                                         String toolArgumentsJson,
                                         List<JSONObject> artifactRefs,
                                         boolean referenceOnly) {
        return TranscriptContextBlock.builder()
                .blockType(blockType)
                .role(role)
                .text(text)
                .toolUseId(toolUseId)
                .toolName(toolName)
                .toolArgumentsJson(toolArgumentsJson)
                .artifactRefs(artifactRefs)
                .referenceOnly(referenceOnly)
                .build();
    }
}

package org.wwz.ai.test.domain;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.model.history.ConversationTurnDetail;
import org.wwz.ai.domain.agent.reactor.service.support.ConversationEventPayloadNormalizer;
import org.wwz.ai.domain.agent.reactor.service.support.ConversationReplayAssembler;

import java.util.List;
import java.util.Map;

public class ConversationHistoryArtifactTest {

    @Test
    public void test_artifactPayloadKeepsStableReference() {
        ConversationReplayAssembler assembler = new ConversationReplayAssembler();
        AgentMessage message = AgentMessage.builder()
                .id(1L)
                .requestId("req-001")
                .sortOrder(0)
                .query("做一份报告")
                .agentType(2)
                .status(1)
                .forceStop(0)
                .build();

        AgentMessageEvent event = AgentMessageEvent.builder()
                .messageId(1L)
                .seqNo(1)
                .eventType("tool_result")
                .eventSubType("markdown.report")
                .displayArea("workspace")
                .title("总结完成")
                .referenceOnly(true)
                .artifactRefsJson("""
                        [{"displayName":"report.md","resourceKey":"file-001","downloadUrl":"https://file.example.com/download/001","previewUrl":"https://file.example.com/download/001","missing":false}]
                        """)
                .structuredDataJson("""
                        {"messageType":"markdown","answer":"请通过稳定引用查看报告正文","isFinal":true}
                        """)
                .status("completed")
                .build();

        List<ConversationTurnDetail> turns = assembler.assembleTurns(List.of(message), Map.of(1L, List.of(event)));
        Object payload = turns.get(0).getEvents().get(0).getPayload();

        Assert.assertTrue(payload instanceof JSONObject);
        JSONArray refs = ((JSONObject) payload).getJSONArray("artifactRefs");
        Assert.assertEquals("file-001", refs.getJSONObject(0).getString("resourceKey"));
        Assert.assertEquals("https://file.example.com/download/001", refs.getJSONObject(0).getString("downloadUrl"));
        Assert.assertEquals("history:1:1", turns.get(0).getEvents().get(0).getMessageIdExt());
        Assert.assertEquals(Integer.valueOf(1), turns.get(0).getEvents().get(0).getIsFinal());
    }

    @Test
    public void test_artifactMissingStateIsExplicit() {
        ConversationReplayAssembler assembler = new ConversationReplayAssembler();
        AgentMessage message = AgentMessage.builder()
                .id(2L)
                .requestId("req-002")
                .sortOrder(0)
                .query("查看缺失文件")
                .agentType(2)
                .status(1)
                .forceStop(0)
                .build();

        AgentMessageEvent event = AgentMessageEvent.builder()
                .messageId(2L)
                .seqNo(1)
                .eventType("tool_result")
                .eventSubType("markdown.report")
                .displayArea("workspace")
                .title("内容缺失")
                .referenceOnly(true)
                .artifactRefsJson("""
                        [{"displayName":"缺失文件.md","resourceKey":"file-404","missing":true,"missingReason":"resource not found"}]
                        """)
                .structuredDataJson("""
                        {"messageType":"markdown","answer":"资源已失效","isFinal":true}
                        """)
                .status("error")
                .build();

        List<ConversationTurnDetail> turns = assembler.assembleTurns(List.of(message), Map.of(2L, List.of(event)));
        JSONObject payload = (JSONObject) turns.get(0).getEvents().get(0).getPayload();
        JSONObject ref = payload.getJSONArray("artifactRefs").getJSONObject(0);

        Assert.assertTrue(ref.getBooleanValue("missing"));
        Assert.assertEquals("resource not found", ref.getString("missingReason"));
    }

    @Test
    public void test_legacyFileInfoIsNormalizedToArtifactRefsDuringWrite() {
        JSONObject payload = JSONObject.parseObject("""
                {
                  "messageType":"deep_search",
                  "taskId":"task-1",
                  "resultMap":{
                    "messageType":"deep_search",
                    "resultMap":{
                      "messageType":"report",
                      "fileInfo":[
                        {
                          "fileName":"summary.md",
                          "domainUrl":"https://file.example.com/preview/summary",
                          "downloadUrl":"https://file.example.com/download/summary",
                          "resourceKey":"file-summary"
                        }
                      ]
                    }
                  }
                }
                """);

        JSONObject normalized = ConversationEventPayloadNormalizer.normalizePayload(payload);
        JSONArray refs = normalized.getJSONArray("artifactRefs");

        Assert.assertNotNull(refs);
        Assert.assertEquals(1, refs.size());
        Assert.assertEquals("summary.md", refs.getJSONObject(0).getString("displayName"));
        Assert.assertEquals("file-summary", refs.getJSONObject(0).getString("resourceKey"));
        Assert.assertNull(normalized.getJSONObject("resultMap").getJSONObject("resultMap").get("fileInfo"));
    }

    @Test
    public void test_semanticArtifactRefsReplayWithoutLegacyFileInfoFallback() {
        ConversationReplayAssembler assembler = new ConversationReplayAssembler();
        AgentMessage message = AgentMessage.builder()
                .id(3L)
                .requestId("req-003")
                .sortOrder(1)
                .query("继续打开旧报告")
                .agentType(2)
                .status(1)
                .forceStop(0)
                .build();

        AgentMessageEvent event = AgentMessageEvent.builder()
                .messageId(3L)
                .seqNo(1)
                .eventType("tool_result")
                .eventSubType("html.page")
                .displayArea("workspace")
                .title("旧版报告")
                .referenceOnly(true)
                .artifactRefsJson("""
                        [{"displayName":"legacy-report.html","resourceKey":"legacy-report","downloadUrl":"https://file.example.com/download/legacy-report","previewUrl":"https://file.example.com/legacy-report","missing":false}]
                        """)
                .structuredDataJson("""
                        {"messageType":"html","answer":"请通过稳定引用打开旧版报告","isFinal":true}
                        """)
                .status("completed")
                .build();

        List<ConversationTurnDetail> turns = assembler.assembleTurns(List.of(message), Map.of(3L, List.of(event)));
        JSONObject payload = (JSONObject) turns.get(0).getEvents().get(0).getPayload();

        Assert.assertEquals("legacy-report", payload.getJSONArray("artifactRefs").getJSONObject(0).getString("resourceKey"));
        Assert.assertEquals("html", payload.getJSONObject("resultMap").getString("messageType"));
    }

    @Test
    public void test_mragMarkdownArtifactCanReplayThroughExistingPayload() {
        ConversationReplayAssembler assembler = new ConversationReplayAssembler();
        AgentMessage message = AgentMessage.builder()
                .id(4L)
                .requestId("req-004")
                .sortOrder(1)
                .query("总结多模态检索核心能力")
                .agentType(2)
                .status(1)
                .forceStop(0)
                .build();

        AgentMessageEvent event = AgentMessageEvent.builder()
                .messageId(4L)
                .seqNo(1)
                .eventType("tool_result")
                .eventSubType("markdown.report")
                .displayArea("workspace")
                .title("多模态检索结果")
                .referenceOnly(true)
                .artifactRefsJson("""
                        [{"displayName":"多模态检索结果.md","resourceKey":"mrag-result-md","downloadUrl":"https://file.example.com/download/mrag-result-md","previewUrl":"https://file.example.com/preview/mrag-result-md","missing":false}]
                        """)
                .structuredDataJson("""
                        {"messageType":"markdown","answer":"请通过稳定引用查看多模态检索结果","isFinal":true}
                        """)
                .status("completed")
                .build();

        List<ConversationTurnDetail> turns = assembler.assembleTurns(List.of(message), Map.of(4L, List.of(event)));
        JSONObject payload = (JSONObject) turns.get(0).getEvents().get(0).getPayload();

        Assert.assertEquals("mrag-result-md", payload.getJSONArray("artifactRefs").getJSONObject(0).getString("resourceKey"));
        Assert.assertEquals("多模态检索结果.md", payload.getJSONArray("artifactRefs").getJSONObject(0).getString("displayName"));
    }

    @Test
    public void test_filePayloadIsCompactedToReferenceOnlyShape() {
        JSONObject payload = JSONObject.parseObject("""
                {
                  "messageType":"markdown",
                  "resultMap":{
                    "messageType":"markdown",
                    "resultMap":{
                      "messageType":"markdown",
                      "codeOutput":"# 完整正文",
                      "data":"# 完整正文",
                      "answer":"不应再内联"
                    }
                  },
                  "artifactRefs":[
                    {
                      "displayName":"report.md",
                      "resourceKey":"report-md",
                      "previewUrl":"https://file.example.com/preview/report.md",
                      "downloadUrl":"https://file.example.com/download/report.md"
                    }
                  ]
                }
                """);

        JSONObject normalized = ConversationEventPayloadNormalizer.normalizePayload(payload);
        JSONObject nestedResultMap = normalized.getJSONObject("resultMap").getJSONObject("resultMap");

        Assert.assertTrue(normalized.getBooleanValue("referenceOnly"));
        Assert.assertNull(nestedResultMap.get("codeOutput"));
        Assert.assertNull(nestedResultMap.get("data"));
        Assert.assertNull(nestedResultMap.get("answer"));
        Assert.assertEquals("report-md", normalized.getJSONArray("artifactRefs").getJSONObject(0).getString("resourceKey"));
    }

    @Test
    public void test_generatedFilesAreExposedSeparatelyInConversationTurn() {
        ConversationReplayAssembler assembler = new ConversationReplayAssembler();
        AgentMessage message = AgentMessage.builder()
                .id(5L)
                .requestId("req-005")
                .sortOrder(2)
                .query("生成结果文件")
                .filesJson("""
                        [
                          {
                            "name":"输入资料.pdf",
                            "url":"https://file.example.com/input.pdf",
                            "downloadUrl":"https://file.example.com/input-download.pdf",
                            "type":"pdf"
                          }
                        ]
                        """)
                .generatedFilesJson("""
                        [
                          {
                            "fileName":"输出结果.md",
                            "domainUrl":"https://file.example.com/output.md",
                            "ossUrl":"https://file.example.com/output-download.md",
                            "fileType":"markdown",
                            "resourceKey":"output-md"
                          }
                        ]
                        """)
                .agentType(2)
                .status(1)
                .forceStop(0)
                .build();

        List<ConversationTurnDetail> turns = assembler.assembleTurns(List.of(message), Map.of(5L, List.of()));

        JSONObject uploadFile = ((JSONArray) turns.get(0).getFiles()).getJSONObject(0);
        JSONObject generatedFile = ((JSONArray) turns.get(0).getGeneratedFiles()).getJSONObject(0);
        Assert.assertEquals("输入资料.pdf", uploadFile.getString("name"));
        Assert.assertEquals("输出结果.md", generatedFile.getString("fileName"));
        Assert.assertEquals("output-md", generatedFile.getString("resourceKey"));
    }

    @Test
    public void test_semanticFactEventsReplayToCanonicalTaskPayload() {
        ConversationReplayAssembler assembler = new ConversationReplayAssembler();
        AgentMessage message = AgentMessage.builder()
                .id(6L)
                .requestId("req-006")
                .sortOrder(3)
                .query("继续补充历史结论")
                .generatedFilesJson("""
                        [
                          {
                            "fileName":"semantic-report.md",
                            "domainUrl":"https://file.example.com/semantic-report.md",
                            "ossUrl":"https://file.example.com/download/semantic-report.md",
                            "fileType":"markdown",
                            "resourceKey":"semantic-report-md"
                          }
                        ]
                        """)
                .agentType(2)
                .status(1)
                .forceStop(0)
                .build();

        AgentMessageEvent thoughtEvent = AgentMessageEvent.builder()
                .messageId(6L)
                .seqNo(1)
                .eventType("assistant_thought")
                .eventSubType("tool")
                .displayArea("timeline")
                .taskId("task-6")
                .taskOrder(1)
                .toolUseId("tool-semantic-6")
                .toolName("deep_search")
                .toolArgumentsJson("""
                        {"query":"Spring AI MCP"}
                        """)
                .contentText("先复用上一轮 deep_search 的搜索条件")
                .status("completed")
                .build();
        AgentMessageEvent toolUseEvent = AgentMessageEvent.builder()
                .messageId(6L)
                .seqNo(2)
                .eventType("tool_use")
                .eventSubType("deep_search")
                .displayArea("timeline")
                .taskId("task-6")
                .taskOrder(1)
                .toolUseId("tool-semantic-6")
                .toolName("deep_search")
                .toolArgumentsJson("""
                        {"query":"Spring AI MCP"}
                        """)
                .status("completed")
                .build();
        AgentMessageEvent resultEvent = AgentMessageEvent.builder()
                .messageId(6L)
                .seqNo(3)
                .eventType("tool_result")
                .eventSubType("markdown.report")
                .displayArea("workspace")
                .taskId("task-6")
                .taskOrder(1)
                .toolUseId("tool-semantic-6")
                .toolName("deep_search")
                .toolArgumentsJson("""
                        {"query":"Spring AI MCP"}
                        """)
                .contentText("已生成最终 Markdown 报告，请通过稳定引用打开。")
                .referenceOnly(true)
                .artifactRefsJson("""
                        [{"displayName":"semantic-report.md","resourceKey":"semantic-report-md","downloadUrl":"https://file.example.com/download/semantic-report.md","previewUrl":"https://file.example.com/semantic-report.md","missing":false}]
                        """)
                .structuredDataJson("""
                        {"messageType":"markdown","answer":"已生成最终 Markdown 报告，请通过稳定引用打开。","isFinal":true}
                        """)
                .status("completed")
                .build();

        List<ConversationTurnDetail> turns = assembler.assembleTurns(
                List.of(message),
                Map.of(6L, List.of(thoughtEvent, toolUseEvent, resultEvent)));

        Assert.assertEquals(1, turns.size());
        Assert.assertEquals(2, turns.get(0).getEvents().size());

        JSONObject thoughtPayload = (JSONObject) turns.get(0).getEvents().get(0).getPayload();
        Assert.assertEquals("task", turns.get(0).getEvents().get(0).getEventType());
        Assert.assertEquals("task", thoughtPayload.getString("messageType"));
        Assert.assertEquals("tool_thought", thoughtPayload.getJSONObject("resultMap").getString("messageType"));

        JSONObject resultPayload = (JSONObject) turns.get(0).getEvents().get(1).getPayload();
        Assert.assertEquals("markdown", turns.get(0).getEvents().get(1).getEventType());
        Assert.assertEquals("task", resultPayload.getString("messageType"));
        Assert.assertEquals("semantic-report-md", resultPayload.getJSONArray("artifactRefs").getJSONObject(0).getString("resourceKey"));
        Assert.assertEquals("semantic-report.md", ((JSONArray) turns.get(0).getGeneratedFiles()).getJSONObject(0).getString("fileName"));
    }
}

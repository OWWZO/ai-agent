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
                .eventType("deep_search")
                .eventSubType("report")
                .displayArea("workspace")
                .title("总结完成")
                .payloadJson("""
                        {"messageType":"task","messageId":"artifact-1","artifactRefs":[{"resourceKey":"file-001","downloadUrl":"https://file.example.com/download/001","missing":false}]}
                        """)
                .status("completed")
                .build();

        List<ConversationTurnDetail> turns = assembler.assembleTurns(List.of(message), Map.of(1L, List.of(event)));
        Object payload = turns.get(0).getEvents().get(0).getPayload();

        Assert.assertTrue(payload instanceof JSONObject);
        JSONArray refs = ((JSONObject) payload).getJSONArray("artifactRefs");
        Assert.assertEquals("file-001", refs.getJSONObject(0).getString("resourceKey"));
        Assert.assertEquals("https://file.example.com/download/001", refs.getJSONObject(0).getString("downloadUrl"));
        Assert.assertEquals("artifact-1", turns.get(0).getEvents().get(0).getMessageIdExt());
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
                .eventType("deep_search")
                .eventSubType("report")
                .displayArea("workspace")
                .title("内容缺失")
                .payloadJson("""
                        {"messageType":"task","messageId":"artifact-404","artifactRefs":[{"resourceKey":"file-404","missing":true,"missingReason":"resource not found"}]}
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
    public void test_legacyFileInfoIsNormalizedToArtifactRefsDuringReplay() {
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
                .eventType("html")
                .eventSubType("report")
                .title("旧版报告")
                .payloadJson("""
                        {
                          "messageType":"html",
                          "resultMap":{
                            "resultMap":{
                              "fileInfo":[
                                {
                                  "fileName":"legacy-report.html",
                                  "domainUrl":"https://file.example.com/legacy-report",
                                  "downloadUrl":"https://file.example.com/download/legacy-report",
                                  "resourceKey":"legacy-report"
                                }
                              ]
                            }
                          }
                        }
                        """)
                .status("completed")
                .build();

        List<ConversationTurnDetail> turns = assembler.assembleTurns(List.of(message), Map.of(3L, List.of(event)));
        JSONObject payload = (JSONObject) turns.get(0).getEvents().get(0).getPayload();

        Assert.assertEquals("legacy-report", payload.getJSONArray("artifactRefs").getJSONObject(0).getString("resourceKey"));
        Assert.assertNull(payload.getJSONObject("resultMap").getJSONObject("resultMap").get("fileInfo"));
    }
}

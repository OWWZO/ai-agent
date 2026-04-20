package org.wwz.ai.test.domain.sessionmemory;

import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionTurnMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptBlockType;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptContextBlock;
import org.wwz.ai.domain.agent.reactor.service.support.SessionArtifactRestoreSupport;
import org.wwz.ai.domain.agent.reactor.service.support.SessionTranscriptBlockAssembler;

import java.util.List;
import java.util.stream.Collectors;

/**
 * transcript block 组装规则测试。
 */
public class SessionTranscriptBlockAssemblerTest {

    @Test
    public void test_buildTurnMemory_pairsRepeatedToolCallsAndMarksReferenceOnly() {
        SessionTranscriptBlockAssembler assembler = new SessionTranscriptBlockAssembler();
        ReflectionTestUtils.setField(assembler, "artifactRestoreSupport", new SessionArtifactRestoreSupport());

        AgentMessage message = SessionMemoryTestSupport.completedMessage(
                501L,
                "req-transcript-001",
                3,
                "继续沿用刚才搜索结果补充结论",
                "我已经把关键结论补充好了。",
                null);

        List<AgentMessageEvent> events = List.of(
                SessionEventPayloadFixtureBuilder.toolThoughtEvent(
                        501L,
                        1,
                        "tool-search-1",
                        "deep_search",
                        JSONObject.parseObject("{\"query\":\"Spring AI MCP 基础\"}"),
                        "先回看第一轮检索结果",
                        "task-search-1",
                        1),
                SessionEventPayloadFixtureBuilder.toolResultEvent(
                        501L,
                        2,
                        "deep_search",
                        "search",
                        "tool-search-1",
                        "deep_search",
                        JSONObject.parseObject("{\"query\":\"Spring AI MCP 基础\"}"),
                        "已经拿到第一轮搜索结果",
                        "task-search-1",
                        1,
                        List.of()),
                SessionEventPayloadFixtureBuilder.toolThoughtEvent(
                        501L,
                        3,
                        "tool-search-2",
                        "deep_search",
                        JSONObject.parseObject("{\"query\":\"Spring AI skilltool\"}"),
                        "继续检索 skilltool 相关资料",
                        "task-search-2",
                        2),
                SessionEventPayloadFixtureBuilder.toolResultEvent(
                        501L,
                        4,
                        "deep_search",
                        "report",
                        null,
                        "deep_search",
                        JSONObject.parseObject("{\"query\":\"Spring AI skilltool\"}"),
                        "已生成详细报告，正文不应被整段回灌到 working memory",
                        "task-search-2",
                        2,
                        List.of(SessionEventPayloadFixtureBuilder.artifactRef(
                                "deepsearch-report.html",
                                "https://file.example.com/deepsearch-report"))));

        SessionTurnMemory turnMemory = assembler.buildTurnMemory(message, events);
        List<TranscriptContextBlock> blocks = turnMemory.getBlocks();

        Assert.assertEquals(List.of(
                TranscriptBlockType.USER_INPUT,
                TranscriptBlockType.ASSISTANT_THOUGHT,
                TranscriptBlockType.TOOL_USE,
                TranscriptBlockType.TOOL_RESULT,
                TranscriptBlockType.ASSISTANT_THOUGHT,
                TranscriptBlockType.TOOL_USE,
                TranscriptBlockType.TOOL_RESULT,
                TranscriptBlockType.ARTIFACT_REFERENCE,
                TranscriptBlockType.ASSISTANT_ANSWER), blocks.stream()
                .map(TranscriptContextBlock::getBlockType)
                .collect(Collectors.toList()));

        TranscriptContextBlock firstResult = blocks.get(3);
        Assert.assertEquals("tool-search-1", firstResult.getToolUseId());
        Assert.assertFalse(Boolean.TRUE.equals(firstResult.getReferenceOnly()));

        TranscriptContextBlock secondResult = blocks.get(6);
        Assert.assertEquals("tool-search-2", secondResult.getToolUseId());
        Assert.assertEquals("deep_search", secondResult.getToolName());
        Assert.assertTrue(Boolean.TRUE.equals(secondResult.getReferenceOnly()));
        Assert.assertEquals("deepsearch-report.html", secondResult.getArtifactRefs().get(0).getString("displayName"));

        Assert.assertEquals("我已经把关键结论补充好了。", turnMemory.getFinalAnswer());
        Assert.assertEquals(1, turnMemory.getArtifactRefs().size());
        Assert.assertEquals("deepsearch-report.html", turnMemory.getArtifactRefs().get(0).getString("displayName"));
    }
}

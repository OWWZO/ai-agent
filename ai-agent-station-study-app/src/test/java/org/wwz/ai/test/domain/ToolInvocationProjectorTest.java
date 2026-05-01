package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.model.ledger.ArtifactView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.DeepSearchDoc;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.DeepSearchQueryResult;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.DeepSearchStage;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.DeepSearchToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ImageGenerationToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.MultimodalAgentToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.ToolInvocationProjectorRegistry;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.CodeInterpreterToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.DataAnalysisToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.DefaultToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.DeepSearchToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.FileToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.ImageGenerationToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.MultiModalToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.ReportToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.ScriptRunnerToolInvocationProjector;

import java.util.List;
import java.util.Map;

/**
 * Tool invocation projector 契约测试。
 * 验证历史 replay 会按 tool_name + structuredOutput 分发，而不是直接复用前端 payload。
 */
public class ToolInvocationProjectorTest {

    private final ToolInvocationProjectorRegistry registry = new ToolInvocationProjectorRegistry(
            List.of(
                    new CodeInterpreterToolInvocationProjector(),
                    new ReportToolInvocationProjector(),
                    new DataAnalysisToolInvocationProjector(),
                    new FileToolInvocationProjector(),
                    new DeepSearchToolInvocationProjector(),
                    new MultiModalToolInvocationProjector(),
                    new ImageGenerationToolInvocationProjector(),
                    new ScriptRunnerToolInvocationProjector(),
                    new DefaultToolInvocationProjector()
            ),
            new DefaultToolInvocationProjector()
    );

    @Test
    public void shouldProjectPlainTextFallbackViaDefaultProjector() {
        ToolInvocationView invocation = ToolInvocationView.builder()
                .toolCallId("tool-call-read-001")
                .toolName("read_tool")
                .llmObservation("hello")
                .build();

        List<ProjectedReplayEvent> events = registry.project(invocation, List.of(), new EventResult());

        Assert.assertEquals(1, events.size());
        Assert.assertEquals("task", events.get(0).getMessageType());
        Assert.assertEquals("tool_result", resultMap(events.get(0)).get("messageType"));
    }

    @Test
    public void shouldProjectFileToolJsonToTaskEventData() {
        ToolInvocationView invocation = ToolInvocationView.builder()
                .id(11L)
                .toolCallId("tool-call-file-001")
                .toolName("file_tool")
                .inputJson("{\"command\":\"get\",\"fileName\":\"风险日报.md\"}")
                .structuredOutput(FileToolOutput.builder()
                        .command("get")
                        .primaryFileName("风险日报.md")
                        .contentStorageMode("artifact_only")
                        .fileRefs(List.of(ToolFileRef.builder().fileName("风险日报.md").build()))
                        .build())
                .build();
        ArtifactView artifact = ArtifactView.builder()
                .toolInvocationId(11L)
                .toolCallId("tool-call-file-001")
                .fileName("风险日报.md")
                .downloadUrl("https://file.example.com/download/risk.md")
                .previewUrl("https://file.example.com/preview/risk.md")
                .storageKey("artifact-key-risk")
                .build();

        List<ProjectedReplayEvent> events = registry.project(invocation, List.of(artifact), new EventResult());

        Assert.assertEquals(1, events.size());
        Assert.assertEquals("task", events.get(0).getMessageType());
        Assert.assertEquals("file", resultMap(events.get(0)).get("messageType"));
        Assert.assertEquals("读取文件", nestedResultMap(events.get(0)).get("command"));
        Assert.assertEquals(1, events.get(0).getArtifactRefs().size());
    }

    @Test
    public void shouldProjectDeepSearchStagesFromNativeJson() {
        ToolInvocationView invocation = ToolInvocationView.builder()
                .toolCallId("tool-call-search-001")
                .toolName("deep_search")
                .inputJson("{\"query\":\"本周项目风险\"}")
                .structuredOutput(DeepSearchToolOutput.builder()
                        .query("本周项目风险")
                        .stages(List.of(
                                DeepSearchStage.builder()
                                        .stage("extend")
                                        .queries(List.of("项目排期风险"))
                                        .build(),
                                DeepSearchStage.builder()
                                        .stage("search")
                                        .results(List.of(DeepSearchQueryResult.builder()
                                                .query("项目排期风险")
                                                .docs(List.of(DeepSearchDoc.builder()
                                                        .title("风险日报")
                                                        .link("https://example.com/risk")
                                                        .build()))
                                                .build()))
                                        .build(),
                                DeepSearchStage.builder()
                                        .stage("report")
                                        .answer("本周主要风险有...")
                                        .build()
                        ))
                        .build())
                .build();

        List<ProjectedReplayEvent> events = registry.project(invocation, List.of(), new EventResult());

        Assert.assertEquals(3, events.size());
        Assert.assertEquals("task", events.get(0).getMessageType());
        Assert.assertEquals("deep_search", resultMap(events.get(0)).get("messageType"));
        Assert.assertEquals("extend", nestedResultMap(events.get(0)).get("messageType"));
        Assert.assertEquals("report", nestedResultMap(events.get(2)).get("messageType"));
    }

    @Test
    public void shouldProjectImageGenerationToFileAndToolResult() {
        ToolInvocationView invocation = ToolInvocationView.builder()
                .id(22L)
                .toolCallId("tool-call-image-001")
                .toolName("image_generation_tool")
                .inputJson("{\"prompt\":\"生成一张海报\"}")
                .structuredOutput(ImageGenerationToolOutput.builder()
                        .prompt("生成一张海报")
                        .summary("已生成图片文件：poster.png")
                        .fileRefs(List.of(ToolFileRef.builder().fileName("poster.png").build()))
                        .build())
                .build();
        ArtifactView artifact = ArtifactView.builder()
                .toolInvocationId(22L)
                .toolCallId("tool-call-image-001")
                .fileName("poster.png")
                .downloadUrl("https://file.example.com/download/poster.png")
                .previewUrl("https://file.example.com/preview/poster.png")
                .storageKey("artifact-poster")
                .build();

        List<ProjectedReplayEvent> events = registry.project(invocation, List.of(artifact), new EventResult());

        Assert.assertEquals(2, events.size());
        Assert.assertEquals("file", resultMap(events.get(0)).get("messageType"));
        Assert.assertEquals("生成图片", nestedResultMap(events.get(0)).get("command"));
        Assert.assertEquals("tool_result", resultMap(events.get(1)).get("messageType"));
        Assert.assertEquals("image_generation_tool", toolResult(events.get(1)).get("toolName"));
    }

    @Test
    public void shouldProjectMultiModalMarkdownFromNativeJson() {
        ToolInvocationView invocation = ToolInvocationView.builder()
                .toolCallId("tool-call-multi-001")
                .toolName("multimodalagent_tool")
                .structuredOutput(MultimodalAgentToolOutput.builder()
                        .summary("多模态检索摘要")
                        .markdownContent("# 结论\n多模态检索结果")
                        .build())
                .build();

        List<ProjectedReplayEvent> events = registry.project(invocation, List.of(), new EventResult());

        Assert.assertEquals(1, events.size());
        Assert.assertEquals("markdown", resultMap(events.get(0)).get("messageType"));
        Assert.assertEquals("# 结论\n多模态检索结果", nestedResultMap(events.get(0)).get("data"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resultMap(ProjectedReplayEvent event) {
        return (Map<String, Object>) event.getResultMap();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedResultMap(ProjectedReplayEvent event) {
        return (Map<String, Object>) resultMap(event).get("resultMap");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toolResult(ProjectedReplayEvent event) {
        return (Map<String, Object>) resultMap(event).get("toolResult");
    }
}

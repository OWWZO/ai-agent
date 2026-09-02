package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.CanvasPublishToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRefMapper;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.CanvasPublishToolInvocationProjector;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.DefaultToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse;

import java.util.List;
import java.util.Map;

public class ToolInvocationProjectorRelativePathTest {

    @Test
    public void defaultProjectorPutsRelativePathOnArtifactRefs() {
        ArtifactView css = ArtifactView.builder()
                .fileName("style.css")
                .storageKey("artifact-style")
                .downloadUrl("https://file.example.com/site/css/style.css")
                .previewUrl("https://file.example.com/preview/site/css/style.css")
                .metadataJson("{\"relativePath\":\"site/css/style.css\",\"originFileName\":\"site/css/style.css\"}")
                .build();
        ToolInvocationView invocation = ToolInvocationView.builder()
                .toolCallId("call-write-001")
                .toolName("workspace_write")
                .llmObservation("写入 site/css/style.css")
                .build();

        List<ProjectedReplayEvent> events = new DefaultToolInvocationProjector()
                .project(invocation, List.of(css), new EventResult());

        Assert.assertEquals(1, events.size());
        Assert.assertEquals("site/css/style.css", events.get(0).getArtifactRefs().get(0).get("relativePath"));
        Assert.assertEquals("site/css/style.css", events.get(0).getArtifactRefs().get(0).get("originFileName"));
    }

    @Test
    public void canvasReplayEnrichesFileInfoFromArtifactMetadata() {
        ArtifactView html = ArtifactView.builder()
                .fileName("index.html")
                .storageKey("artifact-index")
                .downloadUrl("https://file.example.com/site/index.html")
                .previewUrl("https://file.example.com/preview/site/index.html")
                .metadataJson("{\"relativePath\":\"site/index.html\"}")
                .build();
        CanvasPublishToolOutput output = CanvasPublishToolOutput.builder()
                .title("页面")
                .primaryFileName("index.html")
                .previewUrl(html.getPreviewUrl())
                .downloadUrl(html.getDownloadUrl())
                .fileRefs(List.of(ToolFileRef.builder()
                        .fileName("index.html")
                        .downloadUrl(html.getDownloadUrl())
                        .previewUrl(html.getPreviewUrl())
                        .build()))
                .build();
        ToolInvocationView invocation = ToolInvocationView.builder()
                .toolCallId("call-canvas-001")
                .toolName("canvas_publish")
                .structuredOutput(output)
                .build();

        List<ProjectedReplayEvent> events = new CanvasPublishToolInvocationProjector()
                .project(invocation, List.of(html), new EventResult());

        Map<String, Object> response = castMap(events.get(0).getResultMap());
        Map<String, Object> resultMap = castMap(response.get("resultMap"));
        List<?> fileInfo = (List<?>) resultMap.get("fileInfo");
        Assert.assertEquals("site/index.html", castMap(fileInfo.get(0)).get("relativePath"));
        Assert.assertEquals("site/index.html", events.get(0).getArtifactRefs().get(0).get("relativePath"));
    }

    @Test
    public void mapperKeepsRelativePathFromFileInfo() {
        List<ToolFileRef> refs = ToolFileRefMapper.fromCodeInterpreterFileInfo(List.of(
                CodeInterpreterResponse.FileInfo.builder()
                        .fileName("style.css")
                        .relativePath("site/css/style.css")
                        .ossUrl("https://file.example.com/site/css/style.css")
                        .domainUrl("https://file.example.com/preview/site/css/style.css")
                        .fileSize(8)
                        .build()
        ));
        Assert.assertEquals(1, refs.size());
        Assert.assertEquals("site/css/style.css", refs.get(0).getRelativePath());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}

package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.model.ledger.ArtifactView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.reactor.model.replay.ReplayFactBundle;
import org.wwz.ai.domain.agent.reactor.service.replay.ReplayProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.ToolInvocationProjectorRegistry;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.DefaultToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.DeepSearchToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.impl.FileToolInvocationProjector;

import java.util.List;
import java.util.Map;

/**
 * ReplayProjector 回归。
 * 验证共享回放入口会按 invocation 顺序委托 registry，而不是自己硬编码工具分支。
 */
public class ReplayProjectorTest {

    private final ReplayProjector replayProjector = new ReplayProjector(
            new ToolInvocationProjectorRegistry(
                    List.of(
                            new FileToolInvocationProjector(),
                            new DeepSearchToolInvocationProjector(),
                            new DefaultToolInvocationProjector()
                    ),
                    new DefaultToolInvocationProjector()
            )
    );

    @Test
    public void shouldProjectBundleByToolNameAndInvocationOrder() {
        ToolInvocationView fileInvocation = ToolInvocationView.builder()
                .id(1L)
                .toolCallId("tool-call-file-001")
                .toolName("file_tool")
                .outputJson("""
                        {"schemaVersion":1,"command":"get","fileInfo":[{"fileName":"report.md"}]}
                        """)
                .build();
        ToolInvocationView plainInvocation = ToolInvocationView.builder()
                .id(2L)
                .toolCallId("tool-call-plain-001")
                .toolName("read_tool")
                .outputJson("""
                        {"schemaVersion":1,"resultType":"plain_text","data":{"text":"hello"}}
                        """)
                .build();
        ArtifactView artifact = ArtifactView.builder()
                .toolInvocationId(1L)
                .toolCallId("tool-call-file-001")
                .fileName("report.md")
                .downloadUrl("https://file.example.com/report.md")
                .previewUrl("https://file.example.com/preview/report.md")
                .storageKey("artifact-report")
                .build();

        List<ProjectedReplayEvent> events = replayProjector.projectHistory(ReplayFactBundle.builder()
                .toolInvocations(List.of(fileInvocation, plainInvocation))
                .artifacts(List.of(artifact))
                .build());

        Assert.assertEquals(2, events.size());
        Assert.assertEquals("file", outerMessageType(events.get(0)));
        Assert.assertEquals("tool_result", outerMessageType(events.get(1)));
        Assert.assertNotEquals(events.get(0).getTaskId(), events.get(1).getTaskId());
        Assert.assertEquals("读取文件", nestedResultMap(events.get(0)).get("command"));
        Assert.assertEquals("hello", toolResult(events.get(1)).get("toolResult"));
    }

    @SuppressWarnings("unchecked")
    private String outerMessageType(ProjectedReplayEvent event) {
        return String.valueOf(((Map<String, Object>) event.getResultMap()).get("messageType"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedResultMap(ProjectedReplayEvent event) {
        return (Map<String, Object>) ((Map<String, Object>) event.getResultMap()).get("resultMap");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toolResult(ProjectedReplayEvent event) {
        return (Map<String, Object>) ((Map<String, Object>) event.getResultMap()).get("toolResult");
    }
}

package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.artifact.TaskSummaryArtifactProtocol;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactBinding;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.TaskSummaryResult;

import java.util.List;
import java.util.Map;

public class TaskSummaryArtifactProtocolTest {

    @Test
    public void shouldKeepRawFinalAnswerWithoutAttachingVisibleFiles() {
        ToolArtifactBinding binding = ToolArtifactBinding.builder()
                .source(ToolArtifactSource.builder()
                        .toolCallId("child-call-1")
                        .toolName("workspace_write")
                        .build())
                .file(org.wwz.ai.domain.agent.runtime.dto.File.builder()
                        .fileName("child-report.md")
                        .domainUrl("https://example.com/preview/child-report.md")
                        .ossUrl("https://example.com/download/child-report.md")
                        .isInternalFile(false)
                        .build())
                .build();

        TaskSummaryResult result = TaskSummaryArtifactProtocol.resolveForDelivery(
                "子 Agent 已完成",
                List.of(binding)
        );
        Map<String, Object> payload = TaskSummaryArtifactProtocol.toEventPayload(result);

        Assert.assertEquals("子 Agent 已完成", payload.get("taskSummary"));
        Assert.assertNull(payload.get("fileList"));
        Assert.assertNull(payload.get("artifactKeys"));
        Assert.assertTrue(result.getFiles() == null || result.getFiles().isEmpty());
    }

    @Test
    public void shouldKeepDelimiterAndKeysWithoutMatchingBindings() {
        ToolArtifactBinding report = ToolArtifactBinding.builder()
                .source(ToolArtifactSource.builder()
                        .toolCallId("child-call-1")
                        .toolName("workspace_write")
                        .build())
                .file(org.wwz.ai.domain.agent.runtime.dto.File.builder()
                        .fileName("child-report.md")
                        .domainUrl("https://example.com/preview/child-report.md")
                        .ossUrl("https://example.com/download/child-report.md")
                        .isInternalFile(false)
                        .build())
                .build();
        ToolArtifactBinding style = ToolArtifactBinding.builder()
                .source(ToolArtifactSource.builder()
                        .toolCallId("child-call-2")
                        .toolName("workspace_write")
                        .build())
                .file(org.wwz.ai.domain.agent.runtime.dto.File.builder()
                        .fileName("style.css")
                        .domainUrl("https://example.com/preview/style.css")
                        .ossUrl("https://example.com/download/style.css")
                        .isInternalFile(false)
                        .build())
                .build();

        TaskSummaryResult result = TaskSummaryArtifactProtocol.resolveForDelivery(
                "报告已完成。$$$ child-report.md",
                List.of(report, style)
        );
        Map<String, Object> payload = TaskSummaryArtifactProtocol.toEventPayload(
                result,
                List.of(report, style)
        );

        Assert.assertEquals("报告已完成。$$$ child-report.md", payload.get("taskSummary"));
        Assert.assertEquals(List.of("child-report.md"), payload.get("artifactKeys"));
        Assert.assertNull(payload.get("fileList"));
        Assert.assertTrue(result.getFiles() == null || result.getFiles().isEmpty());
    }

    @Test
    public void shouldExtractRelativePathKeysWithoutSelectingFiles() {
        TaskSummaryResult named = TaskSummaryArtifactProtocol.parse(
                "页面已生成。$$$ site/index.html、site/css/style.css"
        );
        Assert.assertEquals("页面已生成。$$$ site/index.html、site/css/style.css", named.getTaskSummary());
        Assert.assertEquals(List.of("site/index.html", "site/css/style.css"), named.getArtifactKeys());
        Assert.assertTrue(named.getFiles() == null || named.getFiles().isEmpty());
    }
}

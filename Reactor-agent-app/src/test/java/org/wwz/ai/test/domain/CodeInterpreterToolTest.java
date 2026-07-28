package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.runtime.tool.common.CodeInterpreterTool;

import java.util.List;

public class CodeInterpreterToolTest {

    @Test
    public void shouldAppendUploadedArtifactUrlToLlmObservation() {
        CodeInterpreterTool tool = new CodeInterpreterTool();
        String observation = ReflectionTestUtils.invokeMethod(
                tool,
                "appendArtifactUrls",
                "图表已生成",
                List.of(CodeInterpreterResponse.FileInfo.builder()
                        .fileName("chart.png")
                        .domainUrl("https://file.example.com/preview/chart.png")
                        .ossUrl("https://file.example.com/download/chart.png")
                        .build())
        );

        Assert.assertTrue(observation.contains("chart.png"));
        Assert.assertTrue(observation.contains("https://file.example.com/preview/chart.png"));
    }
}

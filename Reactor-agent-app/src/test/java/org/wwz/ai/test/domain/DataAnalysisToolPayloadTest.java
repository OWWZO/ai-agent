package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.dto.DataAnalysisRequest;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.DataAnalysisTool;

import java.util.List;
import java.util.Map;

/**
 * 数据分析工具的 LLM payload 与账本结构化输出边界测试。
 */
public class DataAnalysisToolPayloadTest {

    @Test
    public void shouldExposeAnalysisContentOnceAndKeepSummaryInStructuredOutput() {
        DataAnalysisTool tool = new DataAnalysisTool();
        DataAnalysisRequest request = DataAnalysisRequest.builder()
                .task("分析销售趋势")
                .build();
        String content = "分析结论：收入持续增长。";

        ToolResultPayload payload = ReflectionTestUtils.invokeMethod(
                tool,
                "buildSuccessPayload",
                request,
                content,
                List.of());

        Assert.assertNotNull(payload);
        Assert.assertTrue(payload.getLlmData() instanceof Map<?, ?>);
        Map<?, ?> llmData = (Map<?, ?>) payload.getLlmData();
        Assert.assertEquals(content, llmData.get("content"));
        Assert.assertFalse(llmData.containsKey("summary"));
        Assert.assertNotNull(payload.getStructuredOutput());
    }
}

package org.wwz.ai.domain.agent.runtime.tool.common;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class DeepSearchFileNameTest {

    @Test
    public void shouldUseReportNameAsSearchResultBaseName() {
        String fileName = DeepSearchFileNamePolicy.buildSearchResultFileName("机器人行业报告.md");

        Assert.assertEquals("机器人行业报告_search_result.txt", fileName);
        Assert.assertTrue(fileName.getBytes(StandardCharsets.UTF_8).length < 200);
    }

    @Test
    public void shouldKeepShortRequestedReportName() {
        Assert.assertEquals("机器人行业报告.md",
                DeepSearchFileNamePolicy.resolveReportFileName("机器人行业报告.md"));
    }

    @Test
    public void shouldAcceptReportNameUpToTwentyCharacters() {
        Assert.assertNull(DeepSearchFileNamePolicy.validateReportFileName("一一一一一一一一一一一一一一一一一.md"));
        Assert.assertNotNull(DeepSearchFileNamePolicy.validateReportFileName("一一一一一一一一一一一一一一一一一一.md"));
    }

    @Test
    public void shouldRejectBlankReportName() {
        Assert.assertNotNull(DeepSearchFileNamePolicy.validateReportFileName("  "));
    }

    @Test
    public void shouldUseReadableNameInsteadOfTruncatingLongReportName() {
        String longName = "截至2026年人形机器人技术路线、执行器、减速器、灵巧手和感知能力的完整证据链研究报告.md";
        String reportFileName = DeepSearchFileNamePolicy.resolveReportFileName(longName);

        Assert.assertNotNull(DeepSearchFileNamePolicy.validateReportFileName(longName));
        Assert.assertEquals("深度搜索报告.md", reportFileName);
        Assert.assertEquals("深度搜索报告_search_result.txt",
                DeepSearchFileNamePolicy.buildSearchResultFileName(reportFileName));
    }
}

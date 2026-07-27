package org.wwz.ai.test.domain.canvas;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.canvas.CanvasPublishArgSalvage;

import java.util.Map;

public class CanvasPublishArgSalvageTest {

    @Test
    public void salvageTruncatedHtmlArgs() {
        String raw = "{\"title\":\"Demo Page\",\"mode\":\"html\",\"html\":\"<html><body><h1>Hello";
        Map<String, Object> salvaged = CanvasPublishArgSalvage.parseOrSalvage(raw);
        Assert.assertNotNull(salvaged);
        Assert.assertEquals("Demo Page", salvaged.get("title"));
        Assert.assertEquals("html", salvaged.get("mode"));
        Assert.assertTrue(String.valueOf(salvaged.get("html")).contains("<h1>Hello"));
        Assert.assertEquals(Boolean.TRUE, salvaged.get("__salvaged"));
    }

    @Test
    public void parseValidJsonWithoutSalvageFlag() {
        String raw = "{\"title\":\"OK\",\"mode\":\"html\",\"html\":\"<p>hi</p>\"}";
        Map<String, Object> parsed = CanvasPublishArgSalvage.parseOrSalvage(raw);
        Assert.assertNotNull(parsed);
        Assert.assertEquals("OK", parsed.get("title"));
        Assert.assertEquals("<p>hi</p>", parsed.get("html"));
        Assert.assertNull(parsed.get("__salvaged"));
    }

    @Test
    public void salvageHtmlPath() {
        String raw = "{\"title\":\"From Disk\",\"mode\":\"html\",\"html_path\":\"pages/index.html\"";
        Map<String, Object> salvaged = CanvasPublishArgSalvage.salvage(raw);
        Assert.assertNotNull(salvaged);
        Assert.assertEquals("pages/index.html", salvaged.get("html_path"));
    }
}

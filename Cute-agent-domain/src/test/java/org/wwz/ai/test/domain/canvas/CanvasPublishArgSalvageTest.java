package org.wwz.ai.test.domain.canvas;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.canvas.CanvasPublishArgSalvage;

import java.util.Map;

public class CanvasPublishArgSalvageTest {

    @Test
    public void salvageTruncatedHtmlPathArgs() {
        String raw = "{\"title\":\"Demo Page\",\"html_path\":\"pages/index.html";
        Map<String, Object> salvaged = CanvasPublishArgSalvage.parseOrSalvage(raw);
        Assert.assertNotNull(salvaged);
        Assert.assertEquals("Demo Page", salvaged.get("title"));
        Assert.assertEquals("pages/index.html", salvaged.get("html_path"));
        Assert.assertEquals(Boolean.TRUE, salvaged.get("__salvaged"));
    }

    @Test
    public void parseValidJsonWithoutSalvageFlag() {
        String raw = "{\"title\":\"OK\",\"html_path\":\"index.html\"}";
        Map<String, Object> parsed = CanvasPublishArgSalvage.parseOrSalvage(raw);
        Assert.assertNotNull(parsed);
        Assert.assertEquals("OK", parsed.get("title"));
        Assert.assertEquals("index.html", parsed.get("html_path"));
        Assert.assertNull(parsed.get("__salvaged"));
    }

    @Test
    public void inlineHtmlCannotBeSalvaged() {
        Map<String, Object> salvaged = CanvasPublishArgSalvage.salvage(
                "{\"title\":\"Inline\",\"html\":\"<p>hi</p>\"}"
        );
        Assert.assertNull(salvaged);
    }
}

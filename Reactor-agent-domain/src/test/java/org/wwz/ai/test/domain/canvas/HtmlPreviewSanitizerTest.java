package org.wwz.ai.test.domain.canvas;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.HtmlPreviewSanitizer;

public class HtmlPreviewSanitizerTest {

    @Test
    public void stripsScriptAndHandlersWhenSanitizing() {
        String html = "<div onclick=\"alert(1)\"><script>alert(2)</script><a href=\"javascript:evil()\">x</a></div>";
        String out = HtmlPreviewSanitizer.sanitize(html);
        Assert.assertFalse(out.toLowerCase().contains("<script"));
        Assert.assertFalse(out.toLowerCase().contains("onclick"));
        Assert.assertFalse(out.toLowerCase().contains("javascript:"));
    }

    @Test
    public void injectsCspForFragmentWhenJsOff() {
        String out = HtmlPreviewSanitizer.ensurePreviewCsp("<main>hi</main>", false);
        Assert.assertTrue(out.toLowerCase().contains("content-security-policy"));
        Assert.assertTrue(out.contains("script-src 'none'"));
        Assert.assertTrue(out.contains("<main>hi</main>"));
    }

    @Test
    public void buildPreviewHtmlKeepsScriptsAndInjectsShell() {
        String fragment = "<main class=\"p-8\"><h1 class=\"text-2xl font-semibold\">Hello</h1>"
                + "<script>window.__x=1</script></main>";
        String out = HtmlPreviewSanitizer.buildPreviewHtml(fragment, true);
        Assert.assertTrue(out.contains("cdn.tailwindcss.com"));
        Assert.assertTrue(out.contains("window.__x=1"));
        Assert.assertTrue(out.contains("__reactorPreviewIframeBootstrap"));
        Assert.assertFalse(out.contains("script-src 'none'"));
    }

    @Test
    public void doesNotClobberAuthoredStyles() {
        String style = "body{background:#111;color:#fff;}".repeat(5);
        String html = "<!DOCTYPE html><html><head><style>" + style
                + "</style></head><body><h1>Dark</h1></body></html>";
        String out = HtmlPreviewSanitizer.buildPreviewHtml(html, true);
        Assert.assertFalse(out.contains("cdn.tailwindcss.com"));
        Assert.assertTrue(out.contains("Dark"));
    }

    @Test
    public void stripsScriptsWhenAllowJsFalse() {
        String html = "<!DOCTYPE html><html><body><script>alert(1)</script><p>ok</p></body></html>";
        String out = HtmlPreviewSanitizer.buildPreviewHtml(html, false);
        Assert.assertFalse(out.toLowerCase().contains("<script>alert"));
        Assert.assertTrue(out.contains("script-src 'none'"));
        Assert.assertTrue(out.contains("<p>ok</p>"));
    }

    @Test
    public void injectsThreeJsOnBareFragment() {
        String out = HtmlPreviewSanitizer.buildPreviewHtml("<div id=\"c\"></div>", true);
        Assert.assertTrue(out.contains("three@0.160.0"));
        Assert.assertTrue(out.contains("cdn.tailwindcss.com"));
    }

    @Test
    public void skipsThreeWhenDocumentAlreadyLoadsIt() {
        String html = "<!DOCTYPE html><html><head>"
                + "<script src=\"https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.min.js\"></script>"
                + "</head><body><div></div></body></html>";
        // bare shell (no authored style) → host injects tailwind but not second three
        String out = HtmlPreviewSanitizer.buildPreviewHtml(html, true);
        int count = 0;
        int idx = 0;
        while ((idx = out.indexOf("three@0.160.0", idx)) >= 0) {
            count++;
            idx += 10;
        }
        Assert.assertEquals(1, count);
    }
}

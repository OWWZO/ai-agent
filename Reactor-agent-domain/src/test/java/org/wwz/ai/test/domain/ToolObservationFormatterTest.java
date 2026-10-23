package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationFormatter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具结果投影契约：模型只看紧凑文本，UI / 账本仍拿完整 JSON。
 * 未注册、非 Map、ok=false、缺键、异常一律回落 null。
 */
public class ToolObservationFormatterTest {

    @Test
    public void unregisteredToolFallsBackToJson() {
        Assert.assertNull(ToolObservationFormatter.format("some_mcp_tool", Map.of("anything", 1)));
    }

    @Test
    public void nonMapPayloadFallsBackToJson() {
        Assert.assertNull(ToolObservationFormatter.format("workspace_list", "plain text"));
        Assert.assertNull(ToolObservationFormatter.format("workspace_list", null));
    }

    @Test
    public void softFailFallsBackToJson() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.FALSE);
        data.put("path", "src/a.ts");
        Assert.assertNull(ToolObservationFormatter.format("workspace_write", data));
    }

    @Test
    public void nullToolNameFallsBackToJson() {
        Assert.assertNull(ToolObservationFormatter.format(null, Map.of("path", "a.ts")));
    }

    @Test
    public void missingRequiredKeyFallsBackToJson() {
        Assert.assertNull(ToolObservationFormatter.format("workspace_write", Map.of("ok", true)));
        Assert.assertNull(ToolObservationFormatter.format("workspace_edit", Map.of("ok", true)));
    }

    @Test
    public void registeredToolReturnsCompactText() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("path", "src/a.ts");
        data.put("chars", 1200);
        data.put("message", "已写入文件");

        String projected = ToolObservationFormatter.format("workspace_write", data);

        Assert.assertEquals("File written successfully: src/a.ts", projected);
        Assert.assertFalse(projected.contains("\"chars\""));
        Assert.assertFalse(projected.contains("已写入文件"));
    }

    @Test
    public void grepUsesPathLineTextFormat() {
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("path", "src/Foo.java");
        hit.put("line", 7);
        hit.put("text", "    public void handle() {");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("matches", List.of(hit));

        String projected = ToolObservationFormatter.format("workspace_grep", data);

        Assert.assertEquals("src/Foo.java:7:    public void handle() {", projected);
    }

    @Test
    public void emptyGrepSaysNoMatches() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("matches", List.of());

        Assert.assertEquals("No matches found", ToolObservationFormatter.format("workspace_grep", data));
    }

    @Test
    public void readKeepsLineNumberPrefixAndDropsMetadata() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("type", "text");
        data.put("path", "src/Demo.java");
        data.put("startLine", 1);
        data.put("endLine", 2);
        data.put("numLines", 2);
        data.put("totalLines", 2);
        data.put("content", "1 | class Demo {\n2 | }");

        String projected = ToolObservationFormatter.format("workspace_read", data);

        Assert.assertTrue(projected.startsWith("1 | class Demo {"));
        Assert.assertFalse(projected.contains("\"numLines\""));
        Assert.assertFalse(projected.contains("\"totalLines\""));
        // 全文已读完，不再追加行数提示。
        Assert.assertFalse(projected.contains("file has"));
    }

    @Test
    public void readAppendsLineCountWhenNotFullyRead() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("type", "text");
        data.put("startLine", 1);
        data.put("endLine", 10);
        data.put("totalLines", 120);
        data.put("content", "1 | a");

        String projected = ToolObservationFormatter.format("workspace_read", data);

        Assert.assertTrue(projected.contains("[file has 120 lines; shown 1-10]"));
    }

    @Test
    public void readUnchangedStubIsPlainText() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("type", "file_unchanged");
        data.put("message", "File unchanged since last read.");

        Assert.assertEquals("File unchanged since last read.",
                ToolObservationFormatter.format("workspace_read", data));
    }

    @Test
    public void readImageFallsBackToJson() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("type", "image");
        data.put("path", "a.png");

        Assert.assertNull(ToolObservationFormatter.format("workspace_read", data));
    }

    @Test
    public void listDropsBytesAndKeepsTypePrefix() {
        Map<String, Object> dir = new LinkedHashMap<>();
        dir.put("type", "DIR");
        dir.put("path", "src");
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("type", "FILE");
        file.put("path", "src/App.tsx");
        file.put("bytes", 1201);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("entries", List.of(dir, file));

        String projected = ToolObservationFormatter.format("workspace_list", data);

        Assert.assertEquals("DIR  src\nFILE src/App.tsx", projected);
        Assert.assertFalse(projected.contains("bytes"));
    }

    @Test
    public void editKeepsDiffAndDropsCharCounts() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("path", "src/Main.java");
        data.put("replacements", 1);
        data.put("charsBefore", 100);
        data.put("charsAfter", 101);
        data.put("diff", "--- a/src/Main.java\n+++ b/src/Main.java\n@@ -1 +1 @@\n-a\n+b");

        String projected = ToolObservationFormatter.format("workspace_edit", data);

        Assert.assertTrue(projected.startsWith(
                "The file src/Main.java has been updated successfully. (1 replacement)"));
        Assert.assertTrue(projected.contains("@@ -1 +1 @@"));
        Assert.assertFalse(projected.contains("charsBefore"));
        Assert.assertFalse(projected.contains("charsAfter"));
    }

    @Test
    public void taskOutputDropsDuplicateResultKey() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("message", "任务已完成。");
        data.put("output", "done");

        String projected = ToolObservationFormatter.format("TaskOutput", data);

        Assert.assertTrue(projected.contains("done"));
        Assert.assertEquals(projected.indexOf("done"), projected.lastIndexOf("done"));
    }

    @Test
    public void cliOmitsDefaultFlags() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("stdout", "hello\n");
        data.put("stderr", "");
        data.put("exit_code", 0);
        data.put("timed_out", false);
        data.put("truncated", false);
        data.put("duration_ms", 42);

        String projected = ToolObservationFormatter.format("host_cli", data);

        Assert.assertEquals("hello", projected);
        Assert.assertFalse(projected.contains("exit_code"));
        Assert.assertFalse(projected.contains("duration_ms"));
    }

    @Test
    public void cliReportsNonDefaultFlags() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("stdout", "");
        data.put("stderr", "boom");
        data.put("exit_code", 1);
        data.put("timed_out", true);

        String projected = ToolObservationFormatter.format("browser", data);

        Assert.assertTrue(projected.contains("stderr:\nboom"));
        Assert.assertTrue(projected.contains("exit_code: 1"));
        Assert.assertTrue(projected.contains("[timed out]"));
    }

    @Test
    public void taskOutputKeepsRetrievalStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("message", "任务仍在运行。");
        data.put("retrieval_status", "not_ready");

        String projected = ToolObservationFormatter.format("TaskOutput", data);

        // 模型靠 retrieval_status 区分「还在跑」和「已完成但没输出」；丢掉会误判后台任务已终态。
        Assert.assertTrue(projected.contains("retrieval_status: not_ready"));
    }

    @Test
    public void cliKeepsArtifactPaths() {
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("relativePath", "screenshot-1730000000.png");
        artifact.put("previewUrl", "https://example.com/p");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("stdout", "");
        data.put("artifacts", List.of(artifact));

        String projected = ToolObservationFormatter.format("browser", data);

        // browser 的工具描述让模型用 workspace_read 读相对路径，吞掉它就看不了截的图。
        Assert.assertTrue(projected.contains("screenshot-1730000000.png"));
    }

    @Test
    public void cliKeepsRebuiltSessionNotice() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("stdout", "opened");
        data.put("notice", "Agent 浏览器已重建，之前的页面状态已丢失。");

        Assert.assertTrue(ToolObservationFormatter.format("agent_browser", data)
                .contains("Agent 浏览器已重建"));
    }
}

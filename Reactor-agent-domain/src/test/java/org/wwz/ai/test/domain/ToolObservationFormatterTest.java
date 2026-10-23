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

    // ---- 新增覆盖：检索类 ----

    @Test
    public void webSearchDropsEchoAndTruncatesSnippet() {
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("title", "Title A");
        hit.put("url", "https://a.example");
        hit.put("snippet", "s".repeat(1000));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", "web_search");
        data.put("ok", Boolean.TRUE);
        data.put("query", "模型刚发过的查询");
        data.put("provider", "exa");
        data.put("durationSec", 3.49);
        data.put("reminder", "Cite sources with markdown hyperlinks in the final answer.");
        data.put("hits", List.of(hit));

        String projected = ToolObservationFormatter.format("WebSearch", data);

        Assert.assertTrue(projected.contains("https://a.example"));
        // query / provider / durationSec 是入参回显与计时，不进上下文。
        Assert.assertFalse(projected.contains("模型刚发过的查询"));
        Assert.assertFalse(projected.contains("durationSec"));
        Assert.assertFalse(projected.contains("provider"));
        // snippet 保留但截断，引用要求折成一行页脚。
        Assert.assertTrue(projected.contains("…"));
        Assert.assertEquals(1, countOccurrences(projected, "Cite sources"));
    }

    @Test
    public void webSearchEmptyHitsSaysNoResults() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("hits", List.of());

        Assert.assertTrue(ToolObservationFormatter.format("WebSearch", data).contains("No results found"));
    }

    @Test
    public void webFetchKeepsContentAndDropsRequestEcho() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("url", "https://a.example");
        data.put("status", 200);
        data.put("prompt", "抽取关键结论");
        data.put("bytes", 4721);
        data.put("durationMs", 15576);
        data.put("contentFormat", "markdown");
        data.put("wordCount", 642);
        data.put("content", "页面正文");

        String projected = ToolObservationFormatter.format("WebFetch", data);

        Assert.assertEquals("页面正文", projected);
        Assert.assertFalse(projected.contains("抽取关键结论"));
        Assert.assertFalse(projected.contains("wordCount"));
    }

    @Test
    public void webFetchReportsNonSuccessStatusOnly() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("status", 404);
        data.put("content", "not found body");

        String projected = ToolObservationFormatter.format("WebFetch", data);

        Assert.assertTrue(projected.contains("status: 404"));
    }

    @Test
    public void deepSearchAcceptsPojoPayload() {
        // deep_search 传的是 POJO 而非 Map：入口必须归一化，否则投影整个失效。
        Map<String, Object> chapter = new LinkedHashMap<>();
        chapter.put("title", "第一章");
        chapter.put("summary", "章节结论");
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("title", "Doc A");
        doc.put("url", "https://doc.example");
        Map<String, Object> queryResult = new LinkedHashMap<>();
        queryResult.put("query", "子查询");
        queryResult.put("docs", List.of(doc));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", "deep_search");
        data.put("query", "原始查询回显");
        data.put("answerSummary", "总结正文");
        data.put("chapters", List.of(chapter));
        data.put("results", List.of(queryResult));

        String projected = ToolObservationFormatter.format("deep_search", data);

        Assert.assertTrue(projected.startsWith("总结正文"));
        Assert.assertTrue(projected.contains("第一章"));
        Assert.assertTrue(projected.contains("https://doc.example"));
        Assert.assertFalse(projected.contains("原始查询回显"));
    }

    // ---- 新增覆盖：子 Agent ----

    @Test
    public void agentKeepsAgentIdWithActionableHint() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("status", "completed");
        data.put("agentId", "b968e75b");
        data.put("content", "done");
        data.put("agentType", "code-executor");
        data.put("totalToolUseCount", 3);
        data.put("childSessionId", "sub_b968e75b");

        String projected = ToolObservationFormatter.format("Agent", data);

        Assert.assertTrue(projected.startsWith("done"));
        // agentId 是续跑入口，必须带可执行的 SendMessage 提示。
        Assert.assertTrue(projected.contains("agentId: b968e75b"));
        Assert.assertTrue(projected.contains("SendMessage with to: 'b968e75b'"));
        Assert.assertFalse(projected.contains("sub_b968e75b"));
    }

    @Test
    public void agentWithoutContentSaysSo() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("status", "completed");
        data.put("agentId", "a1");
        data.put("content", "");

        Assert.assertTrue(ToolObservationFormatter.format("Agent", data)
                .contains("Subagent completed but returned no output"));
    }

    // ---- 新增覆盖：Plan Mode ----

    @Test
    public void enterPlanModeDropsDuplicateInstructions() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("entered", Boolean.TRUE);
        data.put("message", "Entered plan mode.");
        data.put("planFilePath", ".reactor/plan.md");
        data.put("instructions", "PLAN_MODE_INSTRUCTIONS_V2".repeat(100));

        String projected = ToolObservationFormatter.format("EnterPlanMode", data);

        // instructions 已注入 system prompt，工具结果再带一份就是双份全文。
        Assert.assertFalse(projected.contains("PLAN_MODE_INSTRUCTIONS_V2"));
        Assert.assertTrue(projected.contains(".reactor/plan.md"));
    }

    @Test
    public void exitPlanModeDropsDuplicatePlanBody() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("approved", Boolean.TRUE);
        data.put("message", "User has approved your plan.\n\n## Approved Plan:\n\n步骤一");
        data.put("plan", "步骤一");

        String projected = ToolObservationFormatter.format("ExitPlanMode", data);

        Assert.assertEquals(1, countOccurrences(projected, "步骤一"));
    }

    // ---- 新增覆盖：bash / code_execution ----

    @Test
    public void bashDropsSkillInventory() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("stdout", "ok\n");
        data.put("stderr", "");
        data.put("exit_code", 0);
        data.put("cwd", ".");
        data.put("duration_ms", 9462);
        data.put("skills_materialized", List.of("a", "b", "c"));
        data.put("skills_synced_back", List.of());

        String projected = ToolObservationFormatter.format("bash", data);

        Assert.assertEquals("ok", projected);
        Assert.assertFalse(projected.contains("skills_materialized"));
        Assert.assertFalse(projected.contains("duration_ms"));
        Assert.assertFalse(projected.contains("cwd"));
    }

    @Test
    public void codeExecutionSurfacesErrorAndFiles() {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("file_name", "out.csv");
        file.put("url", "https://files.example/out.csv");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("status", "ok");
        data.put("stdout", "rows: 10");
        data.put("stderr", "");
        data.put("hint", "Use produced_files.url as image.url");
        data.put("produced_files", List.of(file));

        String projected = ToolObservationFormatter.format("code_execution", data);

        Assert.assertTrue(projected.contains("rows: 10"));
        Assert.assertTrue(projected.contains("out.csv"));
        Assert.assertFalse(projected.contains("produced_files.url as image.url"));
    }

    // ---- 新增覆盖：任务 / 画布 / 检索元工具 ----

    @Test
    public void taskListRendersCompactRows() {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", "1");
        task.put("status", "pending");
        task.put("subject", "写计划");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("tasks", List.of(task));

        Assert.assertEquals("- [1] pending 写计划", ToolObservationFormatter.format("TaskList", data));
    }

    @Test
    public void emitUiTreeDoesNotEchoSubmittedTree() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("message", "GenUI tree emitted successfully.");
        data.put("canvasId", "c1");
        data.put("tree", Map.of("root", Map.of("kind", "Card")));

        String projected = ToolObservationFormatter.format("emit_ui_tree", data);

        Assert.assertFalse(projected.contains("Card"));
        Assert.assertTrue(projected.contains("c1"));
    }

    @Test
    public void toolSearchKeepsMatchesAndHint() {
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("name", "mcp__maps__text_search");
        match.put("description", "按关键词搜索地点");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", Boolean.TRUE);
        data.put("matches", List.of(match));
        data.put("hint", "Retry with the service name.");

        String projected = ToolObservationFormatter.format("ToolSearch", data);

        Assert.assertTrue(projected.contains("mcp__maps__text_search"));
        Assert.assertTrue(projected.contains("Retry with the service name."));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}

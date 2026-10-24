package org.wwz.ai.test.domain;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeRequest;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeResult;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePathGuard;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.infrastructure.codemode.CodeModeHeapGuard;
import org.wwz.ai.infrastructure.codemode.GraalJsCodeModeExecutor;
import org.wwz.ai.types.agent.config.CodeModeProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * GraalJS Code Mode 执行器：入口约定、Promise、Context 权限、workspace 编排、
 * workspaceFile 一键执行、超时与取消、CSV 合并。
 */
public class GraalJsCodeModeExecutorTest {

    private Path workspace;
    private AgentContext agentContext;
    private GraalJsCodeModeExecutor executor;

    @Before
    public void setUp() throws IOException {
        CodeModeHeapGuard.shared().reset();
        workspace = Files.createTempDirectory("codemode-exec-");
        agentContext = AgentContext.builder()
                .requestId("req-exec")
                .sessionId("sess-exec")
                .userId("user-exec")
                .workspaceRoot(workspace.toAbsolutePath().normalize().toString())
                .build();
        executor = executor(new CodeModeProperties());
    }

    @After
    public void tearDown() throws IOException {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(workspace)) {
            for (Path path : new ArrayList<>(walk.sorted(Comparator.reverseOrder()).toList())) {
                Files.deleteIfExists(path);
            }
        }
    }

    private GraalJsCodeModeExecutor executor(CodeModeProperties properties) {
        properties.setEnabled(true);
        return new GraalJsCodeModeExecutor(properties,
                new WorkspaceService(null, new WorkspacePathGuard(), null, null));
    }

    private CodeModeResult run(String source) {
        return executor.execute(CodeModeRequest.builder()
                .source(source)
                .inputs(Map.of())
                .timeoutMs(30_000L)
                .build(), agentContext);
    }

    private void assertFailure(CodeModeResult result, String expectedCode) {
        Assert.assertFalse(String.valueOf(result.getErrorMessage()), result.isSuccess());
        Assert.assertEquals(expectedCode, result.getErrorCode());
    }

    @Test
    public void runsSyncAndAsyncMain() {
        CodeModeResult sync = run("async function main(){ return { sum: 1 + 1 }; }");
        Assert.assertTrue(String.valueOf(sync.getErrorMessage()), sync.isSuccess());
        Assert.assertEquals(2L, ((Number) ((Map<?, ?>) sync.getResult()).get("sum")).longValue());

        CodeModeResult awaited = run("""
                async function main() {
                  const value = await Promise.resolve(41);
                  return { value: value * 2 };
                }
                """);
        Assert.assertTrue(String.valueOf(awaited.getErrorMessage()), awaited.isSuccess());
        Assert.assertEquals(82L, ((Number) ((Map<?, ?>) awaited.getResult()).get("value")).longValue());
    }

    @Test
    public void receivesInputsObject() {
        CodeModeResult result = executor.execute(CodeModeRequest.builder()
                .source("async function main({ input }) { return { keyword: input.keyword, n: input.n }; }")
                .inputs(Map.of("keyword", "手机", "n", 3))
                .timeoutMs(30_000L)
                .build(), agentContext);
        Assert.assertTrue(result.isSuccess());
        Map<?, ?> value = (Map<?, ?>) result.getResult();
        Assert.assertEquals("手机", value.get("keyword"));
        Assert.assertEquals(3L, ((Number) value.get("n")).longValue());
    }

    @Test
    public void missingMainIsInvalidSource() {
        assertFailure(run("const x = 1;"), "CODE_MODE_INVALID_SOURCE");
    }

    @Test
    public void syntaxErrorIsInvalidSource() {
        assertFailure(run("async function main( { return 1; }"), "CODE_MODE_INVALID_SOURCE");
    }

    @Test
    public void runtimeErrorIsReported() {
        CodeModeResult result = run("async function main(){ throw new Error('boom'); }");
        assertFailure(result, "CODE_MODE_RUNTIME_ERROR");
        Assert.assertTrue(String.valueOf(result.getErrorMessage()).contains("boom"));
    }

    @Test
    public void contextOnlyExposesBrowserHostWorkspaceAndInput() {
        CodeModeResult result = run("""
                async function main(context) {
                  return {
                    keys: Object.keys(context).sort(),
                    agentBrowser: typeof context.agentBrowser,
                    hasBrowser: typeof context.browser.command,
                    hasHost: typeof context.host.exec,
                    hasWorkspace: typeof context.workspace.readCsv
                  };
                }
                """);
        Assert.assertTrue(String.valueOf(result.getErrorMessage()), result.isSuccess());
        Map<?, ?> value = (Map<?, ?>) result.getResult();
        Assert.assertEquals(List.of("browser", "host", "input", "workspace"), value.get("keys"));
        Assert.assertEquals("undefined", value.get("agentBrowser"));
        Assert.assertEquals("function", value.get("hasBrowser"));
        Assert.assertEquals("function", value.get("hasHost"));
        Assert.assertEquals("function", value.get("hasWorkspace"));
    }

    @Test
    public void hostEscapesAreUnusable() {
        CodeModeResult result = run("""
                async function main() {
                  const probe = (fn) => {
                    try {
                      const v = fn();
                      return v === undefined || v === null ? "blocked" : "allowed:" + String(v);
                    } catch (e) {
                      return "blocked";
                    }
                  };
                  return {
                    javaType: probe(() => Java.type('java.lang.System').getProperty('java.version')),
                    packages: probe(() => Packages.java.lang.System.getProperty('java.version')),
                    requireFn: probe(() => require('fs').readFileSync('/etc/passwd')),
                    processObj: probe(() => process.env.PATH),
                    polyglot: probe(() => Polyglot.import('anything')),
                    fileIo: probe(() => new java.io.File('/tmp').getAbsolutePath())
                  };
                }
                """);
        Assert.assertTrue(String.valueOf(result.getErrorMessage()), result.isSuccess());
        Map<?, ?> value = (Map<?, ?>) result.getResult();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            Assert.assertEquals(entry.getKey() + "=" + entry.getValue(), "blocked", entry.getValue());
        }
    }

    @Test
    public void javaClassLookupIsBlocked() {
        CodeModeResult result = run("""
                async function main() {
                  try {
                    Java.type('java.lang.System');
                    return { blocked: false };
                  } catch (e) {
                    return { blocked: true };
                  }
                }
                """);
        Assert.assertTrue(String.valueOf(result.getErrorMessage()), result.isSuccess());
        Assert.assertEquals(Boolean.TRUE, ((Map<?, ?>) result.getResult()).get("blocked"));
    }

    @Test
    public void writesAndReadsWorkspaceFiles() {
        CodeModeResult result = run("""
                async function main({ workspace }) {
                  const written = await workspace.writeText({
                    path: "outputs/hello.txt",
                    content: "hello code mode"
                  });
                  const read = await workspace.readText({ path: "outputs/hello.txt" });
                  return { content: read.content, version: written.version };
                }
                """);
        Assert.assertTrue(String.valueOf(result.getErrorMessage()), result.isSuccess());
        Map<?, ?> value = (Map<?, ?>) result.getResult();
        Assert.assertEquals("hello code mode", value.get("content"));
        Assert.assertTrue(String.valueOf(value.get("version")).startsWith("sha256:"));
        Assert.assertTrue(result.getProducedFiles().contains("outputs/hello.txt"));
    }

    @Test
    public void mergesTwoCsvFilesInsideJavaScript() {
        writeRaw("input/first.csv", "name,price\r\nA,1\r\n");
        writeRaw("input/second.csv", "name,price,extra\r\nB,2,x\r\n");
        CodeModeResult result = run("""
                async function main({ workspace }) {
                  const first = await workspace.readCsv({ path: "input/first.csv" });
                  const second = await workspace.readCsv({ path: "input/second.csv" });
                  const columns = [...new Set([...first.columns, ...second.columns])];
                  const rows = [...first.rows, ...second.rows].map(row => {
                    const normalized = {};
                    for (const column of columns) {
                      normalized[column] = row[column] ?? "";
                    }
                    return normalized;
                  });
                  const output = await workspace.writeCsv({
                    path: "outputs/merged.csv",
                    columns,
                    rows
                  });
                  return { rowCount: rows.length, outputPath: output.path };
                }
                """);
        Assert.assertTrue(String.valueOf(result.getErrorMessage()), result.isSuccess());
        Map<?, ?> value = (Map<?, ?>) result.getResult();
        Assert.assertEquals(2L, ((Number) value.get("rowCount")).longValue());
        Assert.assertEquals("outputs/merged.csv", value.get("outputPath"));
        Assert.assertTrue(Files.isRegularFile(workspace.resolve("outputs/merged.csv")));
    }

    @Test
    public void executesWorkspaceScriptByFile() {
        writeRaw("scripts/echo.js", """
                async function main({ workspace, input }) {
                  return await workspace.writeText({
                    path: "outputs/echo.txt",
                    content: "echo:" + input.keyword
                  });
                }
                """);
        CodeModeResult result = executor.execute(CodeModeRequest.builder()
                .workspaceFile("scripts/echo.js")
                .inputs(Map.of("keyword", "手机"))
                .timeoutMs(30_000L)
                .build(), agentContext);
        Assert.assertTrue(String.valueOf(result.getErrorMessage()), result.isSuccess());
        Assert.assertTrue(result.getSourceHash().startsWith("sha256:"));
        Assert.assertEquals("echo:手机", readRaw("outputs/echo.txt"));
    }

    @Test
    public void rejectsWorkspaceFileOutsideScriptDirectories() {
        writeRaw("outputs/not-a-script.js", "async function main(){ return 1; }");
        CodeModeResult result = executor.execute(CodeModeRequest.builder()
                .workspaceFile("outputs/not-a-script.js")
                .timeoutMs(30_000L)
                .build(), agentContext);
        assertFailure(result, "CODE_MODE_WORKSPACE_FILE_DENIED");
    }

    @Test
    public void reportsMissingWorkspaceScript() {
        CodeModeResult result = executor.execute(CodeModeRequest.builder()
                .workspaceFile("scripts/missing.js")
                .timeoutMs(30_000L)
                .build(), agentContext);
        assertFailure(result, "CODE_MODE_WORKSPACE_FILE_NOT_FOUND");
    }

    @Test
    public void rejectsHostCliOutsideWhitelist() {
        CodeModeResult result = run("""
                async function main({ host }) {
                  return await host.exec({ tool: "curl", args: ["http://example.com"] });
                }
                """);
        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals("CODE_MODE_CAPABILITY_DENIED", result.getErrorCode());
    }

    @Test
    public void reportsBrowserUnavailableAsStructuredError() {
        CodeModeResult result = run("""
                async function main({ browser }) {
                  return await browser.command({ args: ["jd", "search", "手机"] });
                }
                """);
        // 运行时未装配时 browser 调用不抛异常，而是回一个结构化失败，让脚本自己决定怎么继续
        Assert.assertTrue(String.valueOf(result.getErrorMessage()), result.isSuccess());
        Map<?, ?> value = (Map<?, ?>) result.getResult();
        Assert.assertEquals(Boolean.FALSE, value.get("ok"));
        Assert.assertTrue(String.valueOf(value.get("error")).contains("runtime_unavailable"));
    }

    @Test
    public void timesOutOnInfiniteLoop() {
        CodeModeProperties properties = new CodeModeProperties();
        properties.setMaxTimeoutMs(5_000L);
        GraalJsCodeModeExecutor timeoutExecutor = executor(properties);
        long started = System.currentTimeMillis();
        CodeModeResult result = timeoutExecutor.execute(CodeModeRequest.builder()
                .source("async function main(){ while (true) {} }")
                .timeoutMs(1_500L)
                .build(), agentContext);
        long elapsed = System.currentTimeMillis() - started;
        assertFailure(result, "CODE_MODE_TIMEOUT");
        Assert.assertTrue("elapsed=" + elapsed, elapsed < 20_000L);
    }

    @Test
    public void reportsCancelledWhenRunAlreadyStopped() {
        org.wwz.ai.domain.agent.runtime.cancel.RunCancellation cancellation =
                new org.wwz.ai.domain.agent.runtime.cancel.RunCancellation();
        cancellation.cancel(org.wwz.ai.domain.agent.runtime.cancel.RunCancellation.REASON_USER_STOP);
        AgentContext cancelled = AgentContext.builder()
                .requestId("req-cancel")
                .sessionId("sess-cancel")
                .userId("user-exec")
                .workspaceRoot(workspace.toAbsolutePath().normalize().toString())
                .runCancellation(cancellation)
                .build();
        CodeModeResult result = executor.execute(CodeModeRequest.builder()
                .source("async function main(){ return 1; }")
                .timeoutMs(30_000L)
                .build(), cancelled);
        assertFailure(result, "CODE_MODE_CANCELLED");
    }

    @Test
    public void rejectsOversizedOutput() {
        CodeModeProperties properties = new CodeModeProperties();
        properties.setMaxOutputChars(64);
        GraalJsCodeModeExecutor small = executor(properties);
        CodeModeResult result = small.execute(CodeModeRequest.builder()
                .source("async function main(){ return { text: 'x'.repeat(500) }; }")
                .timeoutMs(30_000L)
                .build(), agentContext);
        assertFailure(result, "CODE_MODE_OUTPUT_TOO_LARGE");
    }

    @Test
    public void enforcesToolCallLimit() {
        CodeModeProperties properties = new CodeModeProperties();
        properties.setMaxToolCalls(2);
        GraalJsCodeModeExecutor limited = executor(properties);
        CodeModeResult result = limited.execute(CodeModeRequest.builder()
                .source("""
                        async function main({ workspace }) {
                          for (let i = 0; i < 5; i++) {
                            await workspace.writeText({ path: "outputs/n" + i + ".txt", content: "x" });
                          }
                          return { done: true };
                        }
                        """)
                .timeoutMs(30_000L)
                .build(), agentContext);
        assertFailure(result, "CODE_MODE_TOOL_CALL_LIMIT");
    }

    @Test
    public void collectsConsoleLogs() {
        CodeModeResult result = run("""
                async function main() {
                  console.log("step-1", 42);
                  console.warn("careful");
                  return { ok: true };
                }
                """);
        Assert.assertTrue(String.valueOf(result.getErrorMessage()), result.isSuccess());
        Assert.assertEquals(2, result.getLogs().size());
        Assert.assertTrue(result.getLogs().get(0).contains("step-1"));
    }

    @Test
    public void tripsOnHeapWatermarkAndRejectsFollowUpExecutions() {
        CodeModeProperties properties = new CodeModeProperties();
        // 阈值压到接近 0，任何分配都会触发熔断；冷却期拉长到测试时长之外
        properties.setMaxHeapUsedRatio(0.01D);
        properties.setHeapCheckIntervalMs(10L);
        properties.setHeapTripCooldownMs(60_000L);
        properties.setMaxTimeoutMs(20_000L);
        GraalJsCodeModeExecutor guarded = executor(properties);

        CodeModeResult tripped = guarded.execute(CodeModeRequest.builder()
                .source("async function main(){ const x = []; for (let i = 0; i < 5e6; i++) x.push(i); return x.length; }")
                .timeoutMs(15_000L)
                .build(), agentContext);
        assertFailure(tripped, "CODE_MODE_MEMORY_LIMIT");

        CodeModeResult rejected = guarded.execute(CodeModeRequest.builder()
                .source("async function main(){ return 1; }")
                .timeoutMs(5_000L)
                .build(), agentContext);
        assertFailure(rejected, "CODE_MODE_MEMORY_LIMIT");
        Assert.assertTrue(String.valueOf(rejected.getErrorMessage()),
                rejected.getErrorMessage().contains("冷却"));
    }

    @Test
    public void reportsHeapExhaustionAsMemoryLimit() {
        CodeModeProperties properties = new CodeModeProperties();
        // 关闭水位熔断，让脚本直接把堆打穿，验证 OOM 也被归类为内存上限
        properties.setMaxHeapUsedRatio(0D);
        properties.setMaxTimeoutMs(120_000L);
        GraalJsCodeModeExecutor unguarded = executor(properties);

        // 按当前最大堆动态估算需要分配的量，避免测试 JVM 堆大时打不穿
        long heapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long outer = Math.max(600L, heapMb * 2);
        CodeModeResult result = unguarded.execute(CodeModeRequest.builder()
                .source("async function main(){ const x = []; for (let i = 0; i < " + outer
                        + "; i++) x.push(new Array(1e6).fill(i)); return x.length; }")
                .timeoutMs(90_000L)
                .build(), agentContext);
        assertFailure(result, "CODE_MODE_MEMORY_LIMIT");
    }

    private void writeRaw(String relative, String content) {
        Path file = workspace.resolve(relative);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private String readRaw(String relative) {
        try {
            return Files.readString(workspace.resolve(relative), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}

package org.wwz.ai.test.domain;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePathGuard;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.infrastructure.codemode.CodeModeExecutionBudget;
import org.wwz.ai.infrastructure.codemode.CodeModeExecutionException;
import org.wwz.ai.infrastructure.codemode.WorkspaceCodeCapability;
import org.wwz.ai.types.agent.config.CodeModeProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Workspace capability：路径安全、任意字节读写、CSV 合并/覆盖、版本冲突、原子写入。
 */
public class CodeModeWorkspaceCapabilityTest {

    private Path workspace;
    private WorkspaceCodeCapability capability;

    @Before
    public void setUp() throws IOException {
        workspace = Files.createTempDirectory("codemode-ws-");
        AgentContext agentContext = AgentContext.builder()
                .requestId("req-1")
                .sessionId("sess-1")
                .userId("user-1")
                .workspaceRoot(workspace.toAbsolutePath().normalize().toString())
                .build();
        WorkspaceService workspaceService = new WorkspaceService(null, new WorkspacePathGuard(), null, null);
        capability = new WorkspaceCodeCapability(
                agentContext,
                new CodeModeExecutionBudget(30_000L, 100, 64L * 1024 * 1024),
                workspaceService,
                new CodeModeProperties().getWorkspace());
    }

    @After
    public void tearDown() throws IOException {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(workspace)) {
            List<Path> paths = new ArrayList<>(walk.sorted(Comparator.reverseOrder()).toList());
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void writesAndReadsTextWithVersion() {
        Map<String, Object> written = (Map<String, Object>) capability.writeText(Map.of(
                "path", "outputs/note.txt",
                "content", "hello 世界"));
        Assert.assertEquals("outputs/note.txt", written.get("path"));
        Assert.assertTrue(String.valueOf(written.get("version")).startsWith("sha256:"));

        Map<String, Object> read = (Map<String, Object>) capability.readText(Map.of("path", "outputs/note.txt"));
        Assert.assertEquals("hello 世界", read.get("content"));
        Assert.assertEquals(written.get("version"), read.get("version"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void writesAndReadsArbitraryBinaryBytes() {
        byte[] payload = new byte[256];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        String base64 = java.util.Base64.getEncoder().encodeToString(payload);

        capability.writeBytes(Map.of("path", "outputs/blob.bin", "base64", base64));
        Map<String, Object> read = (Map<String, Object>) capability.readBytes(Map.of("path", "outputs/blob.bin"));
        Assert.assertEquals(256, ((Number) read.get("bytes")).intValue());
        Assert.assertEquals(base64, read.get("base64"));

        byte[] onDisk;
        try {
            onDisk = Files.readAllBytes(workspace.resolve("outputs/blob.bin"));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        Assert.assertArrayEquals(payload, onDisk);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void mergesTwoCsvFilesIntoNewCsv() {
        writeRaw("input/first.csv", "name,price\r\nA,1\r\n");
        writeRaw("input/second.csv", "name,price,extra\r\nB,2,x\r\n");

        Map<String, Object> first = (Map<String, Object>) capability.readCsv(Map.of("path", "input/first.csv"));
        Map<String, Object> second = (Map<String, Object>) capability.readCsv(Map.of("path", "input/second.csv"));

        List<String> columns = new ArrayList<>((List<String>) first.get("columns"));
        for (String column : (List<String>) second.get("columns")) {
            if (!columns.contains(column)) {
                columns.add(column);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> source : List.of(first, second)) {
            for (Map<String, String> row : (List<Map<String, String>>) source.get("rows")) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (String column : columns) {
                    normalized.put(column, row.getOrDefault(column, ""));
                }
                rows.add(normalized);
            }
        }
        capability.writeCsv(Map.of("path", "outputs/merged.csv", "columns", columns, "rows", rows));

        Map<String, Object> merged = (Map<String, Object>) capability.readCsv(Map.of("path", "outputs/merged.csv"));
        Assert.assertEquals(2, ((Number) merged.get("rowCount")).intValue());
        Assert.assertEquals(columns, merged.get("columns"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void overwritesCsvOnlyWhenVersionMatches() {
        writeRaw("data/products.csv", "name,price\r\nA,1\r\n");
        Map<String, Object> source = (Map<String, Object>) capability.readCsv(Map.of("path", "data/products.csv"));
        String version = String.valueOf(source.get("version"));

        Map<String, Object> updated = (Map<String, Object>) capability.writeCsv(Map.of(
                "path", "data/products.csv",
                "columns", List.of("name", "price", "status"),
                "rows", List.of(Map.of("name", "A", "price", 1, "status", "processed")),
                "expectedVersion", version));
        Assert.assertTrue(String.valueOf(updated.get("version")).startsWith("sha256:"));

        // 文件已被本次写入改过，旧 version 必须触发冲突
        try {
            capability.writeCsv(Map.of(
                    "path", "data/products.csv",
                    "columns", List.of("name"),
                    "rows", List.of(Map.of("name", "B")),
                    "expectedVersion", version));
            Assert.fail("expected CODE_MODE_FILE_CONFLICT");
        } catch (CodeModeExecutionException e) {
            Assert.assertEquals("CODE_MODE_FILE_CONFLICT", e.getErrorCode().code());
        }

        // 冲突失败后旧内容必须完好
        String content;
        try {
            content = Files.readString(workspace.resolve("data/products.csv"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        Assert.assertTrue(content.contains("processed"));
        Assert.assertFalse(content.contains("B\r\n"));
    }

    @Test
    public void rejectsPathsOutsideSessionWorkspace() {
        assertDenied("../outside.txt");
        assertDenied("outputs/../../outside.txt");
        assertDenied(workspace.getParent().resolve("host-abs.txt").toString());
        assertDenied("/etc/passwd");
    }

    private void assertDenied(String path) {
        try {
            capability.writeText(Map.of("path", path, "content", "x"));
            Assert.fail("expected denial for " + path);
        } catch (CodeModeExecutionException e) {
            Assert.assertEquals(path, "CODE_MODE_CAPABILITY_DENIED", e.getErrorCode().code());
        }
    }

    @Test
    public void reportsMissingFileAsNotFound() {
        try {
            capability.readText(Map.of("path", "outputs/missing.txt"));
            Assert.fail("expected not found");
        } catch (CodeModeExecutionException e) {
            Assert.assertEquals("CODE_MODE_WORKSPACE_FILE_NOT_FOUND", e.getErrorCode().code());
        }
    }

    @Test
    public void rejectsOversizedReadAndWrite() {
        CodeModeProperties.Workspace limits = new CodeModeProperties().getWorkspace();
        limits.setMaxWriteBytes(8);
        limits.setMaxReadBytes(4);
        AgentContext agentContext = AgentContext.builder()
                .requestId("req-2")
                .workspaceRoot(workspace.toAbsolutePath().normalize().toString())
                .build();
        WorkspaceCodeCapability small = new WorkspaceCodeCapability(
                agentContext, new CodeModeExecutionBudget(30_000L, 100, 1_000_000L),
                new WorkspaceService(null, new WorkspacePathGuard(), null, null), limits);

        try {
            small.writeText(Map.of("path", "outputs/big.txt", "content", "123456789"));
            Assert.fail("expected write limit");
        } catch (CodeModeExecutionException e) {
            Assert.assertEquals("CODE_MODE_FILE_TOO_LARGE", e.getErrorCode().code());
        }

        writeRaw("outputs/raw.txt", "0123456789");
        try {
            small.readText(Map.of("path", "outputs/raw.txt"));
            Assert.fail("expected read limit");
        } catch (CodeModeExecutionException e) {
            Assert.assertEquals("CODE_MODE_FILE_TOO_LARGE", e.getErrorCode().code());
        }
    }

    @Test
    public void truncatesTextOnlyWhenExplicitlyAsked() {
        writeRaw("outputs/long.txt", "abcdefghij");
        @SuppressWarnings("unchecked")
        Map<String, Object> read = (Map<String, Object>) capability.readText(Map.of(
                "path", "outputs/long.txt", "maxChars", 4));
        Assert.assertEquals("abcd", read.get("content"));
        Assert.assertEquals(Boolean.TRUE, read.get("truncated"));
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
}

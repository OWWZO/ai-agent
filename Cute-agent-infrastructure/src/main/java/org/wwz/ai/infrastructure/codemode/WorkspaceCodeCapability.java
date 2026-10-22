package org.wwz.ai.infrastructure.codemode;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeErrorCode;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeInvocationRecorder;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceAccessException;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceFileRegistration;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.types.agent.config.CodeModeProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Code Mode 的 {@code workspace} capability：会话 workspace 内的文件读写与 CSV 处理。
 * <p>
 * 路径安全：只走 {@link WorkspaceService} 解析，解析后<b>再对 realPath 做一次根包含检查</b>
 * （防符号链接逃逸）；对 JS 只回传 Agent 可见的相对路径，不暴露宿主绝对路径。
 * 写入走「临时文件 → 原子替换 → 登记 artifact」，失败时保留旧文件。
 * </p>
 */
public final class WorkspaceCodeCapability {

    private final AgentContext agentContext;
    private final CodeModeExecutionBudget budget;
    private final WorkspaceService workspaceService;
    private final CodeModeProperties.Workspace limits;

    public WorkspaceCodeCapability(AgentContext agentContext,
                                   CodeModeExecutionBudget budget,
                                   WorkspaceService workspaceService,
                                   CodeModeProperties.Workspace limits) {
        this.agentContext = agentContext;
        this.budget = budget;
        this.workspaceService = workspaceService;
        this.limits = limits == null ? new CodeModeProperties.Workspace() : limits;
    }

    /** 读文本。 */
    public Map<String, Object> readText(Map<String, Object> args) {
        Map<String, Object> params = begin(args, CodeModeInvocationRecorder.TOOL_WORKSPACE_READ);
        Path file = resolvePath(requirePath(params), false);
        long maxBytes = longParam(params, "maxBytes", limits.getMaxReadBytes());
        byte[] bytes = readAllBytes(file, maxBytes);
        int maxChars = intParam(params, "maxChars", 0);
        String content = new String(bytes, StandardCharsets.UTF_8);
        boolean truncated = false;
        if (maxChars > 0 && content.length() > maxChars) {
            content = content.substring(0, maxChars);
            truncated = true;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", agentPath(file));
        result.put("content", content);
        result.put("chars", content.length());
        result.put("truncated", truncated);
        result.put("version", version(bytes));
        result.put("encoding", "UTF-8");
        return finish(params, CodeModeInvocationRecorder.TOOL_WORKSPACE_READ, result);
    }

    /** 读二进制（JS 侧以 Base64 表示）。 */
    public Map<String, Object> readBytes(Map<String, Object> args) {
        Map<String, Object> params = begin(args, CodeModeInvocationRecorder.TOOL_WORKSPACE_READ);
        Path file = resolvePath(requirePath(params), false);
        long maxBytes = longParam(params, "maxBytes", limits.getMaxReadBytes());
        byte[] bytes = readAllBytes(file, maxBytes);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", agentPath(file));
        result.put("base64", Base64.getEncoder().encodeToString(bytes));
        result.put("bytes", bytes.length);
        result.put("version", version(bytes));
        return finish(params, CodeModeInvocationRecorder.TOOL_WORKSPACE_READ, result);
    }

    /** 读 CSV → 结构化 rows（解析在 Java 侧完成）。 */
    public Map<String, Object> readCsv(Map<String, Object> args) {
        Map<String, Object> params = begin(args, CodeModeInvocationRecorder.TOOL_WORKSPACE_READ);
        Path file = resolvePath(requirePath(params), false);
        byte[] bytes = readAllBytes(file, limits.getMaxReadBytes());
        String delimiterRaw = params.get("delimiter") == null ? null : String.valueOf(params.get("delimiter"));
        char delimiter = StringUtils.isBlank(delimiterRaw) ? ',' : delimiterRaw.charAt(0);
        int maxRows = intParam(params, "maxRows", limits.getMaxCsvRows());
        CsvCodec.Table table = CsvCodec.parse(
                CsvCodec.stripBom(new String(bytes, StandardCharsets.UTF_8)),
                delimiter,
                maxRows > 0 ? maxRows : 100_000,
                limits.getMaxCsvColumns());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", agentPath(file));
        result.put("columns", table.columns());
        result.put("rows", table.rows());
        result.put("rowCount", table.rows().size());
        result.put("version", version(bytes));
        result.put("encoding", "UTF-8");
        result.put("delimiter", String.valueOf(delimiter));
        return finish(params, CodeModeInvocationRecorder.TOOL_WORKSPACE_READ, result);
    }

    /** 写文本（原子替换 + artifact 登记）。 */
    public Map<String, Object> writeText(Map<String, Object> args) {
        Map<String, Object> params = begin(args, CodeModeInvocationRecorder.TOOL_WORKSPACE_WRITE);
        Path file = resolvePath(requirePath(params), true);
        Object content = params.get("content");
        if (content == null) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT,
                    "writeText 需要 content");
        }
        byte[] bytes = String.valueOf(content).getBytes(StandardCharsets.UTF_8);
        return write(params, file, bytes, "writeText");
    }

    /** 写二进制（Base64 入参）。 */
    public Map<String, Object> writeBytes(Map<String, Object> args) {
        Map<String, Object> params = begin(args, CodeModeInvocationRecorder.TOOL_WORKSPACE_WRITE);
        Path file = resolvePath(requirePath(params), true);
        Object base64 = params.get("base64");
        if (base64 == null) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT,
                    "writeBytes 需要 base64");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(String.valueOf(base64).trim());
        } catch (IllegalArgumentException e) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT,
                    "writeBytes 的 base64 非法");
        }
        return write(params, file, bytes, "writeBytes");
    }

    /** 写 CSV（序列化在 Java 侧完成）。 */
    public Map<String, Object> writeCsv(Map<String, Object> args) {
        Map<String, Object> params = begin(args, CodeModeInvocationRecorder.TOOL_WORKSPACE_WRITE);
        Path file = resolvePath(requirePath(params), true);
        List<String> columns = stringList(params.get("columns"));
        List<Map<String, Object>> rows = rowList(params.get("rows"));
        if (columns.isEmpty()) {
            // 列为空时从第一行推导，兼容只传 rows 的写法
            if (rows.isEmpty()) {
                throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT,
                        "writeCsv 需要 columns 或非空 rows");
            }
            columns = new ArrayList<>(rows.get(0).keySet());
        }
        if (columns.size() > limits.getMaxCsvColumns()) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_FILE_TOO_LARGE,
                    "CSV 列数超过上限 " + limits.getMaxCsvColumns());
        }
        if (rows.size() > limits.getMaxCsvRows()) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_FILE_TOO_LARGE,
                    "CSV 行数超过上限 " + limits.getMaxCsvRows());
        }
        String delimiterRaw = params.get("delimiter") == null ? null : String.valueOf(params.get("delimiter"));
        char delimiter = StringUtils.isBlank(delimiterRaw) ? ',' : delimiterRaw.charAt(0);
        byte[] bytes = CsvCodec.serialize(columns, rows, delimiter).getBytes(StandardCharsets.UTF_8);
        return write(params, file, bytes, "writeCsv");
    }

    // ---------- 内部实现 ----------

    private Map<String, Object> begin(Map<String, Object> args, String toolName) {
        budget.beginCall(agentContext);
        Map<String, Object> params = args == null ? new LinkedHashMap<>() : new LinkedHashMap<>(args);
        ToolArtifactSource source = agentContext.getCurrentToolArtifactSource();
        Long invocationId = CodeModeInvocationRecorder.begin(
                agentContext,
                source == null ? null : source.getToolCallId(),
                toolName,
                CodeModeInvocationRecorder.summarize(params),
                LocalDateTime.now());
        params.put("__invocationId", invocationId == null ? -1L : invocationId);
        return params;
    }

    private Map<String, Object> finish(Map<String, Object> params, String toolName, Map<String, Object> result) {
        Long invocationId = (Long) params.get("__invocationId");
        CodeModeInvocationRecorder.end(agentContext,
                invocationId == null || invocationId < 0 ? null : invocationId,
                toolName,
                ExecutionLedgerConstants.STATUS_SUCCESS,
                String.valueOf(result.get("path")),
                null,
                LocalDateTime.now());
        return result;
    }

    private Map<String, Object> write(Map<String, Object> params, Path file, byte[] bytes, String method) {
        if (bytes.length > limits.getMaxWriteBytes()) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_FILE_TOO_LARGE,
                    "写入字节数超过单文件上限 " + limits.getMaxWriteBytes());
        }
        budget.accountWrite(bytes.length);
        checkVersion(file, params.get("expectedVersion"));
        Long invocationId = (Long) params.get("__invocationId");
        String toolName = CodeModeInvocationRecorder.TOOL_WORKSPACE_WRITE;
        try {
            atomicWrite(file, bytes);
        } catch (IOException e) {
            CodeModeInvocationRecorder.end(agentContext, invocationId, toolName,
                    ExecutionLedgerConstants.STATUS_FAILED, null, e.getMessage(), LocalDateTime.now());
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_RUNTIME_ERROR,
                    method + " 写入失败：" + e.getMessage(), e);
        }
        String agentPath = agentPath(file);
        budget.produced(agentPath);
        WorkspaceFileRegistration.registerLocalFile(agentContext, agentPath, file, "Code Mode 生成文件");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", agentPath);
        result.put("bytes", bytes.length);
        result.put("version", version(bytes));
        result.put("ok", true);
        CodeModeInvocationRecorder.end(agentContext, invocationId, toolName,
                ExecutionLedgerConstants.STATUS_SUCCESS, agentPath, null, LocalDateTime.now());
        return result;
    }

    /** 临时文件 + 原子替换；失败保留旧文件。 */
    private void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent == null ? Path.of(".") : parent, ".codemode-", ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailed) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (Files.exists(temp)) {
                try {
                    Files.delete(temp);
                } catch (IOException ignore) {
                    // 忽略清理失败，旧文件仍然完整
                }
            }
        }
    }

    private void checkVersion(Path file, Object expectedVersion) {
        String expected = expectedVersion == null ? null : String.valueOf(expectedVersion).trim();
        if (StringUtils.isBlank(expected) || !Files.isRegularFile(file)) {
            return;
        }
        byte[] current;
        try {
            current = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_RUNTIME_ERROR,
                    "读取现有文件失败：" + e.getMessage(), e);
        }
        String actual = version(current);
        if (!expected.equals(actual)) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_FILE_CONFLICT,
                    "文件已被修改：expectedVersion=" + expected + "，实际=" + actual);
        }
    }

    private byte[] readAllBytes(Path file, long maxBytes) {
        long limit = maxBytes > 0 ? maxBytes : limits.getMaxReadBytes();
        try {
            long size = Files.size(file);
            if (size > limit) {
                throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_FILE_TOO_LARGE,
                        "文件超过读取上限 " + limit + " 字节");
            }
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_RUNTIME_ERROR,
                    "读取文件失败：" + e.getMessage(), e);
        }
    }

    /**
     * 解析并校验路径：会话 workspace 内、拒绝宿主绝对路径、拒绝符号链接逃逸。
     */
    private Path resolvePath(String rawPath, boolean write) {
        Path root = workspaceRoot();
        Path candidate;
        try {
            candidate = write
                    ? workspaceService.resolveWritablePath(root, rawPath)
                    : workspaceService.resolveAllowedPath(root, rawPath);
        } catch (WorkspaceAccessException e) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_CAPABILITY_DENIED, e.getMessage(), e);
        }
        Path normalizedRoot = normalize(root);
        if (!normalize(candidate).startsWith(normalizedRoot)) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_CAPABILITY_DENIED,
                    "路径不在当前会话 workspace 内");
        }
        // 符号链接逃逸：对已存在的部分做 realPath 复查
        Path existing = Files.exists(candidate) ? candidate : candidate.getParent();
        if (existing != null && Files.exists(existing)) {
            try {
                if (!existing.toRealPath().startsWith(normalizedRoot.toRealPath())) {
                    throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_CAPABILITY_DENIED,
                            "路径解析后越出会话 workspace（符号链接）");
                }
            } catch (IOException e) {
                throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_CAPABILITY_DENIED,
                        "路径解析失败：" + e.getMessage(), e);
            }
        }
        if (write && Files.isDirectory(candidate)) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT,
                    "写入目标不能是目录");
        }
        if (!write && !Files.isRegularFile(candidate)) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_WORKSPACE_FILE_NOT_FOUND,
                    "文件不存在");
        }
        return candidate;
    }

    private Path workspaceRoot() {
        String root = agentContext == null ? null : agentContext.getWorkspaceRoot();
        if (StringUtils.isNotBlank(root)) {
            return Path.of(root).toAbsolutePath().normalize();
        }
        String sessionId = agentContext == null ? null : agentContext.getSessionId();
        if (workspaceService == null) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_CAPABILITY_UNAVAILABLE,
                    "workspace 未装配");
        }
        return workspaceService.resolveAndEnsureRoot(sessionId);
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private String agentPath(Path absolute) {
        if (workspaceService == null) {
            return absolute.getFileName() == null ? "file" : absolute.getFileName().toString();
        }
        try {
            String visible = workspaceService.toAgentVisiblePath(workspaceRoot(), absolute);
            return StringUtils.isBlank(visible) ? absolute.getFileName().toString() : visible;
        } catch (RuntimeException e) {
            return absolute.getFileName() == null ? "file" : absolute.getFileName().toString();
        }
    }

    private static String version(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes == null ? new byte[0] : bytes);
            StringBuilder builder = new StringBuilder("sha256:");
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_RUNTIME_ERROR,
                    "SHA-256 不可用：" + e.getMessage(), e);
        }
    }

    private static String requirePath(Map<String, Object> params) {
        Object raw = params.get("path");
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT, "path 不能为空");
        }
        return String.valueOf(raw).trim();
    }

    private static long longParam(Map<String, Object> params, String key, long fallback) {
        Object raw = params.get(key);
        if (raw instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        return fallback;
    }

    private static int intParam(Map<String, Object> params, String key, int fallback) {
        Object raw = params.get(key);
        if (raw instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rowList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                ((Map<String, Object>) map).forEach((key, value) -> row.put(String.valueOf(key), value));
                rows.add(row);
            } else {
                throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT,
                        "writeCsv 的 rows 必须是对象数组");
            }
        }
        return rows;
    }
}

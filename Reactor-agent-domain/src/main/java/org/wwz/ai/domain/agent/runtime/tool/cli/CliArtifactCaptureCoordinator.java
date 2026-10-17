package org.wwz.ai.domain.agent.runtime.tool.cli;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 规划并协调 CLI 文件捕获边界。
 * <p>
 * 普通 Browser 调用不创建 lease；只有已知下载命令按 capture root 获取目录级锁，因此
 * 不会把同一 owner 的普通只读调用串行化。
 */
public final class CliArtifactCaptureCoordinator {

    public static final String GENERATED_ROOT = ".reactor-artifacts";
    public static final String OUTPUT_ROOT_INVALID = "artifact_output_root_invalid";
    public static final String OUTPUT_ROOT_OUTSIDE_WORKSPACE = "artifact_output_root_outside_workspace";
    public static final String CAPTURE_ROOT_CONFLICT = "artifact_capture_root_conflict";

    private static final ConcurrentHashMap<String, LockEntry> LOCKS = new ConcurrentHashMap<>();

    public static CliArtifactCapturePlan plan(String toolName,
                                              List<String> originalArgs,
                                              List<String> processArgs,
                                              String workspaceRoot,
                                              String toolCallId,
                                              String requestId) {
        List<String> original = copy(originalArgs);
        List<String> actual = copy(processArgs);
        if (!OpenCliArgv.isDownloadCommand(actual)) {
            return CliArtifactCapturePlan.ordinary(original, actual);
        }

        OpenCliArgv.OutputOption option = OpenCliArgv.resolveOutputOption(actual);
        if (option.present() && !option.valid()) {
            return CliArtifactCapturePlan.invalid(original, actual, true, OUTPUT_ROOT_INVALID,
                    "下载命令的 output 目录无效");
        }
        Path workspace = resolveWorkspace(workspaceRoot);
        if (workspace == null || !Files.isDirectory(workspace)) {
            return CliArtifactCapturePlan.invalid(original, actual, true, OUTPUT_ROOT_OUTSIDE_WORKSPACE,
                    "workspace 不可用");
        }

        String captureId = captureId(toolCallId, requestId);
        String requestedRoot = option.present() ? option.rawValue() : null;
        String captureRoot;
        boolean injected = !option.present();
        if (injected) {
            captureRoot = GENERATED_ROOT + "/" + safeSegment(toolName, "browser") + "/" + captureId;
        } else {
            captureRoot = normalizeRoot(requestedRoot);
            if (captureRoot == null) {
                return CliArtifactCapturePlan.invalid(original, actual, true, OUTPUT_ROOT_INVALID,
                        "output 目录必须是 workspace 内的相对路径");
            }
        }

        Path absolute = workspace.resolve(captureRoot).normalize();
        if (!absolute.startsWith(workspace)) {
            return CliArtifactCapturePlan.invalid(original, actual, true, OUTPUT_ROOT_OUTSIDE_WORKSPACE,
                    "output 目录必须位于 workspace 内");
        }
        if (!isExistingPathUnderWorkspace(absolute, workspace)) {
            return CliArtifactCapturePlan.invalid(original, actual, true, OUTPUT_ROOT_OUTSIDE_WORKSPACE,
                    "output 目录不能通过符号链接逃出 workspace");
        }

        List<String> rewritten;
        try {
            rewritten = OpenCliArgv.rewriteOutputRoot(actual, captureRoot);
        } catch (IllegalArgumentException e) {
            return CliArtifactCapturePlan.invalid(original, actual, true, OUTPUT_ROOT_INVALID,
                    "下载命令的 output 目录无效");
        }
        return CliArtifactCapturePlan.builder()
                .originalArgs(original)
                .rewrittenArgs(rewritten)
                .artifactCommand(true)
                .requestedOutputRoot(requestedRoot)
                .captureRoot(captureRoot)
                .captureRootAbsolute(absolute.toString())
                .outputRootInjected(injected)
                .captureId(captureId)
                .valid(true)
                .build();
    }

    /** 获取 capture root 的目录级 lease，并在 lease 内创建/校验目录。 */
    public static CliArtifactCaptureLease acquire(CliArtifactCapturePlan plan) {
        if (plan == null || !plan.isValid() || !plan.requiresCaptureLease()) {
            return CliArtifactCaptureLease.noop();
        }
        Path root;
        try {
            root = Path.of(plan.getCaptureRootAbsolute()).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new CliArtifactCaptureException(CAPTURE_ROOT_CONFLICT, "capture root 无效", e);
        }
        // The plan already validated the lexical workspace boundary. The lock key is the
        // normalized absolute capture root, so equivalent ./foo and foo calls share a lock.
        return acquirePath(root, plan.getCaptureId(), plan.getCaptureRoot(), true, resolvePlanWorkspace(plan));
    }

    /** host_cli 的历史 workspace diff fallback 使用 workspace 级 lease 防止交叉捕获。 */
    public static CliArtifactCaptureLease acquireWorkspaceDiffLease(String workspaceRoot, String captureId) {
        Path root = resolveWorkspace(workspaceRoot);
        if (root == null || !Files.isDirectory(root)) {
            throw new CliArtifactCaptureException(CAPTURE_ROOT_CONFLICT, "workspace capture root 不可用");
        }
        return acquirePath(root, captureId, null, false, root);
    }

    private static CliArtifactCaptureLease acquirePath(Path root,
                                                       String captureId,
                                                       String captureRoot,
                                                       boolean create,
                                                       Path workspace) {
        // The plan already validated the lexical workspace boundary. The lock key is the
        // normalized absolute capture root, so equivalent ./foo and foo calls share a lock.
        String key = root.toString();
        LockEntry entry = LOCKS.compute(key, (ignored, current) -> {
            LockEntry next = current == null ? new LockEntry() : current;
            next.users.incrementAndGet();
            return next;
        });
        entry.lock.lock();
        try {
            if (create) {
                Files.createDirectories(root);
            }
            Path realRoot = root.toRealPath();
            if (!realRoot.startsWith(workspace.toRealPath())) {
                release(key, entry);
                throw new CliArtifactCaptureException(CAPTURE_ROOT_CONFLICT,
                        "capture root 不能通过符号链接逃出 workspace");
            }
            return new CliArtifactCaptureLease(captureId, captureRoot, realRoot.toString(),
                    entry.lock, false, () -> release(key, entry));
        } catch (CliArtifactCaptureException e) {
            throw e;
        } catch (Exception e) {
            release(key, entry);
            throw new CliArtifactCaptureException(CAPTURE_ROOT_CONFLICT, "capture root 无法创建或校验", e);
        }
    }

    /** 仅用于调用方构造 failure payload，不将绝对路径泄露给 Agent。 */
    public static ToolCaptureFailure failure(CliArtifactCapturePlan plan) {
        return new ToolCaptureFailure(
                StringUtils.defaultIfBlank(plan == null ? null : plan.getErrorCode(), OUTPUT_ROOT_INVALID),
                StringUtils.defaultIfBlank(plan == null ? null : plan.getErrorMessage(), "artifact capture plan invalid"));
    }

    private static Path resolvePlanWorkspace(CliArtifactCapturePlan plan) {
        String capture = plan.getCaptureRootAbsolute();
        String relative = plan.getCaptureRoot();
        Path capturePath = Path.of(capture).toAbsolutePath().normalize();
        if (relative == null || relative.isBlank()) {
            return capturePath;
        }
        int segments = relative.split("/").length;
        Path workspace = capturePath;
        for (int i = 0; i < segments; i++) {
            workspace = workspace.getParent() == null ? workspace : workspace.getParent();
        }
        return workspace.toAbsolutePath().normalize();
    }

    private static void release(String key, LockEntry entry) {
        if (entry.lock.isHeldByCurrentThread()) {
            entry.lock.unlock();
        }
        if (entry.users.decrementAndGet() == 0 && !entry.lock.isLocked() && !entry.lock.hasQueuedThreads()) {
            LOCKS.remove(key, entry);
        }
    }

    private static Path resolveWorkspace(String workspaceRoot) {
        if (StringUtils.isBlank(workspaceRoot)) {
            return null;
        }
        try {
            return Path.of(workspaceRoot.trim()).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static boolean isExistingPathUnderWorkspace(Path candidate, Path workspace) {
        Path current = candidate;
        while (current != null && !Files.exists(current)) {
            current = current.getParent();
        }
        if (current == null) {
            return false;
        }
        try {
            return current.toRealPath().startsWith(workspace.toRealPath());
        } catch (Exception e) {
            return false;
        }
    }

    private static String normalizeRoot(String raw) {
        try {
            return OpenCliArgv.normalizeOutputRoot(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String captureId(String toolCallId, String requestId) {
        String source = StringUtils.defaultIfBlank(toolCallId, StringUtils.defaultIfBlank(requestId, "capture"));
        String digest;
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : bytes) {
                hex.append(String.format("%02x", value));
            }
            digest = hex.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            digest = Integer.toHexString(source.hashCode());
        }
        return digest + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static String safeSegment(String value, String fallback) {
        String raw = StringUtils.defaultIfBlank(value, fallback);
        String safe = raw.replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.isBlank() ? fallback : safe;
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger users = new AtomicInteger();
    }

    public record ToolCaptureFailure(String code, String message) {
    }

    public static final class CliArtifactCaptureException extends RuntimeException {
        private final String code;

        public CliArtifactCaptureException(String code, String message) {
            super(message);
            this.code = code;
        }

        public CliArtifactCaptureException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}

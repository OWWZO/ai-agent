package org.wwz.ai.domain.agent.runtime.tool.cli;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wwz.ai.domain.agent.adapter.port.cli.CliArtifact;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.CodeInterpreterResponse;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceFileRegistration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * CLI 文件产物物化的唯一入口。
 * <p>
 * {@code host_cli} / {@code browser} / {@code agent_browser} 三条路径共用本模块：
 * CLI 子进程把文件写进 Agent workspace，Java 侧负责校验、登记、绑定和事件广播，
 * 调用方不需要知道文件来自宿主机、Browser Relay 还是 Agent Browser CDP。
 */
public final class CliArtifactMaterializer {

    private static final Logger log = LoggerFactory.getLogger(CliArtifactMaterializer.class);

    public static final int DEFAULT_MAX_FILES = 20;
    public static final long DEFAULT_MAX_FILE_BYTES = 64L * 1024 * 1024;
    public static final long DEFAULT_MAX_TOTAL_BYTES = 256L * 1024 * 1024;

    /** workspace 前后快照的遍历深度；快照只是兼容兜底，不允许无限递归。 */
    private static final int SNAPSHOT_MAX_DEPTH = 4;
    /** 单次 diff 最多考虑的候选数，避免把整个 workspace 当成产物。 */
    private static final int DIFF_CANDIDATE_LIMIT = 200;

    private CliArtifactMaterializer() {
    }

    /**
     * 物化本次 CLI 调用产生的文件。
     * <p>
     * 单个文件失败不会中断其它文件；成功项一定有 {@code downloadUrl}，失败项不进 registry。
     */
    public static CliArtifactMaterialization materialize(CliArtifactMaterializeCommand command) {
        if (command == null || command.getAgentContext() == null) {
            return CliArtifactMaterialization.empty();
        }
        AgentContext agentContext = command.getAgentContext();
        Path workspaceRoot = resolveRoot(command.getWorkspaceRoot());
        if (workspaceRoot == null) {
            return CliArtifactMaterialization.empty();
        }
        List<Candidate> candidates = collectCandidates(workspaceRoot, command);
        if (candidates.isEmpty()) {
            return CliArtifactMaterialization.empty();
        }
        int maxFiles = command.getMaxFiles() > 0 ? command.getMaxFiles() : DEFAULT_MAX_FILES;
        long maxFileBytes = command.getMaxFileBytes() > 0 ? command.getMaxFileBytes() : DEFAULT_MAX_FILE_BYTES;
        long maxTotalBytes = command.getMaxTotalBytes() > 0 ? command.getMaxTotalBytes() : DEFAULT_MAX_TOTAL_BYTES;

        List<MaterializedCliArtifact> artifacts = new ArrayList<>();
        List<CliArtifactFailure> failures = new ArrayList<>();
        long totalBytes = 0L;
        for (Candidate candidate : candidates) {
            if (artifacts.size() >= maxFiles) {
                failures.add(CliArtifactFailure.builder()
                        .relativePath(candidate.display())
                        .reason(CliArtifactFailure.ARTIFACT_LIMIT_EXCEEDED)
                        .message("本次调用登记的文件数超过上限 " + maxFiles)
                        .build());
                break;
            }
            if (candidate.failureReason() != null) {
                failures.add(CliArtifactFailure.builder()
                        .relativePath(candidate.display())
                        .reason(candidate.failureReason())
                        .message(candidate.failureMessage())
                        .build());
                continue;
            }
            if (candidate.path() == null) {
                failures.add(CliArtifactFailure.builder()
                        .relativePath(candidate.display())
                        .reason(CliArtifactFailure.OUTSIDE_WORKSPACE)
                        .message("文件不在 workspace 内")
                        .build());
                continue;
            }
            Path file = candidate.path();
            String display = candidate.display() == null
                    ? (file.getFileName() == null ? null : file.getFileName().toString())
                    : candidate.display();
            if (!Files.isRegularFile(file)) {
                failures.add(CliArtifactFailure.builder()
                        .relativePath(display)
                        .reason(CliArtifactFailure.FILE_NOT_FOUND)
                        .message("文件不存在或不是普通文件")
                        .build());
                continue;
            }
            String relativePath = relativize(workspaceRoot, file, command);
            if (relativePath == null) {
                failures.add(CliArtifactFailure.builder()
                        .relativePath(display)
                        .reason(StringUtils.isNotBlank(command.getCaptureRootAbsolute())
                                ? CliArtifactFailure.CANDIDATE_OUTSIDE_CAPTURE_ROOT
                                : CliArtifactFailure.OUTSIDE_WORKSPACE)
                        .message(StringUtils.isNotBlank(command.getCaptureRootAbsolute())
                                ? "文件不在当前调用的 capture root 内"
                                : "文件不在 workspace 内")
                        .build());
                continue;
            }
            long size;
            try {
                size = Files.size(file);
            } catch (IOException e) {
                failures.add(CliArtifactFailure.builder()
                        .relativePath(relativePath)
                        .reason(CliArtifactFailure.FILE_NOT_FOUND)
                        .message("文件无法读取")
                        .build());
                continue;
            }
            if (size > maxFileBytes) {
                failures.add(CliArtifactFailure.builder()
                        .relativePath(relativePath)
                        .reason(CliArtifactFailure.FILE_TOO_LARGE)
                        .message("文件大小超过上限 " + maxFileBytes)
                        .build());
                continue;
            }
            if (totalBytes + size > maxTotalBytes) {
                failures.add(CliArtifactFailure.builder()
                        .relativePath(relativePath)
                        .reason(CliArtifactFailure.ARTIFACT_LIMIT_EXCEEDED)
                        .message("本次调用登记的文件总大小超过上限 " + maxTotalBytes)
                        .build());
                break;
            }
            MaterializedCliArtifact materialized = register(agentContext, file, relativePath, size,
                    StringUtils.defaultIfBlank(command.getCommandLabel(), "CLI 执行产物"));
            if (materialized == null) {
                failures.add(CliArtifactFailure.builder()
                        .relativePath(relativePath)
                        .reason(CliArtifactFailure.ARTIFACT_REGISTER_FAILED)
                        .message("文件服务登记失败")
                        .build());
                continue;
            }
            totalBytes += size;
            artifacts.add(materialized);
        }
        emitFileEvent(agentContext, command.getCommandLabel(), artifacts);
        return CliArtifactMaterialization.builder()
                .artifacts(artifacts)
                .failures(failures)
                .build();
    }

    /**
     * 执行前的 workspace 文件快照，用于没有 CLI 报告候选时的兼容兜底。
     * 并发工具调用共享同一 workspace 时不要使用。
     */
    public static Set<String> snapshotWorkspace(String workspaceRoot) {
        Path root = resolveRoot(workspaceRoot);
        if (root == null || !Files.isDirectory(root)) {
            return Set.of();
        }
        Set<String> files = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(root, SNAPSHOT_MAX_DEPTH)) {
            walk.filter(Files::isRegularFile)
                    .forEach(path -> files.add(path.toAbsolutePath().normalize().toString()));
        } catch (IOException | RuntimeException ignore) {
            // 快照失败只影响兼容兜底，不阻断 CLI 执行
        }
        return files;
    }

    private static MaterializedCliArtifact register(AgentContext agentContext,
                                                    Path file,
                                                    String relativePath,
                                                    long size,
                                                    String commandLabel) {
        WorkspaceFileRegistration.LocalFileRegistrationResult result =
                WorkspaceFileRegistration.registerLocalFile(agentContext, relativePath, file, commandLabel, false);
        if (!result.isSuccess() || result.getResponse() == null
                || StringUtils.isBlank(result.getResponse().getOssUrl())) {
            log.warn("cli artifact register failed, reason={}, relativePath={}",
                    result.getReason(), relativePath);
            return null;
        }
        Long fileSize = result.getResponse().getFileSize() == null
                ? size
                : result.getResponse().getFileSize().longValue();
        return MaterializedCliArtifact.builder()
                .relativePath(relativePath)
                .fileName(file.getFileName() == null ? relativePath : file.getFileName().toString())
                .fileSize(fileSize)
                .mimeType(probeMime(file))
                .previewUrl(result.getResponse().getDomainUrl())
                .downloadUrl(result.getResponse().getOssUrl())
                .build();
    }

    private static void emitFileEvent(AgentContext agentContext,
                                      String commandLabel,
                                      List<MaterializedCliArtifact> artifacts) {
        if (artifacts.isEmpty() || agentContext.getPrinter() == null) {
            return;
        }
        ToolArtifactSource source = agentContext.getCurrentToolArtifactSource();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("command", StringUtils.defaultIfBlank(commandLabel, "CLI 执行产物"));
        event.put("requestId", StringUtils.defaultIfBlank(
                agentContext.getSessionId(), agentContext.getRequestId()));
        event.put("sessionId", agentContext.getSessionId());
        if (source != null) {
            event.put("toolCallId", source.getToolCallId());
            event.put("toolName", source.getToolName());
        }
        if (artifacts.size() == 1) {
            event.put("relativePath", artifacts.get(0).getRelativePath());
        }
        List<CodeInterpreterResponse.FileInfo> fileInfo = new ArrayList<>(artifacts.size());
        for (MaterializedCliArtifact artifact : artifacts) {
            fileInfo.add(CodeInterpreterResponse.FileInfo.builder()
                    .fileName(artifact.getFileName())
                    .relativePath(artifact.getRelativePath())
                    .ossUrl(artifact.getDownloadUrl())
                    .domainUrl(artifact.getPreviewUrl())
                    .fileSize(artifact.getFileSize() == null ? null : artifact.getFileSize().intValue())
                    .build());
        }
        event.put("fileInfo", fileInfo);
        agentContext.getPrinter().send("file", event, null);
    }

    private static List<Candidate> collectCandidates(Path workspaceRoot, CliArtifactMaterializeCommand command) {
        List<Candidate> candidates = new ArrayList<>();
        Set<String> seenPaths = new LinkedHashSet<>();
        List<CliArtifact> reported = command.getReportedArtifacts();
        if (reported != null && !reported.isEmpty()) {
            for (CliArtifact artifact : reported) {
                if (artifact == null || StringUtils.isBlank(artifact.getPath())) {
                    continue;
                }
                String raw = artifact.getPath().trim();
                if (seenPaths.contains("reported:" + raw)) {
                    continue;
                }
                Candidate candidate = resolveReportedCandidate(workspaceRoot, raw, command);
                if (candidate.path() != null) {
                    if (seenPaths.add("path:" + candidate.path().toAbsolutePath().normalize())) {
                        candidates.add(candidate);
                    }
                } else {
                    seenPaths.add("reported:" + raw);
                    candidates.add(candidate);
                }
            }
        }
        Set<String> baseline = command.getBaselineFiles();
        if (baseline == null) {
            return candidates;
        }
        Path scanRoot = resolveScanRoot(workspaceRoot, command);
        if (scanRoot == null) {
            // 声明的 output root 越界：不扫描任何目录，避免把 workspace 外文件登记成产物
            return candidates;
        }
        try (Stream<Path> walk = Files.walk(scanRoot, SNAPSHOT_MAX_DEPTH)) {
            List<Path> fresh = walk.filter(Files::isRegularFile)
                    .filter(path -> !baseline.contains(path.toAbsolutePath().normalize().toString()))
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                    .toList();
            for (Path path : fresh) {
                if (candidates.size() >= DIFF_CANDIDATE_LIMIT) {
                    break;
                }
                Path normalized = path.toAbsolutePath().normalize();
                String key = "path:" + normalized;
                if (seenPaths.add(key)) {
                    candidates.add(new Candidate(normalized, null, null, null));
                }
            }
        } catch (IOException | RuntimeException ignore) {
            // diff 兜底失败时本次调用不登记文件，不阻断 CLI 结果
        }
        return candidates;
    }

    /** diff 兜底的扫描根：优先使用当前调用的 capture root。 */
    private static Path resolveScanRoot(Path workspaceRoot, CliArtifactMaterializeCommand command) {
        String captureRootAbsolute = StringUtils.trimToNull(command.getCaptureRootAbsolute());
        if (captureRootAbsolute != null) {
            try {
                Path resolved = Path.of(captureRootAbsolute).toAbsolutePath().normalize();
                return isRealPathUnder(resolved, workspaceRoot) ? resolved : null;
            } catch (InvalidPathException e) {
                return null;
            }
        }
        String captureRoot = StringUtils.trimToNull(command.getCaptureRoot());
        if (captureRoot != null) {
            try {
                captureRoot = OpenCliArgv.normalizeOutputRoot(captureRoot);
            } catch (IllegalArgumentException e) {
                return null;
            }
            Path path = resolveCandidate(workspaceRoot, captureRoot);
            return path != null && isRealPathUnder(path, workspaceRoot) ? path : null;
        }
        String outputRoot = command.getOutputRoot();
        if (StringUtils.isBlank(outputRoot)) {
            return workspaceRoot;
        }
        String normalizedOutputRoot;
        try {
            normalizedOutputRoot = OpenCliArgv.normalizeOutputRoot(outputRoot.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        Path resolved = resolveCandidate(workspaceRoot, normalizedOutputRoot);
        return resolved != null && isRealPathUnder(resolved, workspaceRoot) ? resolved : null;
    }

    private static Candidate resolveReportedCandidate(Path workspaceRoot,
                                                      String raw,
                                                      CliArtifactMaterializeCommand command) {
        String boundaryReason = StringUtils.isNotBlank(command.getCaptureRootAbsolute())
                ? CliArtifactFailure.CANDIDATE_OUTSIDE_CAPTURE_ROOT
                : CliArtifactFailure.OUTSIDE_WORKSPACE;
        if (hasParentSegment(raw) || isCrossPlatformAbsolute(raw)) {
            String reason = isCrossPlatformAbsolute(raw)
                    && StringUtils.isBlank(command.getCaptureRootAbsolute())
                    ? CliArtifactFailure.OUTSIDE_WORKSPACE
                    : boundaryReason;
            return new Candidate(null, safeDisplay(raw), reason, "候选路径不在允许的产物边界内");
        }
        Path resolved = resolveCandidate(workspaceRoot, raw);
        if (resolved == null || !resolved.startsWith(workspaceRoot)) {
            return new Candidate(null, safeDisplay(raw), boundaryReason, "候选路径不在允许的产物边界内");
        }
        return new Candidate(resolved, safeDisplay(raw), null, null);
    }

    private static Path resolveCandidate(Path workspaceRoot, String raw) {
        try {
            Path candidate = Path.of(raw);
            return candidate.isAbsolute()
                    ? candidate.toAbsolutePath().normalize()
                    : workspaceRoot.resolve(candidate).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static Path resolveRoot(String workspaceRoot) {
        if (StringUtils.isBlank(workspaceRoot)) {
            return null;
        }
        try {
            return Path.of(workspaceRoot.trim()).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * 计算 workspace 内相对路径；越界、含 {@code ..} 或符号链接逃逸时返回 null。
     */
    private static String relativize(Path workspaceRoot,
                                     Path file,
                                     CliArtifactMaterializeCommand command) {
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(workspaceRoot)) {
            return null;
        }
        try {
            // realPath 同时消解符号链接，避免 workspace 内软链指向外部文件
            Path realFile = normalized.toRealPath();
            Path realRoot = workspaceRoot.toRealPath();
            if (!realFile.startsWith(realRoot)) {
                return null;
            }
            Path captureRoot = resolveCaptureRoot(workspaceRoot, command);
            if (captureRoot != null && !realFile.startsWith(captureRoot.toRealPath())) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        String relative = workspaceRoot.relativize(normalized).toString().replace('\\', '/');
        if (relative.isEmpty() || relative.startsWith("..")) {
            return null;
        }
        return relative;
    }

    private static String probeMime(Path file) {
        String mime = null;
        try {
            mime = Files.probeContentType(file);
        } catch (IOException | RuntimeException ignore) {
            // 探测失败时回退到扩展名推断
        }
        if (StringUtils.isNotBlank(mime)) {
            return mime;
        }
        return mimeByExtension(file.getFileName() == null ? "" : file.getFileName().toString());
    }

    private static String mimeByExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        switch (fileName.substring(dot + 1).toLowerCase(Locale.ROOT)) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "bmp":
                return "image/bmp";
            case "svg":
                return "image/svg+xml";
            case "mp4":
                return "video/mp4";
            case "webm":
                return "video/webm";
            case "mov":
                return "video/quicktime";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "pdf":
                return "application/pdf";
            case "json":
                return "application/json";
            case "csv":
                return "text/csv";
            case "txt":
            case "log":
            case "md":
                return "text/plain";
            case "html":
            case "htm":
                return "text/html";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "zip":
                return "application/zip";
            default:
                return null;
        }
    }

    private static String safeDisplay(String raw) {
        String display = raw.replace('\\', '/');
        int separator = display.lastIndexOf('/');
        return separator >= 0 ? display.substring(separator + 1) : display;
    }

    /** 候选文件；path 为 null 表示路径无法定位或越界。 */
    private record Candidate(Path path, String display, String failureReason, String failureMessage) {
    }

    private static Path resolveCaptureRoot(Path workspaceRoot, CliArtifactMaterializeCommand command) {
        String absolute = StringUtils.trimToNull(command.getCaptureRootAbsolute());
        if (absolute != null) {
            try {
                Path path = Path.of(absolute).toAbsolutePath().normalize();
                return path.startsWith(workspaceRoot) ? path : null;
            } catch (InvalidPathException e) {
                return null;
            }
        }
        if (StringUtils.isNotBlank(command.getCaptureRoot())) {
            String captureRoot;
            try {
                captureRoot = OpenCliArgv.normalizeOutputRoot(command.getCaptureRoot().trim());
            } catch (IllegalArgumentException e) {
                return null;
            }
            Path path = resolveCandidate(workspaceRoot, captureRoot);
            return path != null && path.startsWith(workspaceRoot) ? path : null;
        }
        if (StringUtils.isNotBlank(command.getOutputRoot())) {
            String outputRoot;
            try {
                outputRoot = OpenCliArgv.normalizeOutputRoot(command.getOutputRoot().trim());
            } catch (IllegalArgumentException e) {
                return null;
            }
            Path path = resolveCandidate(workspaceRoot, outputRoot);
            return path != null && path.startsWith(workspaceRoot) ? path : null;
        }
        return null;
    }

    private static boolean hasParentSegment(String raw) {
        String normalized = raw.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCrossPlatformAbsolute(String raw) {
        return OpenCliArgv.isAbsolutePath(raw);
    }

    private static boolean isRealPathUnder(Path candidate, Path workspaceRoot) {
        if (!candidate.startsWith(workspaceRoot)) {
            return false;
        }
        Path existing = candidate;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return false;
        }
        try {
            return existing.toRealPath().startsWith(workspaceRoot.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }
}

package org.wwz.ai.domain.agent.runtime.tool.workspace;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 解析包含 {@code reactor-tool} 的仓库根，避免 JVM {@code user.dir} 在 monorepo 上级启动时
 * 把 skilloutput 写到错误位置（如 {@code ai-agent/reactor-tool} 而非 {@code Reactor-agent/reactor-tool}）。
 */
public final class WorkspacePaths {

    private static final String REACTOR_TOOL = "reactor-tool";
    private static final String REPO_DIR_NAME = "Reactor-agent";
    private static final String APP_MODULE = "Reactor-agent-app";

    private WorkspacePaths() {
    }

    /**
     * 返回应作为 {@code ${user.dir}} 展开结果的仓库根目录（其下应有 reactor-tool）。
     * <p>
     * 优先识别 monorepo 根（同时存在 reactor-tool 与 Reactor-agent-app），
     * 避免父目录下另有同名 reactor-tool 时误写。
     */
    public static Path resolveRepoRoot() {
        Path start = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (Path cursor = start; cursor != null; cursor = cursor.getParent()) {
            Path nested = cursor.resolve(REPO_DIR_NAME);
            if (isMonorepoRoot(nested)) {
                return nested.normalize();
            }
            if (isMonorepoRoot(cursor)) {
                return cursor.normalize();
            }
        }
        for (Path cursor = start; cursor != null; cursor = cursor.getParent()) {
            if (Files.isDirectory(cursor.resolve(REACTOR_TOOL))) {
                return cursor.normalize();
            }
        }
        return start;
    }

    public static Path skillOutputSessionRoot(String sessionId) {
        String safe = (sessionId == null || sessionId.isBlank()) ? "anonymous" : sessionId;
        safe = safe.replaceAll("[\\\\/:*?\"<>|]", "_");
        return resolveRepoRoot().resolve(REACTOR_TOOL).resolve("skilloutput").resolve(safe).normalize();
    }

    /**
     * bash / code_execution 推沙箱用的本地根：优先会话已解析的 workspaceRoot
     *（与 workspace_write 同一目录），未设置时回退 skilloutput/{sessionId}。
     */
    public static Path resolveSandboxRoot(String configuredRoot, String sessionId) {
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            return Path.of(configuredRoot.trim()).toAbsolutePath().normalize();
        }
        return skillOutputSessionRoot(sessionId);
    }

    private static boolean isMonorepoRoot(Path path) {
        return path != null
                && Files.isDirectory(path)
                && Files.isDirectory(path.resolve(REACTOR_TOOL))
                && Files.isDirectory(path.resolve(APP_MODULE));
    }
}

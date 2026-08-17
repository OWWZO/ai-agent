package org.wwz.ai.domain.agent.runtime.tool.workspace;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDefinition;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 解析并初始化会话工作区；路径支持虚拟 {@code skills/} → runtime skill 库。
 */
@Component
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRuntimeOptions workspaceRuntimeOptions;
    private final WorkspacePathGuard workspacePathGuard;
    private final SkillRegistry skillRegistry;
    private final SkillVirtualPaths skillVirtualPaths;

    public boolean isEnabled() {
        return workspaceRuntimeOptions != null && workspaceRuntimeOptions.isEnabled();
    }

    public Path resolveAndEnsureRoot(String sessionId) {
        Path root = resolveRoot(sessionId);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new WorkspaceAccessException("failed to create workspace root: " + root, e);
        }
        return root;
    }

    public Path resolveRoot(String sessionId) {
        if (!isEnabled()) {
            throw new WorkspaceAccessException("workspace tools are disabled");
        }
        String template = workspaceRuntimeOptions.getRootTemplate();
        if (StringUtils.isBlank(template) || isDefaultSkillOutputTemplate(template)) {
            return WorkspacePaths.skillOutputSessionRoot(sessionId);
        }
        String expanded = expandTemplate(template, sessionId);
        return Path.of(expanded).toAbsolutePath().normalize();
    }

    /**
     * 读/list/glob/grep：虚拟 skills/ 直达库；否则 workspace + 已注册 skill 真实路径。
     */
    public Path resolveAllowedPath(Path workspaceRoot, String rawPath) {
        Optional<Path> virtual = skillVirtualPaths == null
                ? Optional.empty()
                : skillVirtualPaths.tryResolve(rawPath);
        if (virtual.isPresent()) {
            return virtual.get();
        }
        Path candidate = resolveCandidate(workspaceRoot, rawPath);
        return workspacePathGuard.ensureUnderAnyRoot(listReadableRoots(workspaceRoot), candidate);
    }

    /**
     * 写/edit：虚拟 skills/ 可写全局库；否则仅会话 workspace。
     */
    public Path resolveWritablePath(Path workspaceRoot, String rawPath) {
        Optional<Path> virtual = skillVirtualPaths == null
                ? Optional.empty()
                : skillVirtualPaths.tryResolve(rawPath);
        if (virtual.isPresent()) {
            return virtual.get();
        }
        Path candidate = resolveCandidate(workspaceRoot, rawPath);
        return workspacePathGuard.ensureUnderRoot(workspaceRoot, candidate);
    }

    public List<Path> listReadableRoots(Path workspaceRoot) {
        Set<Path> roots = new LinkedHashSet<>();
        if (workspaceRoot != null) {
            roots.add(workspaceRoot.toAbsolutePath().normalize());
        }
        Path lib = skillVirtualPaths == null ? null : skillVirtualPaths.libraryRootOrNull();
        if (lib != null) {
            roots.add(lib);
        }
        if (skillRegistry != null && skillRegistry.isEnabled()) {
            for (SkillDefinition skill : skillRegistry.listSkills()) {
                if (skill == null || skill.getBasePath() == null) {
                    continue;
                }
                roots.add(skill.getBasePath().toAbsolutePath().normalize());
            }
        }
        return new ArrayList<>(roots);
    }

    /**
     * 绝对路径 → Agent 可见的虚拟/相对路径。
     * 库内 → {@code skills/...}；会话工作区内 → 相对 cwd；其它 → 仅文件名（不暴露宿主路径）。
     */
    public String toAgentVisiblePath(Path workspaceRoot, Path absolutePath) {
        if (absolutePath == null) {
            return null;
        }
        Path abs = absolutePath.toAbsolutePath().normalize();
        Path lib = skillVirtualPaths == null ? null : skillVirtualPaths.libraryRootOrNull();
        if (lib != null && abs.startsWith(lib)) {
            String rel = lib.relativize(abs).toString().replace('\\', '/');
            return rel.isEmpty() ? SkillVirtualPaths.PREFIX : SkillVirtualPaths.PREFIX_SLASH + rel;
        }
        if (workspaceRoot != null) {
            Path ws = workspaceRoot.toAbsolutePath().normalize();
            if (abs.startsWith(ws)) {
                String rel = ws.relativize(abs).toString().replace('\\', '/');
                return rel.isEmpty() ? "." : rel;
            }
        }
        Path name = abs.getFileName();
        return name == null ? "path" : name.toString();
    }

    /** 错误/日志串脱敏：去掉 Windows/Unix 绝对路径片段。 */
    public static String redactHostPaths(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        out = out.replaceAll("(?i)[a-z]:\\\\[^\\s\"']+", "<host-path>");
        out = out.replaceAll("(?i)/(?:Users|home|var|tmp|opt|mnt|data|private)(?:/[^\\s\"']+)+", "<host-path>");
        return out;
    }

    private Path resolveCandidate(Path workspaceRoot, String rawPath) {
        if (StringUtils.isBlank(rawPath)) {
            throw new WorkspaceAccessException("path is required");
        }
        Path candidate = Path.of(rawPath.trim());
        if (!candidate.isAbsolute()) {
            if (workspaceRoot == null) {
                throw new WorkspaceAccessException("workspace root is not configured");
            }
            candidate = workspaceRoot.resolve(candidate);
        }
        return candidate;
    }

    private boolean isDefaultSkillOutputTemplate(String template) {
        String normalized = template.replace('\\', '/');
        return normalized.contains("reactor-tool/skilloutput")
                || normalized.contains("{repoRoot}")
                || normalized.contains("${user.dir}");
    }

    private String expandTemplate(String template, String sessionId) {
        String safeSessionId = StringUtils.defaultIfBlank(sessionId, "anonymous")
                .replaceAll("[\\\\/:*?\"<>|]", "_");
        String repoRoot = WorkspacePaths.resolveRepoRoot().toString();
        return template
                .replace("{sessionId}", safeSessionId)
                .replace("{repoRoot}", repoRoot)
                .replace("${user.dir}", repoRoot)
                .replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir", "."));
    }
}

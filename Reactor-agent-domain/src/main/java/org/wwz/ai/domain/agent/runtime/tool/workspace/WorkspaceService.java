package org.wwz.ai.domain.agent.runtime.tool.workspace;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDefinition;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 解析并初始化会话工作区根目录；路径校验合并 skill 可读根。
 */
@Component
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRuntimeOptions workspaceRuntimeOptions;
    private final WorkspacePathGuard workspacePathGuard;
    private final SkillRegistry skillRegistry;

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
        if (StringUtils.isBlank(template)) {
            template = System.getProperty("user.dir", ".") + "/reactor-tool/skilloutput/{sessionId}";
        }
        String expanded = expandTemplate(template, sessionId);
        return Path.of(expanded).toAbsolutePath().normalize();
    }

    /**
     * 读/list/glob/grep：允许 workspace 与已注册 skill 目录。
     * 相对路径始终相对 workspaceRoot 解析（cwd 语义）。
     */
    public Path resolveAllowedPath(Path workspaceRoot, String rawPath) {
        Path candidate = resolveCandidate(workspaceRoot, rawPath);
        return workspacePathGuard.ensureUnderAnyRoot(listReadableRoots(workspaceRoot), candidate);
    }

    /**
     * 写：只允许 workspace 根内。
     */
    public Path resolveWritablePath(Path workspaceRoot, String rawPath) {
        Path candidate = resolveCandidate(workspaceRoot, rawPath);
        return workspacePathGuard.ensureUnderRoot(workspaceRoot, candidate);
    }

    public List<Path> listReadableRoots(Path workspaceRoot) {
        Set<Path> roots = new LinkedHashSet<>();
        if (workspaceRoot != null) {
            roots.add(workspaceRoot.toAbsolutePath().normalize());
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

    private String expandTemplate(String template, String sessionId) {
        String safeSessionId = StringUtils.defaultIfBlank(sessionId, "anonymous")
                .replaceAll("[\\\\/:*?\"<>|]", "_");
        String expanded = template
                .replace("{sessionId}", safeSessionId)
                .replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir", "."));
        if (expanded.contains("${user.dir}")) {
            expanded = expanded.replace("${user.dir}", System.getProperty("user.dir", "."));
        }
        return expanded;
    }
}

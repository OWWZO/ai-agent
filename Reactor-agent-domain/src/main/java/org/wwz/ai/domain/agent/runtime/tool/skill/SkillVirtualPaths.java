package org.wwz.ai.domain.agent.runtime.tool.skill;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceAccessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 虚拟路径契约：对外统一 {@code skills/<name>/...}，后端映射到 skill 库根目录
 * （{@code autobots.autoagent.skill.directories} 第一项，通常为 {@code runtime/skills}）。
 *
 * <p>会话物理工作区不需要出现 skill 实体文件；workspace 工具经本类解析后直接读写全局库。
 */
@Component
public class SkillVirtualPaths {

    public static final String PREFIX = "skills";
    public static final String PREFIX_SLASH = "skills/";

    private final SkillRuntimeOptions skillRuntimeOptions;

    public SkillVirtualPaths(SkillRuntimeOptions skillRuntimeOptions) {
        this.skillRuntimeOptions = skillRuntimeOptions;
    }

    public boolean isEnabled() {
        return skillRuntimeOptions != null
                && skillRuntimeOptions.isEnabled()
                && skillRuntimeOptions.getDirectories() != null
                && !skillRuntimeOptions.getDirectories().isEmpty();
    }

    /**
     * 若 rawPath 是虚拟 skill 路径，返回库内绝对路径；否则 empty。
     * 支持：{@code skills}、{@code skills/foo}、{@code skills/foo/scripts/a.py}、
     * {@code ./skills/...}
     */
    public Optional<Path> tryResolve(String rawPath) {
        if (!isEnabled() || StringUtils.isBlank(rawPath)) {
            return Optional.empty();
        }
        String normalized = rawPath.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/")) {
            // 绝对路径不走虚拟契约（可能是本机真实 skill 路径，由其它根处理）
            return Optional.empty();
        }
        if (!normalized.equals(PREFIX) && !normalized.startsWith(PREFIX_SLASH)) {
            return Optional.empty();
        }
        String rest = normalized.equals(PREFIX) ? "" : normalized.substring(PREFIX_SLASH.length());
        Path libRoot = requireLibraryRoot();
        if (rest.isEmpty()) {
            return Optional.of(libRoot);
        }
        if (!SkillPackageParser.isSafeRelativePath(rest)) {
            throw new WorkspaceAccessException("非法 skill 虚拟路径: " + rawPath);
        }
        Path target = libRoot.resolve(rest).normalize();
        if (!target.startsWith(libRoot)) {
            throw new WorkspaceAccessException("skill 路径逃逸库根: " + rawPath);
        }
        return Optional.of(target);
    }

    /** skill 库根（不存在则创建）。 */
    public Path requireLibraryRoot() {
        if (!isEnabled()) {
            throw new WorkspaceAccessException("skill 机制未启用或未配置 directories");
        }
        Path root = Path.of(skillRuntimeOptions.getDirectories().get(0).trim()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new WorkspaceAccessException("无法创建 skill 库根: " + root, e);
        }
        return root;
    }

    public Path libraryRootOrNull() {
        if (!isEnabled()) {
            return null;
        }
        try {
            return requireLibraryRoot();
        } catch (Exception e) {
            return null;
        }
    }

    /** 库下直接子目录（每个子目录视为一个 skill 包）。 */
    public List<Path> listSkillPackageDirs() {
        Path root = libraryRootOrNull();
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}

package org.wwz.ai.domain.agent.runtime.tool.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 沙箱侧 skill 灌入与回写（仅 {@code skills/**}）。
 *
 * <ul>
 *   <li>会话物理工作区日常不需要 skill 实体；workspace 工具走虚拟路径直达 runtime 库</li>
 *   <li>bash 执行前：把库中 skill 包灌入沙箱 cwd 下 {@code skills/&lt;name&gt;/}</li>
 *   <li>bash 执行后：只把沙箱 {@code skills/**} 变更回写到 runtime 库；不立刻 refresh 注册表</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillMaterializer {

    private final SkillVirtualPaths skillVirtualPaths;
    private final SkillRuntimeLayout layout;
    private final SkillRuntimeOptions skillRuntimeOptions;

    /**
     * 执行前：将 skill 库整包灌入沙箱工作区 {@code skills/}（每次强制覆盖，保证见最新库文件）。
     *
     * @return 灌入的 skill 名列表
     */
    public List<String> materializeForSandbox(Path sandboxWorkspace, Collection<String> disabledNames) {
        if (sandboxWorkspace == null || skillVirtualPaths == null || !skillVirtualPaths.isEnabled()) {
            return List.of();
        }
        Path sandboxRoot = sandboxWorkspace.toAbsolutePath().normalize();
        Path libRoot = skillVirtualPaths.libraryRootOrNull();
        if (libRoot == null) {
            return List.of();
        }
        Set<String> disabled = disabledNames == null ? Set.of() : new LinkedHashSet<>(disabledNames);
        List<String> names = new ArrayList<>();
        try {
            Path sandboxSkills = sandboxRoot.resolve(SkillRuntimeLayout.RELATIVE_ROOT);
            if (Files.exists(sandboxSkills)) {
                deleteRecursively(sandboxSkills);
            }
            Files.createDirectories(sandboxSkills);
            for (Path skillDir : skillVirtualPaths.listSkillPackageDirs()) {
                String name = skillDir.getFileName().toString();
                if (disabled.contains(name)) {
                    continue;
                }
                Path target = sandboxSkills.resolve(layout.segmentOf(name));
                copyTree(skillDir, target);
                names.add(name);
            }
            log.info("sandbox skill materialize ok root={} count={}", sandboxRoot, names.size());
        } catch (Exception e) {
            log.warn("sandbox skill materialize failed workspace={}", sandboxRoot, e);
        }
        return names;
    }

    /**
     * 执行后：沙箱 {@code skills/**} → runtime skill 库。不调用 registry.refresh（本轮仍用旧缓存）。
     *
     * @return 回写的 skill 名
     */
    public List<String> syncBackSkillsOnly(Path sandboxWorkspace) {
        if (sandboxWorkspace == null || skillVirtualPaths == null || !skillVirtualPaths.isEnabled()) {
            return List.of();
        }
        Path sandboxSkills = sandboxWorkspace.toAbsolutePath().normalize()
                .resolve(SkillRuntimeLayout.RELATIVE_ROOT);
        Path libRoot = skillVirtualPaths.libraryRootOrNull();
        if (libRoot == null || !Files.isDirectory(sandboxSkills)) {
            return List.of();
        }
        List<String> synced = new ArrayList<>();
        try (var stream = Files.list(sandboxSkills)) {
            for (Path skillDir : stream.filter(Files::isDirectory).toList()) {
                String name = skillDir.getFileName().toString();
                if (name.startsWith(".")) {
                    continue;
                }
                Path target = libRoot.resolve(name).normalize();
                if (!target.startsWith(libRoot)) {
                    continue;
                }
                if (Files.exists(target)) {
                    deleteRecursively(target);
                }
                copyTree(skillDir, target);
                synced.add(name);
            }
            if (!synced.isEmpty()) {
                log.info("sandbox skill sync-back ok lib={} skills={} (registry not refreshed)",
                        libRoot, synced);
            }
        } catch (Exception e) {
            log.warn("sandbox skill sync-back failed sandbox={}", sandboxSkills, e);
        }
        return synced;
    }

    /** 执行结束后清理沙箱内 skills/，避免会话工作区长期残留 skill 实体。 */
    public void cleanupSandboxSkills(Path sandboxWorkspace) {
        if (sandboxWorkspace == null) {
            return;
        }
        Path sandboxSkills = sandboxWorkspace.toAbsolutePath().normalize()
                .resolve(SkillRuntimeLayout.RELATIVE_ROOT);
        try {
            if (Files.exists(sandboxSkills)) {
                deleteRecursively(sandboxSkills);
            }
        } catch (Exception e) {
            log.debug("cleanup sandbox skills failed: {}", e.getMessage());
        }
    }

    /**
     * @deprecated 旧会话 materialize API；请用 {@link #materializeForSandbox}
     */
    @Deprecated
    public List<String> ensureMaterialized(Path workspaceRoot,
                                           Collection<String> skillNames,
                                           Collection<String> disabledNames) {
        return materializeForSandbox(workspaceRoot, disabledNames);
    }

    @Deprecated
    public List<String> rematerialize(Path workspaceRoot,
                                      Collection<String> skillNames,
                                      Collection<String> disabledNames) {
        return materializeForSandbox(workspaceRoot, disabledNames);
    }

    public void evict(Path workspaceRoot) {
        cleanupSandboxSkills(workspaceRoot);
    }

    private void copyTree(Path sourceRoot, Path targetRoot) throws IOException {
        Files.createDirectories(targetRoot.getParent());
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceRoot.relativize(dir);
                Path target = relative.toString().isEmpty() ? targetRoot : targetRoot.resolve(relative);
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceRoot.relativize(file);
                Path target = targetRoot.resolve(relative);
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

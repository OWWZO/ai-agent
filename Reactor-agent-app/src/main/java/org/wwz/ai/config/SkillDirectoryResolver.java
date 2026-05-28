package org.wwz.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 解析 skill 根目录，并在启动目录落在模块内时回退到仓库根 runtime/skills。
 */
@Slf4j
@Component
public class SkillDirectoryResolver {

    private static final String RUNTIME_DIRECTORY_NAME = "runtime";
    private static final String SKILLS_DIRECTORY_NAME = "skills";

    private final Path workingDirectory;
    private final List<Path> anchorDirectories;

    public SkillDirectoryResolver() {
        this(Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize(), detectAnchorDirectories());
    }

    public SkillDirectoryResolver(Path workingDirectory) {
        this(workingDirectory, List.of(workingDirectory));
    }

    public SkillDirectoryResolver(Path workingDirectory, List<Path> anchorDirectories) {
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
        LinkedHashSet<Path> anchors = new LinkedHashSet<>();
        anchors.add(this.workingDirectory);
        if (anchorDirectories != null) {
            anchorDirectories.stream()
                    .filter(path -> path != null)
                    .map(path -> path.toAbsolutePath().normalize())
                    .forEach(anchors::add);
        }
        this.anchorDirectories = List.copyOf(anchors);
    }

    public List<String> resolve(List<String> configuredDirectories) {
        if (configuredDirectories == null || configuredDirectories.isEmpty()) {
            return List.of();
        }

        Set<String> resolvedDirectories = new LinkedHashSet<>();
        for (String configuredDirectory : configuredDirectories) {
            if (configuredDirectory == null || configuredDirectory.isBlank()) {
                continue;
            }

            Path normalizedDirectory = Path.of(configuredDirectory).toAbsolutePath().normalize();
            if (Files.isDirectory(normalizedDirectory)) {
                resolvedDirectories.add(normalizedDirectory.toString());
                continue;
            }

            Path fallbackDirectory = resolveRuntimeSkillsFallback(normalizedDirectory);
            if (fallbackDirectory != null) {
                log.info("skill directory fallback applied, configured={}, resolved={}",
                        normalizedDirectory, fallbackDirectory);
                resolvedDirectories.add(fallbackDirectory.toString());
            } else {
                log.warn("skill directory unresolved, configured={}, anchors={}",
                        normalizedDirectory, anchorDirectories);
            }
        }
        return new ArrayList<>(resolvedDirectories);
    }

    private Path resolveRuntimeSkillsFallback(Path configuredDirectory) {
        if (!looksLikeRuntimeSkillsDirectory(configuredDirectory)) {
            return null;
        }

        for (Path anchorDirectory : anchorDirectories) {
            Path current = anchorDirectory;
            while (current != null) {
                Path candidate = current.resolve(RUNTIME_DIRECTORY_NAME).resolve(SKILLS_DIRECTORY_NAME).normalize();
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
                current = current.getParent();
            }
        }
        return null;
    }

    private boolean looksLikeRuntimeSkillsDirectory(Path directory) {
        int nameCount = directory.getNameCount();
        if (nameCount < 2) {
            return false;
        }

        String lastName = directory.getName(nameCount - 1).toString();
        String parentName = directory.getName(nameCount - 2).toString();
        return SKILLS_DIRECTORY_NAME.equalsIgnoreCase(lastName)
                && RUNTIME_DIRECTORY_NAME.equalsIgnoreCase(parentName);
    }

    private static List<Path> detectAnchorDirectories() {
        LinkedHashSet<Path> anchors = new LinkedHashSet<>();
        anchors.add(Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize());
        anchors.addAll(resolveClassPathAnchors());
        return new ArrayList<>(anchors);
    }

    private static List<Path> resolveClassPathAnchors() {
        try {
            URL location = SkillDirectoryResolver.class.getProtectionDomain().getCodeSource() == null
                    ? null
                    : SkillDirectoryResolver.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return Collections.emptyList();
            }
            Path locationPath = Path.of(location.toURI()).toAbsolutePath().normalize();
            if (Files.isDirectory(locationPath)) {
                return List.of(locationPath);
            }
            Path parent = locationPath.getParent();
            return parent == null ? Collections.emptyList() : List.of(parent);
        } catch (URISyntaxException | IllegalArgumentException e) {
            log.warn("failed to resolve classpath anchors for skill directory fallback", e);
            return Collections.emptyList();
        }
    }
}

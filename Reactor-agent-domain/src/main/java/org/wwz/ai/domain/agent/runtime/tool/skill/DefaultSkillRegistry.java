package org.wwz.ai.domain.agent.runtime.tool.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Skill 注册中心 / Catalog：扫描时只缓存 metadata 与文件清单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSkillRegistry implements SkillRegistry, SkillCatalog {

    private static final Map<String, String> KIND_BY_DIR = Map.of(
            "references", "reference",
            "templates", "template",
            "scripts", "script",
            "assets", "asset",
            "examples", "example"
    );

    private final SkillRuntimeOptions skillRuntimeOptions;
    private final SkillMarkdownParser skillMarkdownParser;
    private final SkillScriptDiscoverer skillScriptDiscoverer;
    private final SkillPathGuard skillPathGuard;

    private volatile Map<String, SkillDescriptor> byId = Collections.emptyMap();
    private volatile Map<String, SkillDescriptor> byUniqueName = Collections.emptyMap();
    private volatile Map<String, List<SkillDescriptor>> byName = Collections.emptyMap();
    private volatile List<SkillDescriptor> descriptors = Collections.emptyList();
    private volatile List<Path> skillRootDirectories = Collections.emptyList();
    private final AtomicLong catalogVersion = new AtomicLong(0);

    @Override
    public synchronized void refresh() {
        List<Path> resolvedRootDirectories = resolveRootDirectories();
        this.skillRootDirectories = Collections.unmodifiableList(resolvedRootDirectories);

        if (!skillRuntimeOptions.isEnabled()) {
            replaceSnapshot(List.of(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
            log.info("skill registry disabled, skip loading skills");
            return;
        }

        List<SkillDescriptor> loaded = new ArrayList<>();
        for (Path rootDirectory : resolvedRootDirectories) {
            loadSkillsFromRoot(rootDirectory, loaded);
        }
        Map<String, SkillDescriptor> idIndex = new LinkedHashMap<>();
        Map<String, List<SkillDescriptor>> nameIndex = new LinkedHashMap<>();
        Map<String, SkillDescriptor> uniqueNameIndex = new LinkedHashMap<>();
        for (SkillDescriptor descriptor : loaded) {
            idIndex.put(descriptor.getRef().id(), descriptor);
            nameIndex.computeIfAbsent(descriptor.getName(), key -> new ArrayList<>()).add(descriptor);
            uniqueNameIndex.putIfAbsent(descriptor.getName(), descriptor);
        }
        replaceSnapshot(loaded, idIndex, nameIndex, uniqueNameIndex);
        log.info("skill catalog refreshed, version={}, roots={}, skills={}",
                catalogVersion.get(), skillRootDirectories, uniqueNameIndex.keySet());
    }

    @Override
    public boolean isEnabled() {
        return skillRuntimeOptions.isEnabled() && !skillRootDirectories.isEmpty();
    }

    @Override
    public Collection<SkillDefinition> listSkills() {
        List<SkillDefinition> definitions = new ArrayList<>();
        for (SkillDescriptor descriptor : byUniqueName.values()) {
            definitions.add(toDefinition(descriptor));
        }
        return definitions;
    }

    @Override
    public Optional<SkillDefinition> findSkill(String skillName) {
        return find(skillName).map(this::toDefinition);
    }

    @Override
    public SkillDefinition getRequiredSkill(String skillName) {
        return toDefinition(getRequired(skillName));
    }

    @Override
    public Path assertPathAllowed(Path candidatePath) {
        Path normalizedCandidatePath = candidatePath.toAbsolutePath().normalize();
        for (SkillDescriptor descriptor : descriptors) {
            Path skillBasePath = descriptor.getBasePath().toAbsolutePath().normalize();
            if (normalizedCandidatePath.startsWith(skillBasePath)) {
                return skillPathGuard.ensureUnderRoot(skillBasePath, normalizedCandidatePath);
            }
        }
        throw new SkillLoadException("path is outside registered skill directories: " + normalizedCandidatePath);
    }

    @Override
    public String buildSkillDescription() {
        if (descriptors.isEmpty()) {
            return "当前没有可用 skill。";
        }
        return "当前可用 skills 见 system prompt 中的 <available_skills>。使用 skill_view 加载正文。";
    }

    @Override
    public List<SkillDescriptor> list() {
        return descriptors;
    }

    @Override
    public List<SkillSearchHit> search(SkillQuery query) {
        return SkillCatalogSearch.search(descriptors, query);
    }

    @Override
    public SkillRef resolve(String identifier) {
        return getRequired(identifier).getRef();
    }

    @Override
    public Optional<SkillDescriptor> find(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String trimmed = identifier.trim();
        SkillDescriptor byExactId = byId.get(trimmed);
        if (byExactId != null) {
            return Optional.of(byExactId);
        }
        if (trimmed.contains("/")) {
            String slashPath = trimmed.replace('\\', '/');
            for (SkillDescriptor descriptor : descriptors) {
                if (slashPath.equals(descriptor.getRef().relativePath())) {
                    return Optional.of(descriptor);
                }
            }
        }
        if (trimmed.contains(":")) {
            int colon = trimmed.indexOf(':');
            String source = trimmed.substring(0, colon);
            String rest = trimmed.substring(colon + 1);
            if (!source.isBlank() && !rest.isBlank() && !rest.contains("\\") && rest.indexOf(':') < 0) {
                for (SkillDescriptor descriptor : descriptors) {
                    if (source.equals(descriptor.getSource())
                            && (rest.equals(descriptor.getName()) || rest.equals(descriptor.getRef().relativePath()))) {
                        return Optional.of(descriptor);
                    }
                }
                String categorized = rest.replace(':', '/');
                for (SkillDescriptor descriptor : descriptors) {
                    if (categorized.equals(descriptor.getRef().relativePath())) {
                        return Optional.of(descriptor);
                    }
                }
            }
        }
        List<SkillDescriptor> sameName = byName.get(trimmed);
        if (sameName != null && sameName.size() == 1) {
            return Optional.of(sameName.get(0));
        }
        if (sameName != null && sameName.size() > 1) {
            throw ambiguous(trimmed, sameName);
        }
        return Optional.empty();
    }

    @Override
    public SkillDescriptor getRequired(String identifier) {
        try {
            return find(identifier).orElseThrow(() -> new SkillLoadException("Skill not found: " + identifier));
        } catch (SkillLoadException e) {
            throw e;
        }
    }

    @Override
    public String version() {
        return String.valueOf(catalogVersion.get());
    }

    private void replaceSnapshot(List<SkillDescriptor> loaded,
                                 Map<String, SkillDescriptor> idIndex,
                                 Map<String, List<SkillDescriptor>> nameIndex,
                                 Map<String, SkillDescriptor> uniqueNameIndex) {
        this.descriptors = Collections.unmodifiableList(new ArrayList<>(loaded));
        this.byId = Collections.unmodifiableMap(idIndex);
        this.byName = unmodifiableNameIndex(nameIndex);
        this.byUniqueName = Collections.unmodifiableMap(uniqueNameIndex);
        catalogVersion.incrementAndGet();
    }

    private Map<String, List<SkillDescriptor>> unmodifiableNameIndex(Map<String, List<SkillDescriptor>> nameIndex) {
        Map<String, List<SkillDescriptor>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<SkillDescriptor>> entry : nameIndex.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private List<Path> resolveRootDirectories() {
        if (skillRuntimeOptions.getDirectories() == null || skillRuntimeOptions.getDirectories().isEmpty()) {
            return Collections.emptyList();
        }
        return skillRuntimeOptions.getDirectories().stream()
                .filter(directory -> directory != null && !directory.isBlank())
                .map(directory -> Path.of(directory).toAbsolutePath().normalize())
                .filter(this::isExistingDirectory)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
    }

    private boolean isExistingDirectory(Path directory) {
        if (!Files.exists(directory)) {
            log.warn("skill root directory does not exist, skip loading: {}", directory);
            return false;
        }
        if (!Files.isDirectory(directory)) {
            log.warn("skill root path is not a directory, skip loading: {}", directory);
            return false;
        }
        return true;
    }

    private void loadSkillsFromRoot(Path rootDirectory, List<SkillDescriptor> loadedSkills) {
        List<Path> skillDirectories = findSkillDirectories(rootDirectory);
        for (Path skillDirectory : skillDirectories) {
            try {
                String relativePath = toRelativePath(rootDirectory, skillDirectory);
                SkillDescriptor descriptor = skillMarkdownParser.parseMetadata(
                        skillDirectory, SkillRef.SOURCE_BUILTIN, relativePath);
                if (!matchesCurrentPlatform(descriptor.getFrontMatter())) {
                    log.info("skip skill {} on current platform", descriptor.getName());
                    continue;
                }
                descriptor.setFiles(scanLinkedFiles(skillDirectory));
                loadedSkills.add(descriptor);
            } catch (SkillLoadException e) {
                log.warn("skip invalid skill directory {}, reason: {}", skillDirectory, e.getMessage());
            }
        }
    }

    private List<Path> findSkillDirectories(Path rootDirectory) {
        try (var pathStream = Files.list(rootDirectory)) {
            return pathStream
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve("SKILL.md")))
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString()))
                    .toList();
        } catch (IOException e) {
            throw new SkillLoadException("failed to scan skill root directory: " + rootDirectory, e);
        }
    }

    private List<SkillFileDescriptor> scanLinkedFiles(Path skillDirectory) {
        Path base = skillDirectory.toAbsolutePath().normalize();
        List<SkillFileDescriptor> files = new ArrayList<>();
        try (var pathStream = Files.walk(base)) {
            pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> !"SKILL.md".equals(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString()))
                    .forEach(path -> {
                        try {
                            Path allowed = skillPathGuard.ensureUnderRoot(base, path);
                            String relative = base.relativize(allowed).toString().replace('\\', '/');
                            files.add(new SkillFileDescriptor(
                                    relative,
                                    kindOf(relative),
                                    "",
                                    Files.size(allowed)));
                        } catch (Exception e) {
                            log.debug("skip skill file {}: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new SkillLoadException("failed to scan skill files under " + skillDirectory, e);
        }
        return files;
    }

    private String kindOf(String relativePath) {
        int slash = relativePath.indexOf('/');
        String dir = slash < 0 ? "" : relativePath.substring(0, slash);
        return KIND_BY_DIR.getOrDefault(dir, "other");
    }

    private String toRelativePath(Path rootDirectory, Path skillDirectory) {
        return rootDirectory.toAbsolutePath().normalize()
                .relativize(skillDirectory.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private SkillDefinition toDefinition(SkillDescriptor descriptor) {
        return SkillDefinition.builder()
                .name(descriptor.getName())
                .description(descriptor.getDescription())
                .basePath(descriptor.getBasePath())
                .content(null)
                .frontMatter(descriptor.getFrontMatter())
                .scripts(Collections.emptyMap())
                .build();
    }

    private SkillLoadException ambiguous(String identifier, List<SkillDescriptor> matches) {
        List<String> ids = matches.stream().map(item -> item.getRef().id()).toList();
        return new SkillLoadException(
                "Ambiguous skill name '" + identifier + "': " + matches.size()
                        + " skills match. Use a qualified id such as " + ids.get(0)
                        + ". matches=" + ids);
    }

    static boolean matchesCurrentPlatform(Map<String, Object> frontMatter) {
        if (frontMatter == null) {
            return true;
        }
        Object raw = frontMatter.get("platforms");
        if (raw == null) {
            return true;
        }
        List<String> platforms = new ArrayList<>();
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    platforms.add(String.valueOf(item).trim().toLowerCase(Locale.ROOT));
                }
            }
        } else {
            String text = String.valueOf(raw).trim();
            if (!text.isBlank()) {
                for (String part : text.split("[,\\s]+")) {
                    if (!part.isBlank()) {
                        platforms.add(part.trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        if (platforms.isEmpty()) {
            return true;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        for (String platform : platforms) {
            if (platform.contains("win") && os.contains("win")) {
                return true;
            }
            if (platform.contains("linux") && os.contains("linux")) {
                return true;
            }
            if ((platform.contains("mac") || platform.contains("darwin")) && os.contains("mac")) {
                return true;
            }
        }
        return false;
    }
}

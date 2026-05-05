package org.wwz.ai.domain.agent.runtime.tool.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 默认 skill 注册中心实现，负责扫描目录、缓存结果并做路径校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSkillRegistry implements SkillRegistry {

    private final SkillRuntimeOptions skillRuntimeOptions;
    private final SkillMarkdownParser skillMarkdownParser;
    private final SkillScriptDiscoverer skillScriptDiscoverer;
    private final SkillPathGuard skillPathGuard;

    private volatile Map<String, SkillDefinition> skillCache = Collections.emptyMap();
    private volatile List<Path> skillRootDirectories = Collections.emptyList();

    @Override
    public synchronized void refresh() {
        List<Path> resolvedRootDirectories = resolveRootDirectories();
        this.skillRootDirectories = Collections.unmodifiableList(resolvedRootDirectories);

        if (!skillRuntimeOptions.isEnabled()) {
            this.skillCache = Collections.emptyMap();
            log.info("skill registry disabled, skip loading skills");
            return;
        }

        Map<String, SkillDefinition> loadedSkills = new LinkedHashMap<>();
        for (Path rootDirectory : resolvedRootDirectories) {
            loadSkillsFromRoot(rootDirectory, loadedSkills);
        }
        this.skillCache = Collections.unmodifiableMap(loadedSkills);
        log.info("skill registry refreshed, roots={}, skills={}", skillRootDirectories, loadedSkills.keySet());
    }

    @Override
    public boolean isEnabled() {
        return skillRuntimeOptions.isEnabled() && !skillRootDirectories.isEmpty();
    }

    @Override
    public Collection<SkillDefinition> listSkills() {
        return skillCache.values();
    }

    @Override
    public Optional<SkillDefinition> findSkill(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(skillCache.get(skillName.trim()));
    }

    @Override
    public SkillDefinition getRequiredSkill(String skillName) {
        return findSkill(skillName)
                .orElseThrow(() -> new SkillLoadException("Skill not found: " + skillName));
    }

    @Override
    public SkillScriptDefinition getRequiredScript(String skillName, String scriptName) {
        SkillDefinition skillDefinition = getRequiredSkill(skillName);
        SkillScriptDefinition scriptDefinition = skillDefinition.getScripts().get(scriptName);
        if (scriptDefinition == null) {
            throw new SkillLoadException("Script not found: " + skillName + "/" + scriptName);
        }
        return scriptDefinition;
    }

    @Override
    public Path assertPathAllowed(Path candidatePath) {
        Path normalizedCandidatePath = candidatePath.toAbsolutePath().normalize();
        for (SkillDefinition skillDefinition : skillCache.values()) {
            Path skillBasePath = skillDefinition.getBasePath().toAbsolutePath().normalize();
            if (normalizedCandidatePath.startsWith(skillBasePath)) {
                return skillPathGuard.ensureUnderRoot(skillBasePath, normalizedCandidatePath);
            }
        }
        throw new SkillLoadException("path is outside registered skill directories: " + normalizedCandidatePath);
    }

    @Override
    public String buildSkillDescription() {
        if (skillCache.isEmpty()) {
            return "当前没有可用 skill。";
        }
        StringBuilder descriptionBuilder = new StringBuilder();
        descriptionBuilder.append("当前可用 skills：");
        for (SkillDefinition skillDefinition : skillCache.values()) {
            descriptionBuilder.append("\n- ")
                    .append(skillDefinition.getName())
                    .append(": ")
                    .append(skillDefinition.getDescription());
        }
        return descriptionBuilder.toString();
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

    private void loadSkillsFromRoot(Path rootDirectory, Map<String, SkillDefinition> loadedSkills) {
        List<Path> skillDirectories = findSkillDirectories(rootDirectory);
        for (Path skillDirectory : skillDirectories) {
            try {
                SkillDefinition skillDefinition = skillMarkdownParser.parse(skillDirectory);
                if (loadedSkills.containsKey(skillDefinition.getName())) {
                    throw new SkillLoadException("duplicate skill name detected: " + skillDefinition.getName());
                }
                skillDefinition.setScripts(skillScriptDiscoverer.discover(skillDirectory));
                loadedSkills.put(skillDefinition.getName(), skillDefinition);
            } catch (SkillLoadException e) {
                // 重名冲突必须显式抛出，避免注册结果不确定。
                if (e.getMessage() != null && e.getMessage().startsWith("duplicate skill name detected")) {
                    throw e;
                }
                log.warn("skip invalid skill directory {}, reason: {}", skillDirectory, e.getMessage());
            }
        }
    }

    private List<Path> findSkillDirectories(Path rootDirectory) {
        try (var pathStream = Files.walk(rootDirectory)) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> "SKILL.md".equals(path.getFileName().toString()))
                    .map(Path::getParent)
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString()))
                    .toList();
        } catch (IOException e) {
            throw new SkillLoadException("failed to scan skill root directory: " + rootDirectory, e);
        }
    }
}

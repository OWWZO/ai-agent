package org.wwz.ai.domain.agent.runtime.tool.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 发现 skill 下注册脚本，支持 scripts/ 自动扫描与 scripts.yaml 增强配置。
 */
@Component
@RequiredArgsConstructor
public class SkillScriptDiscoverer {

    private static final Map<String, String> EXTENSION_RUNTIME_MAP = Map.of(
            ".py", "python",
            ".js", "node",
            ".mjs", "node",
            ".cjs", "node",
            ".sh", "shell",
            ".ps1", "powershell",
            ".bat", "bat",
            ".cmd", "bat"
    );

    private static final Set<String> SUPPORTED_RUNTIMES = Set.of("python", "node", "shell", "powershell", "bat");

    private final SkillPathGuard skillPathGuard;

    public Map<String, SkillScriptDefinition> discover(Path skillDirectory) {
        Path normalizedSkillDirectory = skillDirectory.toAbsolutePath().normalize();
        Map<String, SkillScriptDefinition> discoveredScripts = discoverFromScriptsDirectory(normalizedSkillDirectory);
        Map<String, SkillScriptDefinition> configuredScripts = discoverFromScriptsYaml(normalizedSkillDirectory);
        configuredScripts.forEach(discoveredScripts::put);
        return discoveredScripts;
    }

    private Map<String, SkillScriptDefinition> discoverFromScriptsDirectory(Path skillDirectory) {
        Path scriptsDirectory = skillDirectory.resolve("scripts");
        if (!Files.isDirectory(scriptsDirectory)) {
            return new LinkedHashMap<>();
        }

        Map<String, SkillScriptDefinition> discoveredScripts = new LinkedHashMap<>();
        try (var pathStream = Files.walk(scriptsDirectory)) {
            List<Path> scriptPaths = pathStream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString()))
                    .toList();
            for (Path scriptPath : scriptPaths) {
                String runtime = inferRuntime(scriptPath);
                if (runtime == null) {
                    continue;
                }
                String scriptName = stripExtension(scriptPath.getFileName().toString());
                if (discoveredScripts.containsKey(scriptName)) {
                    throw new SkillLoadException("duplicate auto discovered script name '" + scriptName + "' under " + skillDirectory);
                }
                discoveredScripts.put(scriptName, buildScriptDefinition(skillDirectory, scriptName, scriptPath, runtime,
                        "自动发现脚本", Collections.singletonMap("source", "auto")));
            }
        } catch (IOException e) {
            throw new SkillLoadException("failed to discover scripts under " + scriptsDirectory, e);
        }
        return discoveredScripts;
    }

    @SuppressWarnings("unchecked")
    private Map<String, SkillScriptDefinition> discoverFromScriptsYaml(Path skillDirectory) {
        Path scriptsYamlPath = skillDirectory.resolve("scripts.yaml");
        if (!Files.isRegularFile(scriptsYamlPath)) {
            return new LinkedHashMap<>();
        }

        try {
            String yamlText = Files.readString(scriptsYamlPath, StandardCharsets.UTF_8);
            Object loaded = new Yaml().load(yamlText);
            if (!(loaded instanceof Map<?, ?> loadedMap)) {
                throw new SkillLoadException("scripts.yaml must be a yaml map: " + scriptsYamlPath);
            }

            Object scriptsNode = loadedMap.containsKey("scripts") ? loadedMap.get("scripts") : loadedMap;
            Map<String, SkillScriptDefinition> configuredScripts = new LinkedHashMap<>();
            if (scriptsNode instanceof Map<?, ?> scriptsMap) {
                for (Map.Entry<?, ?> entry : scriptsMap.entrySet()) {
                    String scriptName = String.valueOf(entry.getKey()).trim();
                    Map<String, Object> scriptConfig = normalizeScriptConfig(entry.getValue(), scriptsYamlPath);
                    configuredScripts.put(scriptName, buildConfiguredScript(skillDirectory, scriptName, scriptConfig));
                }
                return configuredScripts;
            }

            if (scriptsNode instanceof List<?> scriptList) {
                for (Object scriptItem : scriptList) {
                    if (!(scriptItem instanceof Map<?, ?> scriptMap)) {
                        throw new SkillLoadException("scripts list item must be a map: " + scriptsYamlPath);
                    }
                    Map<String, Object> scriptConfig = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : scriptMap.entrySet()) {
                        if (entry.getKey() == null) {
                            continue;
                        }
                        scriptConfig.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    String scriptName = readConfiguredValue(scriptConfig, Arrays.asList("name", "script_name"));
                    if (scriptName == null || scriptName.isBlank()) {
                        throw new SkillLoadException("script name is required in scripts.yaml: " + scriptsYamlPath);
                    }
                    if (configuredScripts.containsKey(scriptName)) {
                        throw new SkillLoadException("duplicate configured script name '" + scriptName + "' in " + scriptsYamlPath);
                    }
                    configuredScripts.put(scriptName, buildConfiguredScript(skillDirectory, scriptName, scriptConfig));
                }
                return configuredScripts;
            }

            throw new SkillLoadException("unsupported scripts.yaml structure: " + scriptsYamlPath);
        } catch (IOException e) {
            throw new SkillLoadException("failed to read " + scriptsYamlPath, e);
        }
    }

    private Map<String, Object> normalizeScriptConfig(Object rawConfig, Path scriptsYamlPath) {
        if (rawConfig instanceof String configPath) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("path", configPath);
            return normalized;
        }
        if (!(rawConfig instanceof Map<?, ?> rawConfigMap)) {
            throw new SkillLoadException("script config must be a string or map: " + scriptsYamlPath);
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawConfigMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private SkillScriptDefinition buildConfiguredScript(Path skillDirectory,
                                                        String scriptName,
                                                        Map<String, Object> scriptConfig) {
        String relativePath = readConfiguredValue(scriptConfig, Arrays.asList("path", "script", "relative_path"));
        if (relativePath == null || relativePath.isBlank()) {
            throw new SkillLoadException("script path is required for '" + scriptName + "'");
        }

        Path absolutePath = skillPathGuard.ensureUnderRoot(skillDirectory, skillDirectory.resolve(relativePath));
        if (!Files.isRegularFile(absolutePath)) {
            throw new SkillLoadException("configured script does not exist: " + absolutePath);
        }

        String runtime = readConfiguredValue(scriptConfig, List.of("runtime"));
        if (runtime == null || runtime.isBlank()) {
            runtime = inferRuntime(absolutePath);
        }
        if (runtime == null || !SUPPORTED_RUNTIMES.contains(runtime.toLowerCase(Locale.ROOT))) {
            throw new SkillLoadException("unsupported runtime for script '" + scriptName + "': " + runtime);
        }

        String description = readConfiguredValue(scriptConfig, List.of("description", "desc"));
        Map<String, Object> metadata = new LinkedHashMap<>(scriptConfig);
        metadata.remove("name");
        metadata.remove("script_name");
        metadata.remove("path");
        metadata.remove("script");
        metadata.remove("relative_path");
        metadata.remove("runtime");
        metadata.remove("description");
        metadata.remove("desc");
        metadata.put("source", "config");

        return buildScriptDefinition(skillDirectory,
                scriptName,
                absolutePath,
                runtime.toLowerCase(Locale.ROOT),
                description == null || description.isBlank() ? "脚本定义来源 scripts.yaml" : description.trim(),
                metadata);
    }

    private SkillScriptDefinition buildScriptDefinition(Path skillDirectory,
                                                        String scriptName,
                                                        Path absolutePath,
                                                        String runtime,
                                                        String description,
                                                        Map<String, Object> metadata) {
        Path normalizedPath = skillPathGuard.ensureUnderRoot(skillDirectory, absolutePath);
        String relativePath = normalizeRelativePath(skillDirectory.relativize(normalizedPath));
        return SkillScriptDefinition.builder()
                .scriptName(scriptName)
                .relativePath(relativePath)
                .absolutePath(normalizedPath)
                .runtime(runtime)
                .description(description)
                .metadata(new LinkedHashMap<>(metadata))
                .build();
    }

    private String inferRuntime(Path scriptPath) {
        String fileName = scriptPath.getFileName().toString().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : EXTENSION_RUNTIME_MAP.entrySet()) {
            if (fileName.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String normalizeRelativePath(Path relativePath) {
        List<String> pathParts = new ArrayList<>();
        for (Path pathPart : relativePath) {
            pathParts.add(pathPart.toString());
        }
        return String.join("/", pathParts);
    }

    private String readConfiguredValue(Map<String, Object> scriptConfig, List<String> candidateKeys) {
        for (String candidateKey : candidateKeys) {
            Object value = scriptConfig.get(candidateKey);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }
}

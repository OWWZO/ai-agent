package org.wwz.ai.domain.agent.runtime.tool.skill;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 skill 目录中的 SKILL.md 文件。
 */
@Component
public class SkillMarkdownParser {

    private static final Pattern FRONT_MATTER_PATTERN =
            Pattern.compile("^---\\s*\\R(.*?)\\R---\\s*\\R?(.*)$", Pattern.DOTALL);

    public SkillDefinition parse(Path skillDirectory) {
        Path normalizedSkillDirectory = skillDirectory.toAbsolutePath().normalize();
        Path skillMarkdownPath = normalizedSkillDirectory.resolve("SKILL.md");
        if (!Files.isRegularFile(skillMarkdownPath)) {
            throw new SkillLoadException("missing SKILL.md under " + normalizedSkillDirectory);
        }

        try {
            String markdown = Files.readString(skillMarkdownPath, StandardCharsets.UTF_8);
            ParsedMarkdown parsedMarkdown = parseMarkdown(markdown, skillMarkdownPath);
            String name = readRequiredField(parsedMarkdown.frontMatter(), "name", skillMarkdownPath);
            String description = readRequiredField(parsedMarkdown.frontMatter(), "description", skillMarkdownPath);

            return SkillDefinition.builder()
                    .name(name)
                    .description(description)
                    .basePath(normalizedSkillDirectory)
                    .content(parsedMarkdown.content())
                    .frontMatter(parsedMarkdown.frontMatter())
                    .build();
        } catch (IOException e) {
            throw new SkillLoadException("failed to read " + skillMarkdownPath, e);
        }
    }

    private ParsedMarkdown parseMarkdown(String markdown, Path skillMarkdownPath) {
        if (markdown == null || markdown.isBlank()) {
            throw new SkillLoadException("SKILL.md is empty: " + skillMarkdownPath);
        }

        Matcher matcher = FRONT_MATTER_PATTERN.matcher(markdown);
        if (!matcher.matches()) {
            return new ParsedMarkdown(new LinkedHashMap<>(), markdown.strip());
        }

        String frontMatterBlock = matcher.group(1);
        String content = matcher.group(2) == null ? "" : matcher.group(2).strip();
        return new ParsedMarkdown(parseFrontMatter(frontMatterBlock, skillMarkdownPath), content);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFrontMatter(String frontMatterBlock, Path skillMarkdownPath) {
        Object parsed = new Yaml().load(frontMatterBlock);
        if (parsed == null) {
            return new LinkedHashMap<>();
        }
        if (!(parsed instanceof Map<?, ?> parsedMap)) {
            throw new SkillLoadException("front matter must be a yaml map: " + skillMarkdownPath);
        }

        Map<String, Object> frontMatter = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : parsedMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            frontMatter.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return frontMatter;
    }

    private String readRequiredField(Map<String, Object> frontMatter, String fieldName, Path skillMarkdownPath) {
        Object value = frontMatter.get(fieldName);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new SkillLoadException("missing required front matter '" + fieldName + "' in " + skillMarkdownPath);
        }
        return String.valueOf(value).trim();
    }

    private record ParsedMarkdown(Map<String, Object> frontMatter, String content) {
    }
}

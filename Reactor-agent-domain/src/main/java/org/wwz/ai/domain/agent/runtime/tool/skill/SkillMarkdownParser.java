package org.wwz.ai.domain.agent.runtime.tool.skill;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
            // 统一按 UTF-8 读取；front matter 只解析元数据，正文原样保留供 skill_view 返回。
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

    /**
     * 只解析 frontmatter 与目录定位，丢弃正文，供 catalog 扫描使用。
     */
    public SkillDescriptor parseMetadata(Path skillDirectory, String source, String relativePath) {
        Path normalizedSkillDirectory = skillDirectory.toAbsolutePath().normalize();
        Path skillMarkdownPath = normalizedSkillDirectory.resolve("SKILL.md");
        if (!Files.isRegularFile(skillMarkdownPath)) {
            throw new SkillLoadException("missing SKILL.md under " + normalizedSkillDirectory);
        }
        try {
            String markdown = Files.readString(skillMarkdownPath, StandardCharsets.UTF_8);
            ParsedMarkdown parsedMarkdown = parseMarkdown(markdown, skillMarkdownPath);
            Map<String, Object> frontMatter = parsedMarkdown.frontMatter();
            String name = readRequiredField(frontMatter, "name", skillMarkdownPath);
            String description = readRequiredField(frontMatter, "description", skillMarkdownPath);
            String category = firstNonBlank(
                    readOptionalField(frontMatter, "category"),
                    inferCategory(relativePath),
                    SkillDescriptor.CATEGORY_GENERAL);
            return SkillDescriptor.builder()
                    .ref(SkillRef.of(source, name, relativePath))
                    .name(name)
                    .description(description)
                    .category(category)
                    .tags(readTags(frontMatter))
                    .source(source == null || source.isBlank() ? SkillRef.SOURCE_BUILTIN : source.trim())
                    .version(readOptionalField(frontMatter, "version"))
                    .basePath(normalizedSkillDirectory)
                    .frontMatter(frontMatter)
                    .build();
        } catch (IOException e) {
            throw new SkillLoadException("failed to read " + skillMarkdownPath, e);
        }
    }

    public String readBody(Path skillDirectory) {
        return parse(skillDirectory).getContent();
    }

    private ParsedMarkdown parseMarkdown(String markdown, Path skillMarkdownPath) {
        if (markdown == null || markdown.isBlank()) {
            throw new SkillLoadException("SKILL.md is empty: " + skillMarkdownPath);
        }

        Matcher matcher = FRONT_MATTER_PATTERN.matcher(markdown);
        if (!matcher.matches()) {
            // 没有 front matter 时仍允许正文进入解析流程，必填字段由注册阶段给出明确错误。
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

    private String readOptionalField(Map<String, Object> frontMatter, String fieldName) {
        Object value = frontMatter.get(fieldName);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private Set<String> readTags(Map<String, Object> frontMatter) {
        Set<String> tags = new LinkedHashSet<>();
        collectTags(tags, frontMatter.get("tags"));
        Object metadata = frontMatter.get("metadata");
        if (metadata instanceof Map<?, ?> metadataMap) {
            Object hermes = metadataMap.get("hermes");
            if (hermes instanceof Map<?, ?> hermesMap) {
                collectTags(tags, hermesMap.get("tags"));
            }
        }
        return tags;
    }

    private void collectTags(Set<String> tags, Object raw) {
        if (raw == null) {
            return;
        }
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    tags.add(String.valueOf(item).trim());
                }
            }
            return;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return;
        }
        for (String part : text.split("[,\\s]+")) {
            if (!part.isBlank()) {
                tags.add(part.trim());
            }
        }
    }

    private String inferCategory(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return SkillDescriptor.CATEGORY_GENERAL;
        }
        String rel = relativePath.replace('\\', '/');
        int slash = rel.lastIndexOf('/');
        if (slash <= 0) {
            return SkillDescriptor.CATEGORY_GENERAL;
        }
        String parent = rel.substring(0, slash);
        return parent.isBlank() ? SkillDescriptor.CATEGORY_GENERAL : parent;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record ParsedMarkdown(Map<String, Object> frontMatter, String content) {
    }
}

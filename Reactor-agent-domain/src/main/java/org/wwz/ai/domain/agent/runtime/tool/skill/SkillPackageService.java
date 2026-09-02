package org.wwz.ai.domain.agent.runtime.tool.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能包安装：解析 zip / 粘贴正文 → 写入 skill root → refresh 注册表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillPackageService {

    private final SkillRuntimeOptions skillRuntimeOptions;
    private final SkillRegistry skillRegistry;

    public SkillPackageParser.ParsedSkillPackage previewZip(byte[] zipBytes) {
        return SkillPackageParser.parse(zipBytes);
    }

    public Map<String, Object> previewZipAsMap(byte[] zipBytes) {
        SkillPackageParser.ParsedSkillPackage p = previewZip(zipBytes);
        boolean taken = skillRegistry.findSkill(p.name()).isPresent();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.name());
        m.put("description", p.description());
        m.put("contentPreview", truncate(p.content(), 800));
        m.put("extraFiles", p.extraFiles());
        m.put("nameTaken", taken);
        return m;
    }

    /**
     * 上传 zip 安装；replace=true 时覆盖同名目录。
     */
    public Map<String, Object> installZip(byte[] zipBytes, String originalFilename, boolean replace) {
        SkillPackageParser.ParsedSkillPackage parsed = SkillPackageParser.parse(zipBytes);
        if (skillRegistry.findSkill(parsed.name()).isPresent() && !replace) {
            throw new SkillLoadException("技能「" + parsed.name() + "」已存在；若要覆盖请传 replace=true");
        }
        Map<String, byte[]> files = SkillPackageParser.unpack(zipBytes);
        Path root = requireWritableRoot();
        Path skillDir = root.resolve(parsed.name()).toAbsolutePath().normalize();
        if (!skillDir.startsWith(root.toAbsolutePath().normalize())) {
            throw new SkillLoadException("非法技能目录");
        }
        try {
            if (Files.exists(skillDir) && replace) {
                deleteRecursive(skillDir);
            }
            Files.createDirectories(skillDir);
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                Path target = skillDir.resolve(e.getKey()).normalize();
                if (!target.startsWith(skillDir)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.write(target, e.getValue());
            }
            // 保证 SKILL.md 正文与解析结果一致（frontmatter 规范化）
            writeSkillMd(skillDir, parsed.name(), parsed.description(), parsed.content());
        } catch (IOException e) {
            throw new SkillLoadException("写入技能目录失败：" + e.getMessage(), e);
        }
        skillRegistry.refresh();
        log.info("skill installed from zip name={} file={} replace={}", parsed.name(), originalFilename, replace);
        return skillRow(parsed.name());
    }

    /**
     * 粘贴 SKILL.md 正文创建/覆盖。
     */
    public Map<String, Object> installFromMarkdown(String name, String description, String rawContent, boolean replace) {
        SkillPackageParser.FrontmatterSplit front = SkillPackageParser.splitFrontmatter(
                StringUtils.defaultString(rawContent));
        String resolvedName = SkillPackageParser.sanitizeSkillName(
                StringUtils.defaultIfBlank(name, front.fields().get("name")));
        String resolvedDesc = StringUtils.defaultIfBlank(description, front.fields().get("description"));
        String body = front.body();
        if (StringUtils.isBlank(body)) {
            throw new SkillLoadException("SKILL.md 正文不能为空");
        }
        if (skillRegistry.findSkill(resolvedName).isPresent() && !replace) {
            throw new SkillLoadException("技能「" + resolvedName + "」已存在；若要覆盖请传 replace=true");
        }
        Path root = requireWritableRoot();
        Path skillDir = root.resolve(resolvedName).toAbsolutePath().normalize();
        try {
            if (Files.exists(skillDir) && replace) {
                // 仅覆盖 SKILL.md，保留 scripts/references
                writeSkillMd(skillDir, resolvedName, resolvedDesc, body);
            } else {
                Files.createDirectories(skillDir);
                writeSkillMd(skillDir, resolvedName, resolvedDesc, body);
            }
        } catch (IOException e) {
            throw new SkillLoadException("写入技能失败：" + e.getMessage(), e);
        }
        skillRegistry.refresh();
        log.info("skill installed from markdown name={}", resolvedName);
        return skillRow(resolvedName);
    }

    /**
     * 从 URL 下载 zip 后安装（在线导入最小闭环）。
     */
    public Map<String, Object> installFromUrl(String url, boolean replace) {
        if (StringUtils.isBlank(url) || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new SkillLoadException("url 必须是 http(s)");
        }
        try {
            byte[] bytes;
            try (var in = java.net.URI.create(url.trim()).toURL().openStream()) {
                bytes = in.readAllBytes();
            }
            if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
                // 可能是单文件 SKILL.md
                String text = new String(bytes, StandardCharsets.UTF_8);
                if (text.contains("---") || text.contains("#")) {
                    return installFromMarkdown(null, null, text, replace);
                }
                throw new SkillLoadException("URL 内容不是 zip 也不是 SKILL.md 文本");
            }
            return installZip(bytes, url.substring(url.lastIndexOf('/') + 1), replace);
        } catch (SkillLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new SkillLoadException("下载/导入失败：" + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> listInstalled() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SkillDefinition def : skillRegistry.listSkills()) {
            if (def == null) {
                continue;
            }
            rows.add(skillRow(def.getName()));
        }
        return rows;
    }

    public boolean deleteSkill(String name) {
        String n = SkillPackageParser.sanitizeSkillName(name);
        Path root = requireWritableRoot();
        Path skillDir = root.resolve(n).toAbsolutePath().normalize();
        if (!skillDir.startsWith(root.toAbsolutePath().normalize()) || !Files.isDirectory(skillDir)) {
            return false;
        }
        try {
            deleteRecursive(skillDir);
            skillRegistry.refresh();
            return true;
        } catch (IOException e) {
            throw new SkillLoadException("删除技能失败：" + e.getMessage(), e);
        }
    }

    public void reload() {
        skillRegistry.refresh();
    }

    /**
     * Agent 创作：创建/覆盖 SKILL.md（全局 skill 根目录），并 refresh 注册表。
     * 始终 replace=true 语义，便于迭代优化。
     */
    public Map<String, Object> upsertManual(String name, String description, String content) {
        return installFromMarkdown(name, description, content, true);
    }

    /**
     * 在全局技能目录下写入相对路径文件（脚本/参考资料等），路径不得逃逸技能根。
     * 技能不存在时自动建最小 SKILL.md 骨架。
     */
    public Map<String, Object> writeRelativeFile(String skillName, String relativePath, String content) {
        String name = SkillPackageParser.sanitizeSkillName(skillName);
        String rel = normalizeRelativePath(relativePath);
        Path skillDir = ensureSkillDir(name);
        Path target = skillDir.resolve(rel).normalize();
        if (!target.startsWith(skillDir)) {
            throw new SkillLoadException("路径逃逸技能目录: " + relativePath);
        }
        if ("SKILL.md".equalsIgnoreCase(rel) || rel.endsWith("/SKILL.md")) {
            throw new SkillLoadException("请用 upsert 写 SKILL.md，不要用 write_file 覆盖手册元数据");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SkillLoadException("写入技能文件失败：" + e.getMessage(), e);
        }
        skillRegistry.refresh();
        log.info("skill file written name={} path={}", name, rel);
        Map<String, Object> row = skillRow(name);
        row.put("writtenPath", rel);
        row.put("bytes", content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length);
        return row;
    }

    /**
     * 删除技能包内相对文件（不可删 SKILL.md；删技能请用 deleteSkill）。
     */
    public Map<String, Object> deleteRelativeFile(String skillName, String relativePath) {
        String name = SkillPackageParser.sanitizeSkillName(skillName);
        String rel = normalizeRelativePath(relativePath);
        if ("SKILL.md".equalsIgnoreCase(rel)) {
            throw new SkillLoadException("不能删除 SKILL.md；删除整个技能请用管理端或 delete 动作");
        }
        Path root = requireWritableRoot();
        Path skillDir = root.resolve(name).toAbsolutePath().normalize();
        if (!Files.isDirectory(skillDir)) {
            throw new SkillLoadException("技能不存在: " + name);
        }
        Path target = skillDir.resolve(rel).normalize();
        if (!target.startsWith(skillDir)) {
            throw new SkillLoadException("路径逃逸技能目录: " + relativePath);
        }
        try {
            boolean deleted = Files.deleteIfExists(target);
            skillRegistry.refresh();
            Map<String, Object> row = skillRow(name);
            row.put("deletedPath", rel);
            row.put("deleted", deleted);
            return row;
        } catch (IOException e) {
            throw new SkillLoadException("删除技能文件失败：" + e.getMessage(), e);
        }
    }

    public List<String> listRelativeFiles(String skillName) {
        String name = SkillPackageParser.sanitizeSkillName(skillName);
        Path root = requireWritableRoot();
        Path skillDir = root.resolve(name).toAbsolutePath().normalize();
        if (!Files.isDirectory(skillDir)) {
            throw new SkillLoadException("技能不存在: " + name);
        }
        List<String> files = new ArrayList<>();
        try (var walk = Files.walk(skillDir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String rel = skillDir.relativize(p).toString().replace('\\', '/');
                files.add(rel);
            });
        } catch (IOException e) {
            throw new SkillLoadException("列举技能文件失败：" + e.getMessage(), e);
        }
        files.sort(String::compareTo);
        return files;
    }

    private Path ensureSkillDir(String name) {
        Path root = requireWritableRoot();
        Path skillDir = root.resolve(name).toAbsolutePath().normalize();
        if (!skillDir.startsWith(root.toAbsolutePath().normalize())) {
            throw new SkillLoadException("非法技能目录");
        }
        try {
            if (!Files.isDirectory(skillDir)) {
                Files.createDirectories(skillDir);
                writeSkillMd(skillDir, name, "agent-authored skill",
                        "（由 agent 自动创建骨架；请用 workspace_write/edit 补全手册）\n");
                skillRegistry.refresh();
            }
        } catch (IOException e) {
            throw new SkillLoadException("创建技能目录失败：" + e.getMessage(), e);
        }
        return skillDir;
    }

    private static String normalizeRelativePath(String relativePath) {
        if (StringUtils.isBlank(relativePath)) {
            throw new SkillLoadException("path 不能为空");
        }
        String rel = relativePath.trim().replace('\\', '/');
        while (rel.startsWith("./")) {
            rel = rel.substring(2);
        }
        if (rel.startsWith("/") || !SkillPackageParser.isSafeRelativePath(rel)) {
            throw new SkillLoadException("非法相对路径: " + relativePath);
        }
        return rel;
    }

    private Map<String, Object> skillRow(String name) {
        SkillDefinition def = skillRegistry.findSkill(name).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("description", def == null ? null : def.getDescription());
        m.put("basePath", def == null || def.getBasePath() == null ? null : def.getBasePath().toString());
        m.put("source", "upload");
        return m;
    }

    private Path requireWritableRoot() {
        List<String> dirs = skillRuntimeOptions.getDirectories();
        if (dirs == null || dirs.isEmpty()) {
            throw new SkillLoadException("未配置 skill.directories");
        }
        Path root = Path.of(dirs.get(0)).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new SkillLoadException("无法创建 skill 根目录：" + root, e);
        }
        return root;
    }

    private void writeSkillMd(Path skillDir, String name, String description, String body) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(name).append("\n");
        if (StringUtils.isNotBlank(description)) {
            sb.append("description: ").append(description.replace("\n", " ")).append("\n");
        }
        sb.append("---\n\n");
        sb.append(body.strip()).append("\n");
        Files.writeString(skillDir.resolve(SkillPackageParser.SKILL_FILE), sb.toString(), StandardCharsets.UTF_8);
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}

package org.wwz.ai.domain.agent.runtime.tool.skill;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 技能 zip 解析（对齐 agentic-rag SkillPackageParser）。
 * <p>
 * 支持 {@code SKILL.md} 在根，或 {@code name/SKILL.md} 一层目录；
 * 名字：frontmatter name > 外层目录名；不拿 zip 文件名兜底。
 */
public final class SkillPackageParser {

    public static final String SKILL_FILE = "SKILL.md";

    private static final int MAX_ENTRIES = 500;
    private static final long MAX_INFLATED_BYTES = 32L * 1024 * 1024;
    private static final int MAX_SKILL_MD_BYTES = 1024 * 1024;
    private static final Pattern FRONT_MATTER =
            Pattern.compile("^---\\s*\\R(.*?)\\R---\\s*\\R?(.*)$", Pattern.DOTALL);

    private SkillPackageParser() {
    }

    public record ParsedSkillPackage(
            String name,
            String description,
            String content,
            List<String> extraFiles
    ) {
    }

    public record FrontmatterSplit(Map<String, String> fields, String body) {
    }

    public static ParsedSkillPackage parse(byte[] zip) {
        String skillMd = null;
        String skillDir = null;
        List<String> extras = new ArrayList<>();
        int entries = 0;
        long inflated = 0L;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new SkillLoadException("技能包条目过多（超过 " + MAX_ENTRIES + " 个）");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String path = entry.getName().replace('\\', '/').replaceAll("^/+", "");
                if (path.startsWith("__MACOSX/") || path.endsWith("/.DS_Store") || path.equals(".DS_Store")) {
                    continue;
                }
                byte[] bytes = zis.readAllBytes();
                inflated += bytes.length;
                if (inflated > MAX_INFLATED_BYTES) {
                    throw new SkillLoadException("技能包解压后超过 32MB");
                }
                String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
                String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
                if (SKILL_FILE.equals(fileName) && !dir.contains("/")) {
                    if (skillMd != null) {
                        throw new SkillLoadException("技能包里有多个 " + SKILL_FILE + "，无法确定用哪一份");
                    }
                    if (bytes.length > MAX_SKILL_MD_BYTES) {
                        throw new SkillLoadException(SKILL_FILE + " 超过 1MB");
                    }
                    skillMd = new String(bytes, StandardCharsets.UTF_8);
                    skillDir = dir;
                } else {
                    extras.add(path);
                }
            }
        } catch (SkillLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new SkillLoadException("无法解析技能 zip：" + e.getMessage(), e);
        }

        if (skillMd == null || skillMd.isBlank()) {
            throw new SkillLoadException(
                    "技能包里没有 " + SKILL_FILE + "（支持根目录或一层目录如 my-skill/" + SKILL_FILE + "）");
        }
        FrontmatterSplit front = splitFrontmatter(skillMd);
        String name = firstNonBlank(front.fields().get("name"), blankToNull(skillDir));
        if (name == null || name.isBlank()) {
            throw new SkillLoadException(
                    "取不到技能名：请在 " + SKILL_FILE + " frontmatter 写 name:，或放进以技能名命名的目录");
        }
        name = sanitizeSkillName(name.trim());
        if (front.body().isBlank()) {
            throw new SkillLoadException(SKILL_FILE + " 除 frontmatter 外没有正文");
        }
        String description = front.fields().getOrDefault("description", "").trim();
        String prefix = (skillDir == null || skillDir.isBlank()) ? "" : skillDir + "/";
        List<String> extraDisplay = extras.stream()
                .map(p -> p.startsWith(prefix) ? p.substring(prefix.length()) : p)
                .sorted()
                .toList();
        return new ParsedSkillPackage(name, description.isBlank() ? null : description, front.body(), extraDisplay);
    }

    /**
     * 解压为相对路径 → 字节（剥外层目录），供落地到 skill root。
     */
    public static Map<String, byte[]> unpack(byte[] zip) {
        String skillDir = null;
        Map<String, byte[]> files = new LinkedHashMap<>();
        int entries = 0;
        long inflated = 0L;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new SkillLoadException("技能包条目过多");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String path = entry.getName().replace('\\', '/').replaceAll("^/+", "");
                if (path.startsWith("__MACOSX/") || path.endsWith(".DS_Store")) {
                    continue;
                }
                byte[] bytes = zis.readAllBytes();
                inflated += bytes.length;
                if (inflated > MAX_INFLATED_BYTES) {
                    throw new SkillLoadException("技能包解压后超过 32MB");
                }
                String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
                String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
                if (SKILL_FILE.equals(fileName) && !dir.contains("/")) {
                    if (skillDir != null) {
                        throw new SkillLoadException("技能包里有多个 " + SKILL_FILE);
                    }
                    skillDir = dir;
                }
                files.put(path, bytes);
            }
        } catch (SkillLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new SkillLoadException("解压技能包失败：" + e.getMessage(), e);
        }
        if (skillDir == null) {
            throw new SkillLoadException("技能包里没有 " + SKILL_FILE);
        }
        String prefix = skillDir.isEmpty() ? "" : skillDir + "/";
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String rel = e.getKey().startsWith(prefix) ? e.getKey().substring(prefix.length()) : e.getKey();
            if (isSafeRelativePath(rel)) {
                out.put(rel, e.getValue());
            }
        }
        return out;
    }

    public static FrontmatterSplit splitFrontmatter(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new FrontmatterSplit(Map.of(), "");
        }
        Matcher m = FRONT_MATTER.matcher(markdown);
        if (!m.matches()) {
            return new FrontmatterSplit(new LinkedHashMap<>(), markdown.strip());
        }
        Map<String, String> fields = new LinkedHashMap<>();
        String block = m.group(1) == null ? "" : m.group(1);
        for (String line : block.split("\\R")) {
            int i = line.indexOf(':');
            if (i <= 0) {
                continue;
            }
            String k = line.substring(0, i).trim();
            String v = line.substring(i + 1).trim();
            if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                v = v.substring(1, v.length() - 1);
            }
            if (!k.isBlank()) {
                fields.put(k, v);
            }
        }
        String body = m.group(2) == null ? "" : m.group(2).strip();
        return new FrontmatterSplit(fields, body);
    }

    public static String sanitizeSkillName(String name) {
        String n = name.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
        n = n.replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (n.isBlank()) {
            throw new SkillLoadException("技能名非法");
        }
        return n;
    }

    public static boolean isSafeRelativePath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) < 0x20) {
                return false;
            }
        }
        for (String seg : path.split("/")) {
            if (seg.isBlank() || ".".equals(seg) || "..".equals(seg)) {
                return false;
            }
        }
        return true;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}

package org.wwz.ai.domain.agent.runtime.tool.skill;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 技能 zip 解析。
 * <p>
 * 支持 {@code SKILL.md} 在根，或 {@code name/SKILL.md} 一层目录；
 * 名字：frontmatter name > 外层目录名；不拿 zip 文件名兜底。
 */
public final class SkillPackageParser {

    public static final String SKILL_FILE = "SKILL.md";
    public static final long MAX_PACKAGE_BYTES = 32L * 1024 * 1024;

    private static final int MAX_ENTRIES = 500;
    private static final long MAX_INFLATED_BYTES = MAX_PACKAGE_BYTES;
    private static final int MAX_SKILL_MD_BYTES = 1024 * 1024;

    private SkillPackageParser() {
    }

    public record ParsedSkillPackage(
            String name,
            String description,
            String content,
            List<String> extraFiles,
            Map<String, Object> frontMatter
    ) {
        public ParsedSkillPackage {
            frontMatter = frontMatter == null ? new LinkedHashMap<>() : frontMatter;
        }
    }

    public record FrontmatterSplit(Map<String, Object> fields, String body) {
        public FrontmatterSplit {
            fields = fields == null ? new LinkedHashMap<>() : fields;
            body = body == null ? "" : body;
        }
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
                byte[] bytes = readEntryBytes(zis, MAX_INFLATED_BYTES - inflated);
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
        String name = firstNonBlank(asText(front.fields().get("name")), blankToNull(skillDir));
        if (name == null || name.isBlank()) {
            throw new SkillLoadException(
                    "取不到技能名：请在 " + SKILL_FILE + " frontmatter 写 name:，或放进以技能名命名的目录");
        }
        name = sanitizeSkillName(name.trim());
        if (front.body().isBlank()) {
            throw new SkillLoadException(SKILL_FILE + " 除 frontmatter 外没有正文");
        }
        String description = asText(front.fields().get("description"));
        String prefix = (skillDir == null || skillDir.isBlank()) ? "" : skillDir + "/";
        List<String> extraDisplay = extras.stream()
                .map(p -> p.startsWith(prefix) ? p.substring(prefix.length()) : p)
                .sorted()
                .toList();
        return new ParsedSkillPackage(
                name,
                description,
                front.body(),
                extraDisplay,
                front.fields());
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
                byte[] bytes = readEntryBytes(zis, MAX_INFLATED_BYTES - inflated);
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

    private static byte[] readEntryBytes(ZipInputStream zis, long remainingBytes) throws Exception {
        if (remainingBytes < 0) {
            throw new SkillLoadException("技能包解压后超过 32MB");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                (int) Math.min(8192L, remainingBytes));
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = zis.read(buffer)) != -1) {
            total += read;
            if (total > remainingBytes) {
                throw new SkillLoadException("技能包解压后超过 32MB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /**
     * 切分 frontmatter 与正文。
     * <p>
     * 解析统一委托 {@link SkillFrontMatterParser}。旧实现按行 {@code indexOf(':')} 切分并剥掉
     * 值的外层引号，导致写回时生成非法 YAML（{@code mapping values are not allowed here}），
     * 该实现已删除，请勿恢复。
     */
    public static FrontmatterSplit splitFrontmatter(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new FrontmatterSplit(new LinkedHashMap<>(), "");
        }
        SkillFrontMatter frontMatter = SkillFrontMatterParser.parse(markdown);
        return new FrontmatterSplit(frontMatter.fields(), frontMatter.body());
    }

    /**
     * 把 frontmatter 字段值安全转成字符串；null 或空白返回 null。
     */
    public static String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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

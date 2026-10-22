package org.wwz.ai.domain.agent.runtime.tool.skill;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 导入 skill 的静态威胁扫描。
 * <p>
 * 参照 hermes-agent {@code tools/skills_guard.py::THREAT_PATTERNS} 的精简子集，
 * 覆盖凭据外泄、Prompt 注入、破坏性命令、持久化、供应链、提权、隐形 Unicode 与结构风险。
 * <p>
 * 只做静态正则匹配，不做 AST 分析；结果交给 {@link SkillInstallPolicy} 决定放行方式。
 */
public final class SkillThreatScanner {

    public static final long MAX_FILE_BYTES = 1024 * 1024;
    public static final int MAX_FILES = 500;
    public static final long MAX_TOTAL_BYTES = 32L * 1024 * 1024;
    public static final int MAX_FINDINGS = 200;
    public static final int EXCERPT_CHARS = 120;

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "md", "markdown", "txt", "text", "py", "sh", "bash", "zsh", "ps1", "bat", "cmd",
            "js", "mjs", "cjs", "ts", "tsx", "jsx", "json", "yaml", "yml", "toml", "ini",
            "cfg", "conf", "env", "sql", "java", "go", "rb", "pl", "php", "html", "htm",
            "css", "xml", "csv", "properties");

    public enum Severity {
        INFO, WARN, BLOCK
    }

    public record Finding(String ruleId, Severity severity, String file, int line, String excerpt) {

        public String describe() {
            StringBuilder builder = new StringBuilder()
                    .append('[').append(severity).append("] ").append(ruleId).append(" @ ").append(file);
            if (line > 0) {
                builder.append(':').append(line);
            }
            if (excerpt != null && !excerpt.isBlank()) {
                builder.append(" -> ").append(excerpt);
            }
            return builder.toString();
        }
    }

    public record Report(List<Finding> findings) {

        public Report {
            findings = findings == null ? List.of() : List.copyOf(findings);
        }

        public static Report empty() {
            return new Report(List.of());
        }

        public boolean clean() {
            return findings.isEmpty();
        }

        public boolean blocked() {
            return findings.stream().anyMatch(finding -> finding.severity() == Severity.BLOCK);
        }

        public Severity maxSeverity() {
            Severity max = Severity.INFO;
            for (Finding finding : findings) {
                if (finding.severity().ordinal() > max.ordinal()) {
                    max = finding.severity();
                }
            }
            return max;
        }

        public List<String> descriptions() {
            return findings.stream().map(Finding::describe).toList();
        }
    }

    private record Rule(String id, Severity severity, Pattern pattern) {
    }

    private static final List<Rule> RULES = List.of(
            // ── 凭据外泄 ──────────────────────────────────────────────
            rule("cred-exfil-env", Severity.BLOCK,
                    "(?i)(process\\.env|os\\.environ|os\\.getenv|\\$env:)"
                            + "[^\\n]{0,60}(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL)"),
            rule("cred-exfil-file", Severity.BLOCK,
                    "(?i)(~?/?\\.ssh/id_rsa|\\.aws/credentials|\\.netrc|\\.git-credentials|id_ed25519)"),
            rule("cred-exfil-net", Severity.BLOCK,
                    "(?i)(curl|wget|Invoke-WebRequest|requests\\.(post|get)|fetch\\()[^\\n]{0,120}https?://"),

            // ── Prompt 注入 ───────────────────────────────────────────
            rule("prompt-injection-override", Severity.BLOCK,
                    "(?i)(ignore|disregard|forget)\\s+(all\\s+)?(previous|prior|above)\\s+"
                            + "(instructions?|prompts?|rules?|context)"),
            rule("prompt-injection-roleplay", Severity.BLOCK,
                    "(?i)you\\s+are\\s+now\\s+(a|an|the)\\b"),
            rule("prompt-injection-exfiltrate", Severity.BLOCK,
                    "(?i)(reveal|print|output|dump)\\s+(your\\s+)?(system\\s+prompt|hidden\\s+instructions)"),

            // ── 破坏性命令 ────────────────────────────────────────────
            rule("destructive-rm-root", Severity.BLOCK,
                    "(?i)\\brm\\s+(-[a-z]*\\s+)*-?[a-z]*[rf][a-z]*\\s+/(\\s|$)"),
            rule("destructive-mkfs", Severity.BLOCK, "(?i)\\bmkfs(\\.[a-z0-9]+)?\\b"),
            rule("destructive-dd", Severity.BLOCK, "(?i)\\bdd\\s+[^\\n]*of=/dev/"),
            rule("destructive-chmod-root", Severity.BLOCK, "(?i)\\bchmod\\s+(-R\\s+)?777\\s+/"),
            rule("destructive-format", Severity.BLOCK, "(?i)\\bformat\\s+[a-z]:"),

            // ── 持久化 ────────────────────────────────────────────────
            rule("persistence-crontab", Severity.BLOCK, "(?i)\\bcrontab\\b"),
            rule("persistence-shellrc", Severity.BLOCK,
                    "(?i)>>?\\s*[^\\n]{0,40}(\\.bashrc|\\.zshrc|\\.profile|\\.bash_profile)"),
            rule("persistence-systemd", Severity.BLOCK, "(?i)/etc/systemd/system"),
            rule("persistence-registry", Severity.BLOCK,
                    "(?i)\\breg\\s+add\\b[^\\n]*\\\\Run\\b"),

            // ── 供应链 ────────────────────────────────────────────────
            rule("supply-chain-pipe-shell", Severity.BLOCK,
                    "(?i)\\b(curl|wget)\\b[^\\n|]{0,200}\\|\\s*(ba|z|fi)?sh\\b"),
            rule("supply-chain-global-install", Severity.WARN,
                    "(?i)\\b(npm|pnpm|yarn)\\s+(install|add|i)\\s+(-g|--global)"),
            rule("supply-chain-insecure-index", Severity.BLOCK,
                    "(?i)\\b(pip|pip3)\\s+install\\b[^\\n]*--index-url\\s+http://"),

            // ── 提权 ──────────────────────────────────────────────────
            rule("privilege-sudo", Severity.WARN, "(?i)\\bsudo\\s+"),
            rule("privilege-setuid", Severity.WARN, "(?i)\\bsetuid\\b"));

    private static final Pattern INVISIBLE_UNICODE =
            Pattern.compile("[\\u200B-\\u200D\\u2060\\uFEFF\\u202A-\\u202E\\u2066-\\u2069]");

    private static Rule rule(String id, Severity severity, String regex) {
        return new Rule(id, severity, Pattern.compile(regex));
    }

    /**
     * 扫描整个 skill 目录。目录不存在或不可读时返回空报告（不抛异常）。
     */
    public Report scan(Path skillDirectory) {
        if (skillDirectory == null || !Files.isDirectory(skillDirectory)) {
            return Report.empty();
        }
        Path root = skillDirectory.toAbsolutePath().normalize();
        List<Finding> findings = new ArrayList<>();
        int fileCount = 0;
        long totalBytes = 0L;

        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> paths = stream.toList();
            for (Path path : paths) {
                if (findings.size() >= MAX_FINDINGS) {
                    break;
                }
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (Files.isSymbolicLink(path)) {
                    findings.add(new Finding("structure-symlink", Severity.BLOCK, relative, 0,
                            "符号链接：" + safeLinkTarget(path)));
                    continue;
                }
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                fileCount++;
                long size;
                try {
                    size = Files.size(path);
                } catch (IOException e) {
                    continue;
                }
                totalBytes += size;
                if (size > MAX_FILE_BYTES) {
                    findings.add(new Finding("structure-large-file", Severity.WARN, relative, 0,
                            size + " bytes"));
                    continue;
                }
                if (!isTextFile(relative)) {
                    continue;
                }
                String text;
                try {
                    text = Files.readString(path, StandardCharsets.UTF_8);
                } catch (CharacterCodingException e) {
                    continue;
                } catch (IOException e) {
                    continue;
                }
                findings.addAll(scanText(text, relative).findings());
            }
        } catch (IOException e) {
            return new Report(findings);
        }

        if (fileCount > MAX_FILES) {
            findings.add(new Finding("structure-too-many-files", Severity.BLOCK, ".", 0,
                    fileCount + " 个文件，上限 " + MAX_FILES));
        }
        if (totalBytes > MAX_TOTAL_BYTES) {
            findings.add(new Finding("structure-too-large", Severity.BLOCK, ".", 0,
                    totalBytes + " bytes，上限 " + MAX_TOTAL_BYTES));
        }
        return new Report(truncate(findings));
    }

    /**
     * 扫描一段文本（粘贴 markdown 场景）。
     *
     * @param label 报告中显示的来源标识，例如 {@code SKILL.md}
     */
    public Report scanText(String text, String label) {
        if (text == null || text.isEmpty()) {
            return Report.empty();
        }
        String file = label == null || label.isBlank() ? "SKILL.md" : label;
        List<Finding> findings = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length && findings.size() < MAX_FINDINGS; i++) {
            String line = lines[i];
            for (Rule rule : RULES) {
                Matcher matcher = rule.pattern().matcher(line);
                if (matcher.find()) {
                    findings.add(new Finding(rule.id(), rule.severity(), file, i + 1, excerpt(line)));
                }
            }
            Matcher invisible = INVISIBLE_UNICODE.matcher(line);
            if (invisible.find()) {
                findings.add(new Finding("invisible-unicode", Severity.WARN, file, i + 1,
                        "位置 " + invisible.start() + "，码点 U+"
                                + Integer.toHexString(line.codePointAt(invisible.start())).toUpperCase(Locale.ROOT)));
            }
        }
        return new Report(truncate(findings));
    }

    private static List<Finding> truncate(List<Finding> findings) {
        return findings.size() <= MAX_FINDINGS ? findings : findings.subList(0, MAX_FINDINGS);
    }

    private static boolean isTextFile(String relativePath) {
        int dot = relativePath.lastIndexOf('.');
        if (dot < 0 || dot == relativePath.length() - 1) {
            return false;
        }
        String extension = relativePath.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (TEXT_EXTENSIONS.contains(extension)) {
            return true;
        }
        // 无扩展名的常见脚本
        String name = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        return "Makefile".equals(name) || "Dockerfile".equals(name);
    }

    private static String excerpt(String line) {
        String trimmed = line.strip();
        return trimmed.length() <= EXCERPT_CHARS ? trimmed : trimmed.substring(0, EXCERPT_CHARS) + "…";
    }

    private static String safeLinkTarget(Path path) {
        try {
            Path target = Files.readSymbolicLink(path);
            return target.toString();
        } catch (IOException e) {
            return "<unresolved>";
        }
    }
}

package org.wwz.ai.domain.agent.runtime.tool.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class OpenCliArgv {

    private static final Set<String> ROOT_COMMANDS = Set.of(
            "list", "doctor", "skills", "help", "validate", "verify", "auth",
            "plugin", "adapter", "profile", "daemon", "completion", "external"
    );

    private OpenCliArgv() {
    }

    public static List<String> rewrite(List<String> args, List<String> extraArgs) {
        List<String> out = new ArrayList<>(args == null ? List.of() : args);
        if (out.isEmpty()) {
            return out;
        }
        String head = out.get(0);
        // Browser subcommands render their own JSON envelopes and do not register
        // the site-command -f/--format option.
        if (!"browser".equals(head) && !hasFormat(out) && extraArgs != null && !extraArgs.isEmpty()) {
            out.addAll(extraArgs);
        }
        return out;
    }

    private static final Set<String> OUTPUT_ROOT_OPTIONS = Set.of("--output", "--output-dir", "--out", "--dir");
    private static final Set<String> DOWNLOAD_SITES = Set.of(
            "xiaohongshu", "rednote", "twitter", "bilibili", "1688");
    private static final Pattern WINDOWS_DRIVE_PATH = Pattern.compile("^[A-Za-z]:.*");

    /** 只把已登记的站点 download 命令视为会产生文件的命令。 */
    public static boolean isDownloadCommand(List<String> args) {
        if (args == null || args.isEmpty()) {
            return false;
        }
        for (int i = 0; i + 1 < args.size(); i++) {
            String site = args.get(i);
            String command = args.get(i + 1);
            if (site != null && command != null
                    && DOWNLOAD_SITES.contains(site.toLowerCase(Locale.ROOT))
                    && "download".equalsIgnoreCase(command)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析第一个 output 选项。返回值区分“未声明”和“声明但非法”，供调用方在执行 CLI
     * 之前返回结构化 usage 错误。
     */
    public static OutputOption resolveOutputOption(List<String> args) {
        if (args == null || args.isEmpty()) {
            return OutputOption.absent();
        }
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg == null) {
                continue;
            }
            if (OUTPUT_ROOT_OPTIONS.contains(arg)) {
                if (i + 1 >= args.size()) {
                    return OutputOption.invalid(arg, i, "output option is missing a value");
                }
                String value = args.get(i + 1);
                if (value == null || value.isBlank() || value.startsWith("-")) {
                    return OutputOption.invalid(arg, i, "output option requires a directory value");
                }
                return OutputOption.present(arg, value, i, i + 1, false);
            }
            int equals = arg.indexOf('=');
            if (equals > 0 && OUTPUT_ROOT_OPTIONS.contains(arg.substring(0, equals))) {
                String value = arg.substring(equals + 1);
                if (value.isBlank()) {
                    return OutputOption.invalid(arg.substring(0, equals), i, "output option requires a directory value");
                }
                return OutputOption.present(arg.substring(0, equals), value, i, i, true);
            }
        }
        return OutputOption.absent();
    }

    /**
     * 返回规范化后的 workspace 相对 output root。绝对路径、UNC 路径和 parent traversal
     * 都被拒绝；结果只使用 POSIX 分隔符。
     */
    public static String normalizeOutputRoot(String raw) {
        String normalized = tryNormalizeOutputRoot(raw);
        if (normalized == null) {
            throw new IllegalArgumentException("output root must be a non-empty workspace-relative directory");
        }
        return normalized;
    }

    /** 用本次调用的 capture root 替换已有 output 选项，或在缺失时追加 --output。 */
    public static List<String> rewriteOutputRoot(List<String> args, String captureRoot) {
        String normalizedRoot = normalizeOutputRoot(captureRoot);
        List<String> out = new ArrayList<>(args == null ? List.of() : args);
        boolean replaced = false;
        for (int i = 0; i < out.size(); i++) {
            String arg = out.get(i);
            if (arg == null) {
                continue;
            }
            if (OUTPUT_ROOT_OPTIONS.contains(arg)) {
                if (i + 1 >= out.size()) {
                    throw new IllegalArgumentException("output option is missing a value");
                }
                out.set(i + 1, normalizedRoot);
                replaced = true;
                i++;
                continue;
            }
            int equals = arg.indexOf('=');
            if (equals > 0 && OUTPUT_ROOT_OPTIONS.contains(arg.substring(0, equals))) {
                out.set(i, arg.substring(0, equals) + "=" + normalizedRoot);
                replaced = true;
            }
        }
        if (!replaced) {
            out.add("--output");
            out.add(normalizedRoot);
        }
        return out;
    }

    /**
     * 提取命令声明的原始 output 目录，保持兼容旧调用方；非法声明返回 null，不能被当作
     * “未声明”执行全 workspace fallback。
     */
    public static String outputRoot(List<String> args) {
        OutputOption option = resolveOutputOption(args);
        if (!option.present() || !option.valid()) {
            return null;
        }
        return option.rawValue();
    }

    /** 判断跨平台语义下的绝对路径。 */
    public static boolean isAbsolutePath(String raw) {
        if (raw == null) {
            return false;
        }
        String value = raw.trim().replace('\\', '/');
        return value.startsWith("/")
                || value.startsWith("//")
                || WINDOWS_DRIVE_PATH.matcher(value).matches()
                || isJavaAbsolutePath(value);
    }

    private static String tryNormalizeOutputRoot(String raw) {
        if (raw == null || raw.isBlank() || raw.indexOf('\0') >= 0) {
            return null;
        }
        String value = raw.trim().replace('\\', '/');
        if (isAbsolutePath(value)) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                return null;
            }
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(segment);
        }
        return result.length() == 0 ? null : result.toString();
    }

    private static boolean isJavaAbsolutePath(String value) {
        try {
            return java.nio.file.Path.of(value).isAbsolute();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public record OutputOption(boolean present,
                               boolean valid,
                               String option,
                               String rawValue,
                               int optionIndex,
                               int valueIndex,
                               boolean inline,
                               String error) {

        private static OutputOption absent() {
            return new OutputOption(false, true, null, null, -1, -1, false, null);
        }

        private static OutputOption present(String option,
                                            String rawValue,
                                            int optionIndex,
                                            int valueIndex,
                                            boolean inline) {
            return new OutputOption(true, true, option, rawValue.trim(), optionIndex, valueIndex, inline, null);
        }

        private static OutputOption invalid(String option, int optionIndex, String error) {
            return new OutputOption(true, false, option, null, optionIndex, -1, false, error);
        }
    }

    public static boolean isSensitive(List<String> args) {
        if (args == null || args.isEmpty()) {
            return false;
        }
        List<String> tokens = new ArrayList<>();
        for (String arg : args) {
            if (arg == null || arg.startsWith("-")) {
                continue;
            }
            tokens.add(arg);
        }
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if ("cookies".equals(token) || "eval".equals(token) || "exec".equals(token)) {
                return true;
            }
        }
        return false;
    }

    public static String sensitiveKind(List<String> args) {
        if (args == null) {
            return "cookies";
        }
        for (String arg : args) {
            if ("eval".equals(arg)) {
                return "eval";
            }
            if ("exec".equals(arg)) {
                return "exec";
            }
            if ("cookies".equals(arg)) {
                return "cookies";
            }
        }
        return "cookies";
    }

    static boolean isRootCommand(String command) {
        return command != null && ROOT_COMMANDS.contains(command.toLowerCase(Locale.ROOT));
    }

    private static boolean hasFormat(List<String> args) {
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ("-f".equals(arg) || "--format".equals(arg)) {
                return true;
            }
            if (arg != null && arg.startsWith("--format=")) {
                return true;
            }
        }
        return false;
    }

}

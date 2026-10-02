package org.wwz.ai.domain.agent.runtime.tool.cli;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class OpenCliArgv {

    private static final Set<String> ROOT_COMMANDS = Set.of(
            "list", "doctor", "skills", "help", "validate", "verify", "auth",
            "plugin", "adapter", "profile", "daemon", "completion", "external"
    );

    private OpenCliArgv() {
    }

    public static List<String> rewrite(List<String> args, String visitorId, List<String> extraArgs) {
        List<String> out = new ArrayList<>(args == null ? List.of() : args);
        if (out.isEmpty()) {
            return out;
        }
        String head = out.get(0);
        if ("browser".equals(head)) {
            boolean hasSession = out.size() > 1 && out.get(1) != null && out.get(1).startsWith("visitor:");
            if (!hasSession && StringUtils.isNotBlank(visitorId)) {
                out.add(1, "visitor:" + visitorId);
            }
        }
        // Browser subcommands render their own JSON envelopes and do not register
        // the site-command -f/--format option.
        if (!"browser".equals(head) && !hasFormat(out) && extraArgs != null && !extraArgs.isEmpty()) {
            out.addAll(extraArgs);
        }
        return out;
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

package org.wwz.ai.domain.agent.runtime.tool.cli;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Host CLI 准入策略：白名单、社交 CLI 只读约束、超时和代理环境。
 * <p>
 * 普通 {@code host_cli} 工具和 Code Mode 的 {@code host} capability 共用这一份实现，
 * 避免出现两套白名单。新增 CLI 限制只改这里。
 * </p>
 */
public final class HostCliPolicy {

    private static final Set<String> TWITTER_WRITE_COMMANDS = Set.of(
            "bookmark", "delete", "favorite", "follow", "like", "post", "quote",
            "reply", "retweet", "unbookmark", "unfavorite", "unfollow", "unlike", "unretweet");
    private static final Set<String> REDDIT_WRITE_COMMANDS = Set.of(
            "comment", "login", "logout", "save", "subscribe", "upvote");
    private static final List<String> PROXY_ENVIRONMENT_VARIABLES = List.of(
            "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy");

    private HostCliPolicy() {
    }

    /** CLI 是否在可用名单内。 */
    public static boolean isAllowed(String tool, List<String> availableTools) {
        return StringUtils.isNotBlank(tool) && availableTools != null && availableTools.contains(tool.trim());
    }

    /**
     * @return 被拒绝的写命令；null 表示允许。twitter / rdt 只允许只读命令。
     */
    public static String deniedSocialCommand(String tool, List<String> args) {
        if (!("twitter".equals(tool) || "rdt".equals(tool))) {
            return null;
        }
        if (args == null) {
            return null;
        }
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            String normalized = arg.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("-")) {
                continue;
            }
            if ("twitter".equals(tool) && TWITTER_WRITE_COMMANDS.contains(normalized)) {
                return normalized;
            }
            if ("rdt".equals(tool) && REDDIT_WRITE_COMMANDS.contains(normalized)) {
                return normalized;
            }
            return null;
        }
        return null;
    }

    /** 超时收敛：默认 120s，上限 300s。 */
    public static long resolveTimeout(Object raw, long defaultTimeoutMs, long maxTimeoutMs) {
        long fallback = defaultTimeoutMs <= 0 ? 120_000L : defaultTimeoutMs;
        long max = maxTimeoutMs <= 0 ? 300_000L : maxTimeoutMs;
        if (raw instanceof Number number && number.longValue() > 0) {
            return Math.min(number.longValue(), max);
        }
        return Math.min(fallback, max);
    }

    /** 代理环境变量；proxy 为空时返回空 Map。 */
    public static Map<String, String> proxyEnvironment(String proxy) {
        if (StringUtils.isBlank(proxy)) {
            return Map.of();
        }
        return Map.of(
                "HTTP_PROXY", proxy,
                "HTTPS_PROXY", proxy,
                "ALL_PROXY", proxy,
                "http_proxy", proxy,
                "https_proxy", proxy,
                "all_proxy", proxy);
    }

    /** proxy 为空时需要显式清掉继承来的代理变量。 */
    public static List<String> proxyUnsetVariables(String proxy) {
        return StringUtils.isBlank(proxy) ? PROXY_ENVIRONMENT_VARIABLES : List.of();
    }

    /** argv 数组转换；含非字符串项时返回空数组。 */
    public static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new java.util.ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof String text)) {
                return List.of();
            }
            values.add(text);
        }
        return values;
    }
}

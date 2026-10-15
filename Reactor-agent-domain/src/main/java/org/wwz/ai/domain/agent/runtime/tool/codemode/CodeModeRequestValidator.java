package org.wwz.ai.domain.agent.runtime.tool.codemode;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Code Mode 入参校验。
 * <p>
 * 纯静态、无 Spring 依赖，便于单测；所有分支返回错误码而不是抛异常，
 * 由调用方决定是包装成工具失败还是记录到账本。
 * </p>
 */
public final class CodeModeRequestValidator {

    private CodeModeRequestValidator() {
    }

    public record Validation(String errorCode, String message) {

        public boolean isValid() {
            return errorCode == null;
        }

        public static Validation ok() {
            return new Validation(null, null);
        }

        public static Validation fail(CodeModeErrorCode code, String message) {
            return new Validation(code.code(), message);
        }
    }

    /**
     * source 与 workspaceFile 必须二选一。
     */
    public static Validation validateSourceTarget(String source,
                                                  String workspaceFile,
                                                  int maxSourceChars,
                                                  List<String> allowedScriptPrefixes) {
        boolean hasSource = StringUtils.isNotBlank(source);
        boolean hasFile = StringUtils.isNotBlank(workspaceFile);
        if (hasSource && hasFile) {
            return Validation.fail(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT,
                    "source 与 workspaceFile 只能二选一，不能同时提供");
        }
        if (!hasSource && !hasFile) {
            return Validation.fail(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT,
                    "source 与 workspaceFile 必须提供一个");
        }
        if (hasSource) {
            int limit = maxSourceChars > 0 ? maxSourceChars : 0;
            if (limit > 0 && source.length() > limit) {
                return Validation.fail(CodeModeErrorCode.CODE_MODE_INVALID_SOURCE,
                        "source 超过最大字符数 " + limit);
            }
            return Validation.ok();
        }
        return validateWorkspaceScriptPath(workspaceFile.trim(), allowedScriptPrefixes);
    }

    /**
     * workspaceFile 只能是相对路径、必须是 .js、必须落在脚本目录白名单内。
     */
    public static Validation validateWorkspaceScriptPath(String workspaceFile, List<String> allowedScriptPrefixes) {
        if (StringUtils.isBlank(workspaceFile)) {
            return Validation.fail(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT, "workspaceFile 不能为空");
        }
        String path = workspaceFile.trim().replace('\\', '/');
        if (path.startsWith("/") || path.startsWith("../") || path.contains("/../") || path.endsWith("/..")) {
            return Validation.fail(CodeModeErrorCode.CODE_MODE_WORKSPACE_FILE_DENIED,
                    "workspaceFile 必须是会话 workspace 内的相对路径，不能包含 ..");
        }
        if (path.length() > 2 && path.charAt(1) == ':') {
            return Validation.fail(CodeModeErrorCode.CODE_MODE_WORKSPACE_FILE_DENIED,
                    "workspaceFile 不接受宿主绝对路径");
        }
        if (!path.toLowerCase(Locale.ROOT).endsWith(".js")) {
            return Validation.fail(CodeModeErrorCode.CODE_MODE_WORKSPACE_FILE_DENIED,
                    "workspaceFile 必须是 .js 文件");
        }
        List<String> prefixes = allowedScriptPrefixes == null || allowedScriptPrefixes.isEmpty()
                ? List.of("scripts/", "jobs/")
                : allowedScriptPrefixes;
        boolean allowed = false;
        for (String prefix : prefixes) {
            if (StringUtils.isBlank(prefix)) {
                continue;
            }
            String normalized = prefix.trim().replace('\\', '/');
            if (!normalized.endsWith("/")) {
                normalized = normalized + "/";
            }
            if (path.startsWith(normalized)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            return Validation.fail(CodeModeErrorCode.CODE_MODE_WORKSPACE_FILE_DENIED,
                    "workspaceFile 只允许位于 " + String.join(", ", prefixes));
        }
        return Validation.ok();
    }

    /**
     * inputs 只能是 JSON 对象；缺省返回空 Map，非法类型返回 null。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalizeInputs(Object raw) {
        if (raw == null) {
            return new LinkedHashMap<>();
        }
        if (!(raw instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        ((Map<String, Object>) raw).forEach((key, value) -> {
            if (key != null) {
                inputs.put(String.valueOf(key), value);
            }
        });
        return inputs;
    }

    /**
     * 超时收敛到 [1, maxTimeoutMs]。
     */
    public static long resolveTimeoutMs(Object raw, long defaultTimeoutMs, long maxTimeoutMs) {
        long fallback = defaultTimeoutMs > 0 ? defaultTimeoutMs : 180_000L;
        long max = maxTimeoutMs > 0 ? maxTimeoutMs : 540_000L;
        long requested = fallback;
        if (raw instanceof Number number && number.longValue() > 0) {
            requested = number.longValue();
        } else if (raw instanceof String text && StringUtils.isNumeric(text.trim())) {
            long parsed = Long.parseLong(text.trim());
            if (parsed > 0) {
                requested = parsed;
            }
        }
        return Math.max(1L, Math.min(requested, max));
    }
}

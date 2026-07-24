package org.wwz.ai.domain.agent.runtime.llm;

import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话级冻结 system 正文，避免同 session 多次 Agent 构造时 system 字节漂移打爆 prompt cache。
 * key = sessionId + "|" + agentSlot + "|" + toolSignature
 */
public final class SessionPromptFreeze {

    private static final ConcurrentHashMap<String, String> SYSTEM_BY_KEY = new ConcurrentHashMap<>();
    private static final int MAX_KEYS = 512;

    private SessionPromptFreeze() {
    }

    public static String freezeSystem(String sessionId, String agentSlot, String toolSignature, String systemPrompt) {
        if (StringUtils.isBlank(systemPrompt)) {
            return systemPrompt;
        }
        if (StringUtils.isBlank(sessionId)) {
            return systemPrompt;
        }
        String key = StringUtils.defaultString(sessionId) + "|"
                + StringUtils.defaultString(agentSlot, "default") + "|"
                + StringUtils.defaultString(toolSignature);
        String frozen = SYSTEM_BY_KEY.putIfAbsent(key, systemPrompt);
        if (frozen != null) {
            return frozen;
        }
        trimIfNeeded();
        return systemPrompt;
    }

    public static String peek(String sessionId, String agentSlot, String toolSignature) {
        if (StringUtils.isBlank(sessionId)) {
            return null;
        }
        String key = StringUtils.defaultString(sessionId) + "|"
                + StringUtils.defaultString(agentSlot, "default") + "|"
                + StringUtils.defaultString(toolSignature);
        return SYSTEM_BY_KEY.get(key);
    }

    private static void trimIfNeeded() {
        if (SYSTEM_BY_KEY.size() <= MAX_KEYS) {
            return;
        }
        int remove = SYSTEM_BY_KEY.size() / 2;
        for (String k : SYSTEM_BY_KEY.keySet()) {
            SYSTEM_BY_KEY.remove(k);
            if (--remove <= 0) {
                break;
            }
        }
    }
}

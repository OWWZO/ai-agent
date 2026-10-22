package org.wwz.ai.domain.agent.ledger.model;

import org.apache.commons.lang3.StringUtils;

/**
 * 会话类型，决定会话在哪些入口可见。
 * <p>
 * 对齐 ZCode 的 {@code taskType}：可见性由<b>类型白名单</b>决定，
 * 而不是 {@code parent_session_id is null} 这类层级查询 —— 后者会把"有父但应当在列表中"的会话误杀，
 * 也会让"有父但必须隐藏"的子会话漏出来。
 *
 * @see #isListable()
 */
public enum SessionKind {

    /** 用户会话：侧栏列表可见，可继续对话。 */
    CONVERSATION("conversation"),

    /** 子智能体隐藏会话：真实落库、可独立订阅，但不进侧栏、不可续聊。 */
    SUBAGENT("subagent"),

    /**
     * 未知或缺失类型：一律 fail-closed —— 既不进侧栏列表，也不作为可交互会话放行。
     * <p>
     * 之所以不回落 {@link #CONVERSATION}：新增类型（如未来的 {@code subagent_v2}）若被误判成普通会话，
     * 会直接绕过列表过滤与只读闸门，属于权限 fail-open。
     */
    UNKNOWN("unknown");

    private final String code;

    SessionKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * 是否出现在侧栏会话列表。只有用户会话进列表。
     */
    public boolean isListable() {
        return this == CONVERSATION;
    }

    /**
     * 是否为子智能体隐藏会话（可被 owner 只读订阅，但不可续聊）。
     */
    public boolean isSubAgent() {
        return this == SUBAGENT;
    }

    /**
     * 解析持久化值。未知值、空值一律返回 {@link #UNKNOWN}（fail-closed），
     * 绝不回落为 {@link #CONVERSATION}。
     */
    public static SessionKind fromCode(String value) {
        if (StringUtils.isBlank(value)) {
            return UNKNOWN;
        }
        String normalized = value.trim();
        for (SessionKind kind : values()) {
            if (kind.code.equalsIgnoreCase(normalized)) {
                return kind;
            }
        }
        return UNKNOWN;
    }

    /**
     * 便捷判定：给定持久化值是否可进侧栏列表。未知值返回 {@code false}。
     */
    public static boolean isListableCode(String value) {
        return fromCode(value).isListable();
    }
}

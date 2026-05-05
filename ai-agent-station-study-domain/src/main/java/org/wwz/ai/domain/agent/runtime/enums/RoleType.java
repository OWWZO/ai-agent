package org.wwz.ai.domain.agent.runtime.enums;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 消息角色类型枚举
 */
public enum RoleType {
    USER("user"),
    SYSTEM("system"),
    ASSISTANT("assistant"),
    TOOL("tool");

    private final String value;

    private static final Set<String> ROLE_VALUES = new HashSet<>(Arrays.asList(
            "user", "system", "assistant", "tool"
    ));

    RoleType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 检查角色值是否有效
     */
    public static boolean isValid(String role) {
        return ROLE_VALUES.contains(role);
    }

    /**
     * 从字符串获取角色类型
     */
    public static RoleType fromString(String role) {
        for (RoleType type : RoleType.values()) {
            if (type.getValue().equals(role)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid role: " + role);
    }
}
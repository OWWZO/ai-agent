package org.wwz.ai.domain.agent.memory.ltm;

/**
 * 内置策展作用域。
 */
public enum CuratedMemoryScope {
    /** 代理环境与约定事实 */
    CURATED("curated"),
    /** 用户画像与偏好 */
    USER("user");

    private final String code;

    CuratedMemoryScope(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static CuratedMemoryScope fromCode(String code) {
        if (code == null || code.isBlank()) {
            return CURATED;
        }
        String normalized = code.trim().toLowerCase();
        for (CuratedMemoryScope scope : values()) {
            if (scope.code.equals(normalized) || scope.name().equalsIgnoreCase(normalized)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Unknown curated memory scope: " + code);
    }
}

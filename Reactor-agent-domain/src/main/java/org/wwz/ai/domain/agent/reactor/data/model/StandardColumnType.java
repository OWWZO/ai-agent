package org.wwz.ai.domain.agent.reactor.data.model;

public enum StandardColumnType {
    // 字符串
    VARCHAR(1),
    // 日期
    DATE(2),
    // 数字
    NUMBER(3),
    // 数字-带小数
    DECIMAL(4);

    private final int value;

    StandardColumnType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static StandardColumnType of(String type) {
        StandardColumnType[] enumArray = StandardColumnType.class.getEnumConstants();
        for (StandardColumnType em : enumArray) {
            if (em.name().equalsIgnoreCase(type)) {
                return em;
            }
        }
        return VARCHAR;
    }
}

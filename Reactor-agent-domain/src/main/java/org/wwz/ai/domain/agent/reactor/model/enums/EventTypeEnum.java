package org.wwz.ai.domain.agent.reactor.model.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * 延期保留的事件类别枚举，描述数据、思考、可输入、异常和调试事件。
 */
public enum EventTypeEnum {
    /**
     * 数据
     */
    CHART_DATA,
    /**
     * THINK流式消息
     */
    THINK,
    /**
     * 用户可输入
     */
    READY,
    /**
     * 异常
     */
    ERROR,
    /**
     * debug信息
     */
    DEBUG;

    public static EventTypeEnum of(String type) {
        for (EventTypeEnum authType : EventTypeEnum.class.getEnumConstants()) {
            if (StringUtils.equalsIgnoreCase(type, authType.name())) {
                return authType;
            }
        }
        throw new IllegalArgumentException("不支持类型");
    }
}

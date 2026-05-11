package org.wwz.ai.domain.agent.reactor.model.enums;

import org.apache.commons.lang3.StringUtils;

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

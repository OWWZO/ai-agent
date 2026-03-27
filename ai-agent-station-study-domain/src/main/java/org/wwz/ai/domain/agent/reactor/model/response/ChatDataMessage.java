package org.wwz.ai.domain.agent.reactor.model.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.reactor.model.enums.EventTypeEnum;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatDataMessage<T> implements Serializable {
    private static final long serialVersionUID = 7373974186330108440L;

    private String eventType;
    private T data;

    public static ChatDataMessage<Object> ofData(Object content) {
        return ChatDataMessage.builder()
                .eventType(EventTypeEnum.CHART_DATA.name())
                .data(content)
                .build();
    }


    public static ChatDataMessage<Object> ofReady(String content) {
        return ChatDataMessage.builder()
                .eventType(EventTypeEnum.READY.name())
                .data(content)
                .build();
    }

    public static ChatDataMessage<Object> ofError(String content) {
        return ChatDataMessage.builder()
                .eventType(EventTypeEnum.ERROR.name())
                .data(content)
                .build();
    }

    public static ChatDataMessage<Object> ofThink(String content) {
        return ChatDataMessage.builder()
                .eventType(EventTypeEnum.THINK.name())
                .data(content)
                .build();
    }

    public static ChatDataMessage<Object> ofStatus(String eventType,String content) {
        return ChatDataMessage.builder()
                .eventType(eventType)
                .data(content)
                .build();
    }
}
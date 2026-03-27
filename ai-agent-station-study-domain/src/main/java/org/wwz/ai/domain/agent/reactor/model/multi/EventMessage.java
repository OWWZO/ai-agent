package org.wwz.ai.domain.agent.reactor.model.multi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String taskId;
    private Integer taskOrder;
    private String messageId;
    private String messageType;// task、tool、html、file、
    private Integer messageOrder;
    private Object resultMap;
}

package org.wwz.ai.trigger.http.dataagent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataAgentApiResponseVO<T> {
    private int code;
    private T data;
}

package org.wwz.ai.domain.agent.runtime.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolCall {
    private String id;
    private String type;
    private Function function;

    /**
     * 函数信息类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Function {
        private String name;
        private String arguments;
    }
}
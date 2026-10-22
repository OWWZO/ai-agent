package org.wwz.ai.domain.agent.runtime.tool;

import java.util.Map;

/**
 * 工具基接口
 */
public interface BaseTool {
    String getName();

    String getDescription();

    Map<String, Object> toParams();

    Object execute(Object input);
}
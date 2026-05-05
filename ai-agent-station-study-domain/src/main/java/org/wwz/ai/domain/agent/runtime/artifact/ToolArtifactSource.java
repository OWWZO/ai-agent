package org.wwz.ai.domain.agent.runtime.artifact;

import lombok.Builder;
import lombok.Value;

/**
 * 单次工具调用的运行时来源快照。
 * 该对象创建后不再修改，用于跨线程传递文件产物归属。
 */
@Value
@Builder(toBuilder = true)
public class ToolArtifactSource {
    String sessionId;
    String requestId;
    String toolCallId;
    String toolName;
}

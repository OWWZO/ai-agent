package org.wwz.ai.domain.agent.reactor.service.tooloutput;

import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolOutputView;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolStructuredOutput;

import java.util.Optional;

/**
 * rich tool 输出读取契约。
 */
public interface ToolOutputReader {

    Optional<ToolStructuredOutput> readByInvocationId(String toolName, Long toolInvocationId);

    Optional<ToolOutputView> readDirect(String requestId, String toolCallId);
}

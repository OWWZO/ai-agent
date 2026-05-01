package org.wwz.ai.domain.agent.reactor.service.tooloutput;

import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolOutputPersistCommand;

/**
 * rich tool 输出写入契约。
 */
public interface ToolOutputWriter {

    void write(ToolOutputPersistCommand command);
}

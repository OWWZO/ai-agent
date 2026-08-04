package org.wwz.ai.domain.agent.ledger.tooloutput;

import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputPersistCommand;

/**
 * 结构化工具输出写入端口。
 * <p>将工具专属输出持久化到对应的 tool-output 表，避免把大对象重新塞回通用 invocation 记录。</p>
 */
public interface ToolOutputWriter {

    void write(ToolOutputPersistCommand command);

    void writeOrThrow(ToolOutputPersistCommand command);
}

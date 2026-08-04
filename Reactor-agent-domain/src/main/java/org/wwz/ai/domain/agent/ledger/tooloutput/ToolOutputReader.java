package org.wwz.ai.domain.agent.ledger.tooloutput;

import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputView;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolStructuredOutput;

import java.util.Optional;

/**
 * 结构化工具输出读取端口。
 * <p>读取的是 Execution Ledger 的 tool-output 投影，不负责生成新的执行事实。</p>
 */
public interface ToolOutputReader {

    Optional<ToolStructuredOutput> readByInvocationId(String toolName, Long toolInvocationId);

    Optional<ToolOutputView> readDirect(String requestId, String toolCallId);
}

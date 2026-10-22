package org.wwz.ai.domain.agent.ledger.tooloutput;

import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputView;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolStructuredOutput;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 结构化工具输出读取端口。
 * <p>读取的是 Execution Ledger 的 tool-output 投影，不负责生成新的执行事实。</p>
 */
public interface ToolOutputReader {

    Optional<ToolStructuredOutput> readByInvocationId(String toolName, Long toolInvocationId);

    /**
     * 批量读取 rich tool 输出。传入 {@code null} artifact 时由实现自行批量补查；非空时复用调用方已加载的 artifact。
     */
    Map<Long, ToolStructuredOutput> readByInvocationIds(List<ToolInvocationView> invocations,
                                                        List<ArtifactView> artifacts);

    Optional<ToolOutputView> readDirect(String requestId, String toolCallId);
}

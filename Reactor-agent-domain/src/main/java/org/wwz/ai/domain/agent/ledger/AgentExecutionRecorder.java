package org.wwz.ai.domain.agent.ledger;

import org.wwz.ai.domain.agent.ledger.model.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord;

import java.util.List;
import java.util.Map;

/**
 * 执行账本统一写入契约。
 */
public interface AgentExecutionRecorder {

    Long createRun(DialogueRunStartRecord record);

    void finishRun(DialogueRunFinishRecord record);

    Long createLlmInvocation(LlmInvocationStartRecord record);

    void finishLlmInvocation(LlmInvocationFinishRecord record);

    Map<String, Long> createToolInvocations(ToolInvocationBatchStartRecord record);

    void finishToolInvocation(ToolInvocationFinishRecord record);

    void recordArtifacts(List<ArtifactRecordCommand> records);

    void recordArtifactsOrThrow(List<ArtifactRecordCommand> records);
}

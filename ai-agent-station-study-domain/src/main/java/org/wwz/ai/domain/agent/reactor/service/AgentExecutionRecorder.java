package org.wwz.ai.domain.agent.reactor.service;

import org.wwz.ai.domain.agent.reactor.model.ledger.ArtifactRecordCommand;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunStartRecord;
import org.wwz.ai.domain.agent.reactor.model.ledger.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.reactor.model.ledger.LlmInvocationStartRecord;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationBatchStartRecord;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationFinishRecord;

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

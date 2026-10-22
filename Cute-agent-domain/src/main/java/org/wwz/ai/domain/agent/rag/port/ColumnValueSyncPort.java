package org.wwz.ai.domain.agent.rag.port;

import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelInfo;
import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelSchema;

import java.util.List;

/**
 * Column-value synchronization capability. SQL and index protocol details stay in Infrastructure.
 */
public interface ColumnValueSyncPort {

    void syncColumnValues(ChatModelInfo modelInfo, List<ChatModelSchema> schemas);
}

package org.wwz.ai.domain.agent.rag;

import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;
import org.wwz.ai.domain.agent.reactor.data.QueryResult;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatQueryData;
import org.wwz.ai.domain.agent.reactor.data.dto.ColumnEsRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.ColumnVectorRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.NL2SQLReq;
import org.wwz.ai.domain.agent.reactor.model.req.DataAgentChatReq;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 数据问答稳定领域 seam。
 * 收口 schema recall、NL2SQL、问数取数与模型预览等查询能力。
 */
public interface DataAgentQueryService {

    NL2SQLReq queryAllSchemaNl2SqlReq();

    List<Map<String, Object>> vectorRecall(ColumnVectorRecallReq req);

    List<Map<String, Object>> esRecall(ColumnEsRecallReq req) throws IOException;

    void chatQuery(DataAgentChatReq req, AgentMessageStream stream) throws Exception;

    List<ChatQueryData> apiChatQuery(DataAgentChatReq req);

    Object testQuery(DataAgentChatReq req) throws Exception;

    NL2SQLReq getNl2SqlReq(String query) throws Exception;

    List<?> queryAllModelsWithSchema();

    QueryResult previewData(String modelCode) throws Exception;
}

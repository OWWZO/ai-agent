package org.wwz.ai.application.agent.dataquery;

import org.wwz.ai.application.agent.stream.AgentSessionStream;
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
 * 数据问答应用服务接口。
 * 为 dataagent 入口提供唯一 case seam，不允许入口层继续回流到已删除的 DataAgent bridge。
 */
public interface IDataAgentApplicationService {

    NL2SQLReq queryAllSchemaNl2SqlReq();

    List<Map<String, Object>> vectorRecall(ColumnVectorRecallReq req);

    List<Map<String, Object>> esRecall(ColumnEsRecallReq req) throws IOException;

    void chatQuery(DataAgentChatReq req, AgentSessionStream stream) throws Exception;

    List<ChatQueryData> apiChatQuery(DataAgentChatReq req);

    Object testQuery(DataAgentChatReq req) throws Exception;

    NL2SQLReq getNl2SqlReq(String query) throws Exception;

    List<?> queryAllModelsWithSchema();

    QueryResult previewData(String modelCode) throws Exception;
}

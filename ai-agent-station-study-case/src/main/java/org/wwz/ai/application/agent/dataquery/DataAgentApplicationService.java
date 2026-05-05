package org.wwz.ai.application.agent.dataquery;

import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.rag.DataAgentQueryService;
import org.wwz.ai.domain.agent.reactor.data.QueryResult;
import org.wwz.ai.domain.agent.reactor.data.dto.ChatQueryData;
import org.wwz.ai.domain.agent.reactor.data.dto.ColumnEsRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.ColumnVectorRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.NL2SQLReq;
import org.wwz.ai.domain.agent.reactor.model.req.DataAgentChatReq;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 数据问答应用服务。
 * 通过稳定 rag seam 承接数据问答主链路，避免 case 层继续直接依赖 legacy dataagent bridge。
 */
@Service
public class DataAgentApplicationService implements IDataAgentApplicationService {

    @Resource
    private DataAgentQueryService dataAgentQueryService;

    @Override
    public NL2SQLReq queryAllSchemaNl2SqlReq() {
        return dataAgentQueryService.queryAllSchemaNl2SqlReq();
    }

    @Override
    public List<Map<String, Object>> vectorRecall(ColumnVectorRecallReq req) {
        return dataAgentQueryService.vectorRecall(req);
    }

    @Override
    public List<Map<String, Object>> esRecall(ColumnEsRecallReq req) throws IOException {
        return dataAgentQueryService.esRecall(req);
    }

    @Override
    public void chatQuery(DataAgentChatReq req, AgentSessionStream stream) throws Exception {
        dataAgentQueryService.chatQuery(req, stream);
    }

    @Override
    public List<ChatQueryData> apiChatQuery(DataAgentChatReq req) {
        return dataAgentQueryService.apiChatQuery(req);
    }

    @Override
    public Object testQuery(DataAgentChatReq req) throws Exception {
        return dataAgentQueryService.testQuery(req);
    }

    @Override
    public NL2SQLReq getNl2SqlReq(String query) throws Exception {
        return dataAgentQueryService.getNl2SqlReq(query);
    }

    @Override
    public List<?> queryAllModelsWithSchema() {
        return dataAgentQueryService.queryAllModelsWithSchema();
    }

    @Override
    public QueryResult previewData(String modelCode) throws Exception {
        return dataAgentQueryService.previewData(modelCode);
    }
}

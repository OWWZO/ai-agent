package org.wwz.ai.application.agent.dataquery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.dataquery.command.ColumnSchemaRecallCommand;
import org.wwz.ai.application.agent.dataquery.command.ColumnValueRecallCommand;
import org.wwz.ai.application.agent.dataquery.command.DataAgentChatCommand;
import org.wwz.ai.application.agent.dataquery.command.DataQueryCommand;
import org.wwz.ai.application.agent.dataquery.mapper.DataQueryMapper;
import org.wwz.ai.application.agent.dataquery.result.DataQueryModelResult;
import org.wwz.ai.application.agent.dataquery.result.DataQueryResult;
import org.wwz.ai.application.agent.dataquery.result.Nl2SqlQueryResult;
import org.wwz.ai.application.agent.dataquery.result.SqlQueryResult;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;
import org.wwz.ai.domain.agent.rag.DataAgentQueryService;
import org.wwz.ai.domain.agent.rag.model.query.DataAgentChatQuery;
import org.wwz.ai.domain.agent.rag.model.query.DataQueryStreamEvent;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Data Query use-case orchestration and Domain-to-Case translation. */
@Service
@RequiredArgsConstructor
public class DataAgentApplicationService implements IDataAgentApplicationService {

    private final DataAgentQueryService dataAgentQueryService;
    private final DataQueryMapper dataQueryMapper;

    @Override
    public Nl2SqlQueryResult queryAllSchema() {
        return dataQueryMapper.toResult(dataAgentQueryService.queryAllSchema());
    }

    @Override
    public List<Map<String, Object>> vectorRecall(ColumnSchemaRecallCommand command) {
        return dataAgentQueryService.recallSchemaColumns(dataQueryMapper.toDomain(command));
    }

    @Override
    public List<Map<String, Object>> esRecall(ColumnValueRecallCommand command) throws IOException {
        return dataAgentQueryService.recallColumnValues(dataQueryMapper.toDomain(command));
    }

    @Override
    public void chatQuery(DataAgentChatCommand command, AgentSessionStream stream) throws Exception {
        dataAgentQueryService.chatQuery(dataQueryMapper.toDomain(command), domainStream(stream));
    }

    @Override
    public List<DataQueryResult> apiChatQuery(DataAgentChatCommand command) {
        return dataQueryMapper.mapDataResults(dataAgentQueryService.apiChatQuery(dataQueryMapper.toDomain(command)));
    }

    @Override
    public SqlQueryResult testQuery(DataQueryCommand command) {
        return dataQueryMapper.mapSqlResult(dataAgentQueryService.testQuery(
                new DataAgentChatQuery(command.query(), null)));
    }

    @Override
    public Nl2SqlQueryResult buildNl2SqlQuery(DataQueryCommand command) throws Exception {
        return dataQueryMapper.toResult(dataAgentQueryService.buildNl2SqlQuery(command.query()));
    }

    @Override
    public List<DataQueryModelResult> queryAllModelsWithSchema() {
        return dataQueryMapper.mapModels(dataAgentQueryService.queryAllModelsWithSchema());
    }

    @Override
    public SqlQueryResult previewData(String modelCode) throws Exception {
        return dataQueryMapper.mapSqlResult(dataAgentQueryService.previewData(modelCode));
    }

    private AgentMessageStream domainStream(AgentSessionStream stream) {
        return new AgentMessageStream() {
            @Override
            public void send(Object payload) throws Exception {
                if (!(payload instanceof DataQueryStreamEvent event)) {
                    throw new IllegalArgumentException("Unexpected Data Query stream event type");
                }
                stream.send(dataQueryMapper.mapStreamEvent(event));
            }

            @Override
            public void complete() {
                stream.complete();
            }

            @Override
            public void completeWithError(Throwable throwable) {
                stream.completeWithError(throwable);
            }

            @Override
            public void onAbort(Runnable abortHandler) {
                stream.onAbort(abortHandler);
            }

            @Override
            public boolean isAborted() {
                return stream.isAborted();
            }
        };
    }
}

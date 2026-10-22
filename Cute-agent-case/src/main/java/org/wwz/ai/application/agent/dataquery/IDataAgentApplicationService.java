package org.wwz.ai.application.agent.dataquery;

import org.wwz.ai.application.agent.dataquery.command.ColumnSchemaRecallCommand;
import org.wwz.ai.application.agent.dataquery.command.ColumnValueRecallCommand;
import org.wwz.ai.application.agent.dataquery.command.DataAgentChatCommand;
import org.wwz.ai.application.agent.dataquery.command.DataQueryCommand;
import org.wwz.ai.application.agent.dataquery.result.DataQueryModelResult;
import org.wwz.ai.application.agent.dataquery.result.DataQueryResult;
import org.wwz.ai.application.agent.dataquery.result.Nl2SqlQueryResult;
import org.wwz.ai.application.agent.dataquery.result.SqlQueryResult;
import org.wwz.ai.application.agent.stream.AgentSessionStream;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Application seam for Data Query use cases. */
public interface IDataAgentApplicationService {
    Nl2SqlQueryResult queryAllSchema();

    List<Map<String, Object>> vectorRecall(ColumnSchemaRecallCommand command);

    List<Map<String, Object>> esRecall(ColumnValueRecallCommand command) throws IOException;

    void chatQuery(DataAgentChatCommand command, AgentSessionStream stream) throws Exception;

    List<DataQueryResult> apiChatQuery(DataAgentChatCommand command);

    SqlQueryResult testQuery(DataQueryCommand command);

    Nl2SqlQueryResult buildNl2SqlQuery(DataQueryCommand command) throws Exception;

    List<DataQueryModelResult> queryAllModelsWithSchema();

    SqlQueryResult previewData(String modelCode) throws Exception;
}

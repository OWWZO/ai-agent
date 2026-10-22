package org.wwz.ai.trigger.http.dataagent.mapper;

import org.springframework.stereotype.Component;
import org.wwz.ai.application.agent.dataquery.command.ColumnSchemaRecallCommand;
import org.wwz.ai.application.agent.dataquery.command.ColumnValueRecallCommand;
import org.wwz.ai.application.agent.dataquery.command.DataAgentChatCommand;
import org.wwz.ai.trigger.http.dataagent.vo.ColumnValueRecallRequestVO;
import org.wwz.ai.trigger.http.dataagent.vo.DataAgentChatRequestVO;
import org.wwz.ai.trigger.http.dataagent.vo.VectorRecallRequestVO;

@Component
public class DataAgentRequestMapper {
    public DataAgentChatCommand toCommand(DataAgentChatRequestVO request) {
        return new DataAgentChatCommand(request.getContent(), request.getTraceId());
    }

    public ColumnSchemaRecallCommand toCommand(VectorRecallRequestVO request) {
        return new ColumnSchemaRecallCommand(request.getQuery(), request.getLimit(), request.getScoreThreshold(),
                request.getTimeout(), request.getModelCodeList());
    }

    public ColumnValueRecallCommand toCommand(ColumnValueRecallRequestVO request) {
        return new ColumnValueRecallCommand(request.getQuery(), request.getModelCodeList(), request.getLimit());
    }
}

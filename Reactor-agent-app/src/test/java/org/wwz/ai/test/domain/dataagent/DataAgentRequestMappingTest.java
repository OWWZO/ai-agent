package org.wwz.ai.test.domain.dataagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.dataquery.command.ColumnSchemaRecallCommand;
import org.wwz.ai.application.agent.dataquery.command.ColumnValueRecallCommand;
import org.wwz.ai.application.agent.dataquery.command.DataAgentChatCommand;
import org.wwz.ai.trigger.http.dataagent.mapper.DataAgentRequestMapper;
import org.wwz.ai.trigger.http.dataagent.vo.ColumnValueRecallRequestVO;
import org.wwz.ai.trigger.http.dataagent.vo.DataAgentChatRequestVO;
import org.wwz.ai.trigger.http.dataagent.vo.VectorRecallRequestVO;

import java.util.List;

public class DataAgentRequestMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataAgentRequestMapper mapper = new DataAgentRequestMapper();

    @Test
    public void mapsChatRequestFieldsIntoApplicationCommand() throws Exception {
        DataAgentChatRequestVO request = objectMapper.readValue(
                "{\"content\":\"sales by month\",\"traceId\":\"trace-7\"}", DataAgentChatRequestVO.class);

        DataAgentChatCommand command = mapper.toCommand(request);

        Assert.assertEquals("sales by month", command.content());
        Assert.assertEquals("trace-7", command.traceId());
    }

    @Test
    public void mapsVectorRecallDefaultsAndFiltersIntoTypedCommand() throws Exception {
        VectorRecallRequestVO request = objectMapper.readValue(
                "{\"query\":\"revenue\",\"modelCodeList\":[\"sales\"]}", VectorRecallRequestVO.class);

        ColumnSchemaRecallCommand command = mapper.toCommand(request);

        Assert.assertEquals("revenue", command.query());
        Assert.assertEquals(Integer.valueOf(100), command.limit());
        Assert.assertEquals(Float.valueOf(0.5F), command.scoreThreshold());
        Assert.assertEquals(Long.valueOf(50000L), command.timeout());
        Assert.assertEquals(List.of("sales"), command.modelCodeList());
    }

    @Test
    public void mapsColumnRecallFieldsWithoutExposingHttpVoToCase() throws Exception {
        ColumnValueRecallRequestVO request = objectMapper.readValue(
                "{\"query\":\"Shanghai\",\"modelCodeList\":[\"sales\"],\"limit\":25}",
                ColumnValueRecallRequestVO.class);

        ColumnValueRecallCommand command = mapper.toCommand(request);

        Assert.assertEquals("Shanghai", command.query());
        Assert.assertEquals(List.of("sales"), command.modelCodeList());
        Assert.assertEquals(25, command.limit());
    }
}

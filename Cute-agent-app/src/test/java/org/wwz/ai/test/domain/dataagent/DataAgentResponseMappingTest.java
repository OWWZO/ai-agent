package org.wwz.ai.test.domain.dataagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.dataquery.result.DataAgentStreamResult;
import org.wwz.ai.application.agent.dataquery.result.Nl2SqlQueryResult;
import org.wwz.ai.trigger.http.dataagent.mapper.DataAgentResponseMapper;
import org.wwz.ai.trigger.http.dataagent.vo.DataAgentChatMessageResponseVO;
import org.wwz.ai.trigger.http.dataagent.vo.Nl2SqlQueryResponseVO;

import java.util.List;

public class DataAgentResponseMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataAgentResponseMapper mapper = new DataAgentResponseMapper();

    @Test
    public void preservesNl2SqlResponseJsonFields() throws Exception {
        Nl2SqlQueryResult result = new Nl2SqlQueryResult(
                "request-1", "sales", List.of("sales_model"), List.of(), "today", "trace-1",
                "only_recall", true, "", "mysql", true, false);

        Nl2SqlQueryResponseVO response = mapper.toResponse(result);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        Assert.assertEquals("request-1", json.get("requestId").asText());
        Assert.assertEquals("sales", json.get("query").asText());
        Assert.assertEquals("trace-1", json.get("traceId").asText());
        Assert.assertEquals("mysql", json.get("dbType").asText());
        Assert.assertTrue(json.get("useVector").asBoolean());
        Assert.assertFalse(json.get("useElastic").asBoolean());
        Assert.assertTrue(json.has("schemaInfo"));
        Assert.assertTrue(json.has("currentDateInfo"));
        Assert.assertTrue(json.has("recallType"));
        Assert.assertTrue(json.has("stream"));
        Assert.assertTrue(json.has("userInfo"));
    }

    @Test
    public void preservesSseEnvelopeAndEventTypeFields() throws Exception {
        DataAgentChatMessageResponseVO response = mapper.toResponse(
                new DataAgentStreamResult("CHART_DATA", List.of("row")));
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        Assert.assertEquals("CHART_DATA", json.get("eventType").asText());
        Assert.assertEquals("row", json.get("data").get(0).asText());
        Assert.assertEquals(2, json.size());
    }
}

package org.wwz.ai.test.domain.dataagent;

import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.rag.model.query.Nl2SqlQuery;
import org.wwz.ai.domain.agent.rag.model.query.Nl2SqlResult;
import org.wwz.ai.domain.agent.rag.model.schema.DataQueryModelDescriptor;

import java.util.List;

public class DataQueryWireContractTest {

    @Test
    public void serializesNl2SqlRequestWithExistingFieldNames() {
        DataQueryModelDescriptor model = new DataQueryModelDescriptor();
        model.setModelCode("sales_model");
        Nl2SqlQuery query = new Nl2SqlQuery();
        query.setRequestId("request-1");
        query.setTraceId("trace-1");
        query.setQuery("sales by month");
        query.setModelCodeList(List.of("sales_model"));
        query.setSchemaInfo(List.of(model));
        query.setDbType("mysql");
        query.setUseVector(true);
        query.setUseElastic(false);

        JSONObject json = JSONObject.parseObject(JSONObject.toJSONString(query));

        Assert.assertEquals("request-1", json.getString("requestId"));
        Assert.assertEquals("trace-1", json.getString("traceId"));
        Assert.assertEquals("sales by month", json.getString("query"));
        Assert.assertEquals("mysql", json.getString("dbType"));
        Assert.assertTrue(json.getBooleanValue("useVector"));
        Assert.assertFalse(json.getBooleanValue("useElastic"));
        Assert.assertTrue(json.containsKey("schemaInfo"));
        Assert.assertTrue(json.containsKey("currentDateInfo"));
        Assert.assertTrue(json.containsKey("recallType"));
    }

    @Test
    public void readsNl2SqlSnakeCaseResultFields() {
        Nl2SqlResult result = JSONObject.parseObject("""
                {"code":200,"request_id":"request-1","nl2sql_think":"reasoning",
                 "status":"data","err_msg":"","data":[{"query":"sales","nl2sql":"SELECT 1"}]}
                """, Nl2SqlResult.class);

        Assert.assertEquals("request-1", result.getRequestId());
        Assert.assertEquals("reasoning", result.getNl2sqlThink());
        Assert.assertEquals("", result.getErrorMessage());
        Assert.assertEquals("sales", result.getData().get(0).getQuery());
        Assert.assertEquals("SELECT 1", result.getData().get(0).getNl2sql());
    }
}

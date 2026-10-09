package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.rag.model.config.ColumnValueRecallSettings;
import org.wwz.ai.domain.agent.rag.model.config.DataQuerySettings;
import org.wwz.ai.domain.agent.rag.model.config.VectorRecallSettings;
import org.wwz.ai.domain.agent.rag.model.query.Nl2SqlQuery;
import org.wwz.ai.domain.agent.rag.TableRagService;

import java.util.List;

/**
 * table_rag 空召回降级回归测试。
 */
public class TableRagServiceFallbackTest {

    @Test
    public void shouldReturnEmptyListWhenTableRagRespondsWithEmptyData() throws Exception {
        VectorRecallSettings qdrantConfig = new VectorRecallSettings();
        qdrantConfig.setEnable(true);
        ColumnValueRecallSettings esConfig = new ColumnValueRecallSettings();
        esConfig.setEnable(false);
        DataQuerySettings settings = new DataQuerySettings();
        settings.setAgentUrl("http://127.0.0.1:1601");
        settings.setQdrantConfig(qdrantConfig);
        settings.setEsConfig(esConfig);

        RemoteHttpPort remoteHttpPort = Mockito.mock(RemoteHttpPort.class);
        Mockito.when(remoteHttpPort.execute(Mockito.any()))
                .thenReturn("{\"code\":200,\"data\":[],\"requestId\":\"req-1\"}");

        TableRagService tableRagService = new TableRagService(settings, remoteHttpPort);

        Nl2SqlQuery req = new Nl2SqlQuery();
        req.setTraceId("trace-1");
        req.setRequestId("req-1");

        List<?> result = tableRagService.tableRag(req);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }
}

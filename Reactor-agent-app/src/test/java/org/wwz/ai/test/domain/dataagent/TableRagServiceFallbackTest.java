package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.config.data.EsConfig;
import org.wwz.ai.domain.agent.reactor.config.data.QdrantConfig;
import org.wwz.ai.domain.agent.reactor.data.dto.NL2SQLReq;
import org.wwz.ai.domain.agent.rag.TableRagService;

import java.util.List;

/**
 * table_rag 空召回降级回归测试。
 */
public class TableRagServiceFallbackTest {

    @Test
    public void shouldReturnEmptyListWhenTableRagRespondsWithEmptyData() throws Exception {
        TableRagService tableRagService = new TableRagService();
        DataAgentConfig dataAgentConfig = new DataAgentConfig();
        QdrantConfig qdrantConfig = new QdrantConfig();
        qdrantConfig.setEnable(true);
        EsConfig esConfig = new EsConfig();
        esConfig.setEnable(false);
        dataAgentConfig.setAgentUrl("http://127.0.0.1:1601");
        dataAgentConfig.setQdrantConfig(qdrantConfig);
        dataAgentConfig.setEsConfig(esConfig);

        RemoteHttpPort remoteHttpPort = Mockito.mock(RemoteHttpPort.class);
        Mockito.when(remoteHttpPort.execute(Mockito.any()))
                .thenReturn("{\"code\":200,\"data\":[],\"requestId\":\"req-1\"}");

        ReflectionTestUtils.setField(tableRagService, "dataAgentConfig", dataAgentConfig);
        ReflectionTestUtils.setField(tableRagService, "remoteHttpPort", remoteHttpPort);

        NL2SQLReq req = new NL2SQLReq();
        req.setTraceId("trace-1");
        req.setRequestId("req-1");

        List<?> result = tableRagService.tableRag(req);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }
}

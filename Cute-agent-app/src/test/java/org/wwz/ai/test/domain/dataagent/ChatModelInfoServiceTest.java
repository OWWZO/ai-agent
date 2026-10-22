package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.rag.port.ColumnValueIndexAdminPort;
import org.wwz.ai.domain.agent.rag.port.ColumnValueSyncPort;
import org.wwz.ai.domain.agent.rag.port.DataQueryExecutionPort;
import org.wwz.ai.domain.agent.rag.port.DataQueryMetadataPort;
import org.wwz.ai.domain.agent.rag.port.IChatModelMetadataRepository;
import org.wwz.ai.domain.agent.rag.port.TextEmbeddingPort;
import org.wwz.ai.domain.agent.rag.port.VectorIndexAdminPort;
import org.wwz.ai.domain.agent.rag.service.ChatModelInfoService;
import org.wwz.ai.domain.agent.rag.service.ChatModelSchemaService;
import org.wwz.ai.domain.agent.rag.model.config.DataQueryModelConfig;
import org.wwz.ai.domain.agent.rag.model.config.DataQuerySettings;
import org.wwz.ai.domain.agent.rag.model.schema.DataQueryTableColumn;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChatModelInfoServiceTest {

    @Test
    public void shouldLoadTableModelSchemaFromConfiguredTable() {
        DataQuerySettings dataQuerySettings = new DataQuerySettings();
        dataQuerySettings.getDbConfig().setSchema("sales");
        DataQueryMetadataPort metadataPort = Mockito.mock(DataQueryMetadataPort.class);
        List<DataQueryTableColumn> expected = List.of(DataQueryTableColumn.builder().name("amount").dataType("decimal").build());
        when(metadataPort.queryColumns("sales_data", "sales")).thenReturn(expected);
        ChatModelInfoService service = service(dataQuerySettings, metadataPort);
        DataQueryModelConfig modelConfig = new DataQueryModelConfig();
        modelConfig.setType("table");
        modelConfig.setContent("sales_data");

        List<DataQueryTableColumn> actual = service.getModelSchema(modelConfig);

        Assert.assertSame(expected, actual);
        verify(metadataPort).queryColumns("sales_data", "sales");
        Mockito.verify(metadataPort, Mockito.never()).getTableColumnsOfSql(Mockito.anyString(), Mockito.anyInt());
    }

    @Test
    public void shouldLoadSqlModelSchemaFromConfiguredQuery() {
        DataQuerySettings dataQuerySettings = new DataQuerySettings();
        DataQueryMetadataPort metadataPort = Mockito.mock(DataQueryMetadataPort.class);
        List<DataQueryTableColumn> expected = List.of(DataQueryTableColumn.builder().name("total").dataType("decimal").build());
        when(metadataPort.getTableColumnsOfSql("SELECT SUM(amount) total FROM sales_data", 1))
                .thenReturn(expected);
        ChatModelInfoService service = service(dataQuerySettings, metadataPort);
        DataQueryModelConfig modelConfig = new DataQueryModelConfig();
        modelConfig.setType("sql");
        modelConfig.setContent("SELECT SUM(amount) total FROM sales_data");

        List<DataQueryTableColumn> actual = service.getModelSchema(modelConfig);

        Assert.assertSame(expected, actual);
        verify(metadataPort).getTableColumnsOfSql("SELECT SUM(amount) total FROM sales_data", 1);
        Mockito.verify(metadataPort, Mockito.never()).queryColumns(Mockito.anyString(), Mockito.anyString());
    }

    private ChatModelInfoService service(DataQuerySettings dataQuerySettings, DataQueryMetadataPort metadataPort) {
        return new ChatModelInfoService(
                Mockito.mock(IChatModelMetadataRepository.class),
                dataQuerySettings,
                metadataPort,
                Mockito.mock(DataQueryExecutionPort.class),
                Mockito.mock(ChatModelSchemaService.class),
                Mockito.mock(TextEmbeddingPort.class),
                Mockito.mock(VectorIndexAdminPort.class),
                Mockito.mock(ColumnValueIndexAdminPort.class),
                Mockito.mock(ColumnValueSyncPort.class));
    }
}

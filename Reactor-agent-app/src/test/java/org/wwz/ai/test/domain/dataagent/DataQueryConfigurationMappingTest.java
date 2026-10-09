package org.wwz.ai.test.domain.dataagent;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.wwz.ai.domain.agent.rag.model.config.DataQueryModelConfig;
import org.wwz.ai.domain.agent.rag.model.config.DataQuerySettings;
import org.wwz.ai.infrastructure.dataquery.embedding.DataQueryEmbeddingProperties;
import org.wwz.ai.infrastructure.dataquery.jdbc.JdbcDataSourceProperties;
import org.wwz.ai.infrastructure.dataquery.vector.QdrantProperties;

import java.util.List;
import java.util.Map;

public class DataQueryConfigurationMappingTest {

    @Test
    public void bindsDomainSettingsAndInfrastructurePropertiesFromExistingKeys() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "autobots.data-agent.agent-url", "http://tool.local",
                "autobots.data-agent.force-refresh", true,
                "autobots.data-agent.db-config.type", "mysql",
                "autobots.data-agent.db-config.schema", "sales",
                "autobots.data-agent.db-config.url", "jdbc:mysql://db.local/sales",
                "autobots.data-agent.qdrant-config.enable", true,
                "autobots.data-agent.qdrant-config.url", "https://qdrant.local",
                "autobots.data-agent.qdrant-config.embedding-url", "http://embedding.local",
                "autobots.data-agent.es-config.enable", false)));

        DataQuerySettings settings = binder.bind("autobots.data-agent", Bindable.of(DataQuerySettings.class)).get();
        JdbcDataSourceProperties jdbc = binder.bind("autobots.data-agent.db-config", Bindable.of(JdbcDataSourceProperties.class)).get();
        QdrantProperties qdrant = binder.bind("autobots.data-agent.qdrant-config", Bindable.of(QdrantProperties.class)).get();
        DataQueryEmbeddingProperties embedding = binder.bind("autobots.data-agent", Bindable.of(DataQueryEmbeddingProperties.class)).get();

        Assert.assertEquals("mysql", settings.getDbConfig().getType());
        Assert.assertEquals("sales", settings.getDbConfig().getSchema());
        Assert.assertTrue(settings.getForceRefresh());
        Assert.assertEquals("jdbc:mysql://db.local/sales", jdbc.getUrl());
        Assert.assertEquals("https://qdrant.local", qdrant.getUrl());
        Assert.assertTrue(qdrant.getEnable());
        Assert.assertEquals("http://embedding.local", embedding.getQdrantConfig().getEmbeddingUrl());
    }

    @Test
    public void retainsBuiltInBusinessPromptValue() {
        DataQuerySettings settings = new DataQuerySettings();
        DataQueryModelConfig model = new DataQueryModelConfig();
        model.setId("t_qtpbgamccmrctthlurauclckq");
        settings.setModelList(List.of(model));

        Assert.assertEquals("order_date为日维度数据，如果统计月份要使用DATE_FORMAT(`order_date`, '%Y-%m')",
                settings.getModelList().get(0).getBusinessPrompt());
    }
}

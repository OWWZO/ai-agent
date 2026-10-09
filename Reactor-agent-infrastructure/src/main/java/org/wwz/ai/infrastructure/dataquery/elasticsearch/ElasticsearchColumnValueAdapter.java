package org.wwz.ai.infrastructure.dataquery.elasticsearch;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueDocument;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueHit;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueSearchRequest;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueSearchResult;
import org.wwz.ai.domain.agent.rag.port.ColumnValueIndexAdminPort;
import org.wwz.ai.domain.agent.rag.port.ColumnValueSearchPort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 列值检索与索引管理适配器。
 */
@Slf4j
@Component
public class ElasticsearchColumnValueAdapter implements ColumnValueSearchPort, ColumnValueIndexAdminPort {

    private final RestHighLevelClient client;

    @Autowired
    public ElasticsearchColumnValueAdapter(ObjectProvider<RestHighLevelClient> clientProvider) {
        this.client = clientProvider.getIfAvailable();
    }

    @Override
    public boolean isAvailable() {
        return client != null;
    }

    @Override
    public void initializeIndex() {
        if (!isAvailable()) {
            throw new IllegalStateException("ES client is unavailable");
        }
        if (!indexExists(ElasticsearchDataQueryDefaults.COLUMN_VALUE_INDEX_NAME)
                && !createIndex(ElasticsearchDataQueryDefaults.COLUMN_VALUE_INDEX_NAME,
                ElasticsearchDataQueryDefaults.COLUMN_VALUE_INDEX_MAPPING)) {
            throw new IllegalStateException("Failed to create column-value index");
        }
    }

    @Override
    public void recreateIndex() {
        if (!isAvailable()) {
            throw new IllegalStateException("ES client is unavailable");
        }
        if (indexExists(ElasticsearchDataQueryDefaults.COLUMN_VALUE_INDEX_NAME)
                && !deleteIndex(ElasticsearchDataQueryDefaults.COLUMN_VALUE_INDEX_NAME)) {
            throw new IllegalStateException("Failed to delete column-value index");
        }
        if (!createIndex(ElasticsearchDataQueryDefaults.COLUMN_VALUE_INDEX_NAME,
                ElasticsearchDataQueryDefaults.COLUMN_VALUE_INDEX_MAPPING)) {
            throw new IllegalStateException("Failed to recreate column-value index");
        }
    }

    @Override
    public ColumnValueSearchResult search(ColumnValueSearchRequest request) throws IOException {
        if (client == null || request == null) {
            return ColumnValueSearchResult.empty();
        }
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termsQuery("modelCode", request.getModelCodes()))
                        .must(QueryBuilders.matchQuery("value", request.getQuery())))
                .sort(SortBuilders.scoreSort().order(SortOrder.DESC))
                .size(request.getLimit());
        SearchResponse response = client.search(
                new SearchRequest(ElasticsearchDataQueryDefaults.COLUMN_VALUE_INDEX_NAME).source(sourceBuilder), RequestOptions.DEFAULT);
        SearchHit[] hits = response.getHits().getHits();
        List<ColumnValueHit> result = new ArrayList<>(hits.length);
        for (SearchHit hit : hits) {
            Map<String, Object> fields = new LinkedHashMap<>();
            hit.getSourceAsMap().forEach((key, value) -> {
                if (key != null && value != null) {
                    fields.put(key, value);
                }
            });
            result.add(new ColumnValueHit(fields, hit.getScore()));
        }
        return new ColumnValueSearchResult(result);
    }

    @Override
    public boolean indexExists(String indexName) {
        return client != null && ESUtil.isExistsIndex(client, indexName);
    }

    @Override
    public boolean createIndex(String indexName, String definition) {
        return client != null && ESUtil.createIndex(client, indexName, definition);
    }

    @Override
    public boolean deleteIndex(String indexName) {
        return client != null && ESUtil.deleteIndex(client, indexName);
    }

    @Override
    public boolean bulkInsert(String indexName, List<ColumnValueDocument> documents) {
        if (client == null || documents == null || documents.isEmpty()) {
            return false;
        }
        List<Map<String, Object>> rows = new ArrayList<>(documents.size());
        for (ColumnValueDocument document : documents) {
            Map<String, Object> fields = new LinkedHashMap<>(document.getFields());
            if (StringUtils.isNotBlank(document.getId())) {
                fields.putIfAbsent("valueId", document.getId());
            }
            rows.add(fields);
        }
        try {
            return ESUtil.bulkInsert(client, indexName, rows, "valueId");
        } catch (IOException e) {
            log.warn("ES bulk insert failed, index={}: {}", indexName, e.getMessage(), e);
            return false;
        }
    }
}

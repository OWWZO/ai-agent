package org.wwz.ai.domain.agent.rag.service;

import org.wwz.ai.domain.agent.rag.model.query.ColumnSchemaRecallQuery;
import org.wwz.ai.domain.agent.rag.model.config.DataQuerySettings;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueHit;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueSearchRequest;
import org.wwz.ai.domain.agent.rag.model.column.ColumnValueSearchResult;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingRequest;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingResult;
import org.wwz.ai.domain.agent.rag.model.vector.VectorRecallRequest;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchHit;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchRequest;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchResult;
import org.wwz.ai.domain.agent.rag.port.ColumnValueSearchPort;
import org.wwz.ai.domain.agent.rag.port.TextEmbeddingPort;
import org.wwz.ai.domain.agent.rag.port.VectorSearchPort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain service for schema and column-value recall.
 *
 * <p>Search and embedding are invoked through RAG ports, so transport DTOs and client libraries
 * stay outside Domain.</p>
 */
@Service
public class SchemaRecallService {

    private final TextEmbeddingPort textEmbeddingPort;
    private final VectorSearchPort vectorSearchPort;
    private final ColumnValueSearchPort columnValueSearchPort;

    public SchemaRecallService(TextEmbeddingPort textEmbeddingPort,
                               VectorSearchPort vectorSearchPort,
                               ColumnValueSearchPort columnValueSearchPort) {
        this.textEmbeddingPort = textEmbeddingPort;
        this.vectorSearchPort = vectorSearchPort;
        this.columnValueSearchPort = columnValueSearchPort;
    }

    public List<Map<String, Object>> vectorRecall(ColumnSchemaRecallQuery request) {
        if (request == null) {
            return new ArrayList<>();
        }
        int limit = request.limit() == null ? 100 : request.limit();
        VectorRecallRequest recallRequest = new VectorRecallRequest(
                request.query(),
                DataQuerySettings.SCHEMA_COLLECTION_NAME,
                limit,
                request.scoreThreshold(),
                Map.of("modelCode", request.modelCodes()),
                List.of(),
                request.timeoutMillis());
        return vectorRecall(recallRequest);
    }

    public List<Map<String, Object>> vectorRecall(VectorRecallRequest request) {
        if (request == null || request.getCollectionName() == null || request.getCollectionName().isBlank()
                || request.getQuery() == null || request.getQuery().isBlank()
                || textEmbeddingPort == null || vectorSearchPort == null) {
            return new ArrayList<>();
        }
        try {
            TextEmbeddingResult embedding = textEmbeddingPort.embed(
                    new TextEmbeddingRequest(List.of(request.getQuery()), true));
            if (embedding == null || !embedding.isAvailable() || embedding.getVectors().isEmpty()
                    || embedding.getVectors().get(0).isEmpty()) {
                return new ArrayList<>();
            }
            VectorSearchResult result = vectorSearchPort.search(new VectorSearchRequest(
                    request.getCollectionName(),
                    embedding.getVectors().get(0),
                    request.getLimit(),
                    request.getScoreThreshold(),
                    request.getFilters(),
                    request.getPayloads(),
                    request.getTimeoutMillis()));
            if (result == null || result.getHits().isEmpty()) {
                return new ArrayList<>();
            }
            List<Map<String, Object>> dataList = new ArrayList<>(result.getHits().size());
            for (VectorSearchHit hit : result.getHits()) {
                Map<String, Object> row = new HashMap<>(hit.getPayload());
                row.put("score", hit.getScore());
                row.put("_id", hit.getId());
                dataList.add(row);
            }
            return dataList;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> esValueRecall(ColumnValueSearchRequest request) {
        if (request == null || columnValueSearchPort == null) {
            return new ArrayList<>();
        }
        try {
            ColumnValueSearchResult result = columnValueSearchPort.search(request);
            if (result == null || result.getHits().isEmpty()) {
                return new ArrayList<>();
            }
            List<Map<String, Object>> dataList = new ArrayList<>(result.getHits().size());
            for (ColumnValueHit hit : result.getHits()) {
                Map<String, Object> row = new HashMap<>(hit.getFields());
                if (hit.getScore() != null) {
                    row.put("_score", hit.getScore());
                }
                dataList.add(row);
            }
            return dataList;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

}

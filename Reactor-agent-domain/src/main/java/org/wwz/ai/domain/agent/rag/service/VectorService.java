package org.wwz.ai.domain.agent.rag.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingRequest;
import org.wwz.ai.domain.agent.rag.model.embedding.TextEmbeddingResult;
import org.wwz.ai.domain.agent.rag.model.vector.VectorFilter;
import org.wwz.ai.domain.agent.rag.model.vector.VectorPoint;
import org.wwz.ai.domain.agent.rag.model.vector.VectorRecallRequest;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchHit;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchRequest;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchResult;
import org.wwz.ai.domain.agent.rag.model.vector.VectorWriteRequest;
import org.wwz.ai.domain.agent.rag.port.TextEmbeddingPort;
import org.wwz.ai.domain.agent.rag.port.VectorIndexAdminPort;
import org.wwz.ai.domain.agent.rag.port.VectorSearchPort;
import org.wwz.ai.types.agent.config.AgentExecutorNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Domain-facing vector capability facade.
 *
 * <p>The historical class name remains for the un-migrated metadata service. Request mapping,
 * embedding, and index clients are supplied through Domain ports.</p>
 */
@Service
public class VectorService {

    private TextEmbeddingPort textEmbeddingPort;
    private VectorSearchPort vectorSearchPort;
    private VectorIndexAdminPort vectorIndexAdminPort;
    private Executor toolExecutor;

    public VectorService() {
    }

    @Autowired
    public VectorService(TextEmbeddingPort textEmbeddingPort,
                         VectorSearchPort vectorSearchPort,
                          VectorIndexAdminPort vectorIndexAdminPort,
                          @Qualifier(AgentExecutorNames.TOOL_EXECUTOR) Executor toolExecutor) {
        this.textEmbeddingPort = textEmbeddingPort;
        this.vectorSearchPort = vectorSearchPort;
        this.vectorIndexAdminPort = vectorIndexAdminPort;
        this.toolExecutor = toolExecutor;
    }

    public List<Map<String, Object>> vectorRecall(VectorRecallRequest request) {
        if (request == null || request.getCollectionName() == null || request.getCollectionName().isBlank()) {
            throw new IllegalArgumentException("collectionName is empty");
        }
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("query is empty");
        }

        if (toolExecutor == null) {
            return recall(request);
        }
        CompletableFuture<List<Map<String, Object>>> future =
                CompletableFuture.supplyAsync(() -> recall(request), toolExecutor);
        try {
            if (request.getTimeoutMillis() == null) {
                return future.get();
            }
            return future.get(request.getTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return new ArrayList<>();
        } catch (Exception e) {
            future.cancel(true);
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> recall(VectorRecallRequest request) {
        if (textEmbeddingPort == null || vectorSearchPort == null) {
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

    public Boolean saveVector(VectorWriteRequest request) {
        try {
            if (request == null || request.getCollectionName() == null || request.getCollectionName().isBlank()) {
                throw new IllegalArgumentException("collectionName is null!");
            }
            if (request.getItems().isEmpty() || textEmbeddingPort == null || vectorIndexAdminPort == null) {
                throw new IllegalArgumentException("dataList is null!");
            }

            List<String> textList = request.getItems().stream()
                    .map(VectorWriteRequest.Item::getEmbeddingText)
                    .toList();
            TextEmbeddingResult embedding = textEmbeddingPort.embed(new TextEmbeddingRequest(textList, true));
            if (embedding == null || !embedding.isAvailable()
                    || embedding.getVectors().size() != request.getItems().size()) {
                throw new IllegalStateException("embedding result size mismatch");
            }
            List<VectorPoint> points = new ArrayList<>(request.getItems().size());
            for (int i = 0; i < request.getItems().size(); i++) {
                VectorWriteRequest.Item item = request.getItems().get(i);
                String id = item.getId() != null && isUuid(item.getId())
                        ? item.getId() : UUID.randomUUID().toString();
                points.add(new VectorPoint(id, embedding.getVectors().get(i), item.getPayload()));
            }
            vectorIndexAdminPort.upsert(request.getCollectionName(), points);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean deleteVector(String collectionName, List<String> vectorIdList) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("collectionName is null!");
        }
        if (vectorIdList == null || vectorIdList.isEmpty()) {
            throw new IllegalArgumentException("vectorIdList is null!");
        }
        try {
            if (vectorIndexAdminPort == null) {
                return false;
            }
            vectorIndexAdminPort.deleteByIds(collectionName, vectorIdList);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean deleteVector(String collectionName, Map<String, Object> filter) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("collectionName is null!");
        }
        if (filter == null) {
            throw new IllegalArgumentException("filter is null!");
        }
        try {
            if (vectorIndexAdminPort == null) {
                return false;
            }
            vectorIndexAdminPort.deleteByFilter(collectionName, new VectorFilter(filter));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void setTextEmbeddingPort(TextEmbeddingPort textEmbeddingPort) {
        this.textEmbeddingPort = textEmbeddingPort;
    }

    public void setVectorSearchPort(VectorSearchPort vectorSearchPort) {
        this.vectorSearchPort = vectorSearchPort;
    }

    public void setVectorIndexAdminPort(VectorIndexAdminPort vectorIndexAdminPort) {
        this.vectorIndexAdminPort = vectorIndexAdminPort;
    }

    public void setToolExecutor(Executor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }
}

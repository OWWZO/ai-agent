package org.wwz.ai.infrastructure.dataquery.vector;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.rag.model.vector.VectorFilter;
import org.wwz.ai.domain.agent.rag.model.vector.VectorPoint;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchHit;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchRequest;
import org.wwz.ai.domain.agent.rag.model.vector.VectorSearchResult;
import org.wwz.ai.domain.agent.rag.port.VectorIndexAdminPort;
import org.wwz.ai.domain.agent.rag.port.VectorSearchPort;
import org.wwz.ai.infrastructure.dataquery.vector.QdrantProperties;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.qdrant.client.ConditionFactory.match;
import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.ConditionFactory.matchKeywords;
import static io.qdrant.client.ConditionFactory.matchValues;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.list;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;
import static io.qdrant.client.WithPayloadSelectorFactory.include;

/**
 * Qdrant 向量能力适配器。
 *
 * <p>保留原有 REST/gRPC 分流：带 scheme 的 URL 使用 REST，旧 host/port 配置使用 gRPC；
 * REST 请求仍通过 RemoteHttpPort 发出，避免基础设施内部再创建第二套 HTTP 连接池。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantVectorAdapter implements VectorSearchPort, VectorIndexAdminPort {

    private static final String JSON_MEDIA_TYPE = "application/json; charset=utf-8";
    private static final int MAX_LIMIT_SIZE = 5000;

    private final QdrantProperties properties;
    private final RemoteHttpPort remoteHttpPort;
    private volatile QdrantClient client;

    @PostConstruct
    public void warmUp() {
        if (Boolean.TRUE.equals(properties.getEnable())) {
            try {
                if (!shouldUseRestApi(resolveEndpoint())) {
                    getClient();
                }
            } catch (Exception e) {
                log.warn("Qdrant client lazy init skipped: {}", e.getMessage());
            }
        }
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    @Override
    public VectorSearchResult search(VectorSearchRequest request) throws Exception {
        if (request == null || StringUtils.isBlank(request.getCollectionName())) {
            throw new IllegalArgumentException("collectionName is empty");
        }
        if (request.getVector() == null || request.getVector().isEmpty()) {
            throw new IllegalArgumentException("vector is empty");
        }
        ResolvedEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vector", request.getVector());
            body.put("limit", Math.min(request.getLimit(), MAX_LIMIT_SIZE));
            body.put("with_payload", request.getPayloads().isEmpty()
                    ? true : Collections.singletonMap("include", request.getPayloads()));
            if (request.getFilters() != null && !request.getFilters().isEmpty()) {
                body.put("filter", convertFilterToRest(request.getFilters()));
            }
            if (request.getScoreThreshold() != null) {
                body.put("score_threshold", request.getScoreThreshold());
            }
            try {
                JSONObject result = JSON.parseObject(executeRestRequest(
                        endpoint, "POST", "/collections/" + request.getCollectionName() + "/points/search", body));
                JSONArray points = result.getJSONArray("result");
                if (points == null) {
                    return VectorSearchResult.empty();
                }
                List<VectorSearchHit> hits = new ArrayList<>(points.size());
                for (int i = 0; i < points.size(); i++) {
                    hits.add(toSearchHit(points.getJSONObject(i)));
                }
                return new VectorSearchResult(hits);
            } catch (IOException e) {
                throw new IllegalStateException("Qdrant REST 检索失败", e);
            }
        }

        Points.SearchPoints.Builder builder = Points.SearchPoints.newBuilder()
                .setCollectionName(request.getCollectionName())
                .addAllVector(request.getVector())
                .setLimit(Math.min(request.getLimit(), MAX_LIMIT_SIZE));
        if (!request.getPayloads().isEmpty()) {
            builder.setWithPayload(include(request.getPayloads()));
        } else {
            builder.setWithPayload(enable(true));
        }
        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            builder.setFilter(toQdrantFilter(request.getFilters()));
        }
        if (request.getScoreThreshold() != null) {
            builder.setScoreThreshold(request.getScoreThreshold());
        }
        List<Points.ScoredPoint> points = request.getTimeoutMillis() == null
                ? getClient().searchAsync(builder.build()).get()
                : getClient().searchAsync(builder.build()).get(request.getTimeoutMillis(), TimeUnit.MILLISECONDS);
        List<VectorSearchHit> hits = points.stream().map(this::toSearchHit).collect(Collectors.toList());
        return new VectorSearchResult(hits);
    }

    @Override
    public boolean collectionExists(String collectionName) throws Exception {
        ResolvedEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            return listCollectionsByRest(endpoint).contains(collectionName);
        }
        return getClient().listCollectionsAsync().get().contains(collectionName);
    }

    @Override
    public void createCollection(String collectionName, int dimension) throws Exception {
        ResolvedEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            if (collectionExists(collectionName)) {
                ensurePayloadIndexes(endpoint, collectionName);
                return;
            }
            Map<String, Object> vectorConfig = new LinkedHashMap<>();
            vectorConfig.put("size", dimension);
            vectorConfig.put("distance", "Cosine");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vectors", vectorConfig);
            try {
                executeRestRequest(endpoint, "PUT", "/collections/" + collectionName, body);
                ensurePayloadIndexes(endpoint, collectionName);
            } catch (IOException e) {
                throw new IllegalStateException("Qdrant REST 创建集合失败", e);
            }
            return;
        }
        if (!collectionExists(collectionName)) {
            getClient().createCollectionAsync(collectionName,
                    io.qdrant.client.grpc.Collections.VectorParams.newBuilder()
                            .setDistance(io.qdrant.client.grpc.Collections.Distance.Cosine)
                            .setSize(dimension)
                            .build()).get();
        }
    }

    @Override
    public void recreateCollection(String collectionName, int dimension) throws Exception {
        ResolvedEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            if (collectionExists(collectionName)) {
                executeRestRequest(endpoint, "DELETE", "/collections/" + collectionName, null);
            }
            createCollection(collectionName, dimension);
            return;
        }
        if (collectionExists(collectionName)) {
            getClient().deleteCollectionAsync(collectionName).get();
        }
        createCollection(collectionName, dimension);
    }

    @Override
    public void upsert(String collectionName, List<VectorPoint> points) throws Exception {
        if (StringUtils.isBlank(collectionName)) {
            throw new IllegalArgumentException("集合名为空！");
        }
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("向量集合为空！");
        }
        ResolvedEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            List<Map<String, Object>> pointList = new ArrayList<>(points.size());
            for (VectorPoint point : points) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", point.getId());
                value.put("vector", point.getVector());
                value.put("payload", point.getPayload());
                pointList.add(value);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("points", pointList);
            try {
                executeRestRequest(endpoint, "PUT", "/collections/" + collectionName + "/points?wait=true", body);
                return;
            } catch (IOException e) {
                throw new IllegalStateException("Qdrant REST 写入向量失败", e);
            }
        }
        List<Points.PointStruct> pointStructs = new ArrayList<>(points.size());
        for (VectorPoint point : points) {
            pointStructs.add(Points.PointStruct.newBuilder()
                    .setId(id(UUID.fromString(point.getId())))
                    .setVectors(vectors(point.getVector()))
                    .putAllPayload(toQdrantPayload(point.getPayload()))
                    .build());
        }
        getClient().upsertAsync(collectionName, pointStructs).get();
    }

    @Override
    public void deleteByIds(String collectionName, List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("vectorIdList is null!");
        }
        ResolvedEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("points", ids);
            executeRestRequest(endpoint, "POST", "/collections/" + collectionName + "/points/delete?wait=true", body);
            return;
        }
        List<Points.PointId> pointIds = ids.stream()
                .map(value -> id(UUID.fromString(value)))
                .collect(Collectors.toList());
        getClient().deleteAsync(collectionName, pointIds).get();
    }

    @Override
    public void deleteByFilter(String collectionName, VectorFilter filter) throws Exception {
        Map<String, Object> must = filter == null ? Collections.emptyMap() : filter.getMust();
        ResolvedEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("filter", convertFilterToRest(must));
            executeRestRequest(endpoint, "POST", "/collections/" + collectionName + "/points/delete?wait=true", body);
            return;
        }
        getClient().deleteAsync(collectionName, toQdrantFilter(must)).get();
    }

    private QdrantClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = createClient();
                }
            }
        }
        return client;
    }

    private QdrantClient createClient() {
        ResolvedEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            throw new IllegalStateException("当前 Qdrant 配置已切换为 REST 模式，不创建 gRPC Client");
        }
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                endpoint.host, endpoint.port, endpoint.tlsEnabled);
        if (StringUtils.isNotBlank(endpoint.apiKey)) {
            builder.withApiKey(endpoint.apiKey);
        }
        return new QdrantClient(builder.build());
    }

    private ResolvedEndpoint resolveEndpoint() {
        QdrantProperties config = properties;
        Integer configuredPort = config.getPort();
        if (configuredPort == null || configuredPort <= 0) {
            throw new IllegalStateException("Qdrant port is blank");
        }
        Boolean preferGrpc = config.getPreferGrpc();
        if (preferGrpc == null) {
            // 兼容未迁移的 host/port 配置；带 URL 时仍由 URL 决定 REST 分流。
            preferGrpc = false;
        }
        String url = StringUtils.trimToNull(config.getUrl());
        if (url != null) {
            if (url.contains("://")) {
                URI uri = URI.create(url);
                String host = StringUtils.trimToNull(uri.getHost());
                if (host == null) {
                    throw new IllegalStateException("Qdrant url host is blank");
                }
                return new ResolvedEndpoint(host, uri.getPort() > 0 ? uri.getPort() : configuredPort,
                        "https".equalsIgnoreCase(uri.getScheme()), config.getApiKey(), preferGrpc, url);
            }
            return new ResolvedEndpoint(url, configuredPort, false, config.getApiKey(), preferGrpc, url);
        }
        String host = StringUtils.trimToNull(config.getHost());
        if (host == null) {
            throw new IllegalStateException("Qdrant host is blank");
        }
        return new ResolvedEndpoint(host, configuredPort, false, config.getApiKey(), preferGrpc, null);
    }

    private boolean shouldUseRestApi(ResolvedEndpoint endpoint) {
        return StringUtils.isNotBlank(endpoint.url);
    }

    private List<String> listCollectionsByRest(ResolvedEndpoint endpoint) throws IOException {
        JSONObject body = JSON.parseObject(executeRestRequest(endpoint, "GET", "/collections", null));
        JSONObject result = body.getJSONObject("result");
        if (result == null || result.getJSONArray("collections") == null) {
            return new ArrayList<>();
        }
        JSONArray collections = result.getJSONArray("collections");
        List<String> names = new ArrayList<>(collections.size());
        for (int i = 0; i < collections.size(); i++) {
            names.add(collections.getJSONObject(i).getString("name"));
        }
        return names;
    }

    private String executeRestRequest(ResolvedEndpoint endpoint, String method, String path, Object body)
            throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(endpoint.apiKey)) {
            headers.put("api-key", endpoint.apiKey);
        }
        if (body != null) {
            headers.put("Content-Type", JSON_MEDIA_TYPE);
        }
        return remoteHttpPort.execute(RemoteHttpRequest.builder()
                .method(method)
                .url(buildRestBaseUrl(endpoint) + path)
                .headers(headers)
                .body(body == null ? null : JSON.toJSONString(body))
                .build());
    }

    private String buildRestBaseUrl(ResolvedEndpoint endpoint) {
        if (StringUtils.isNotBlank(endpoint.url)) {
            return StringUtils.removeEnd(endpoint.url, "/");
        }
        return String.format("%s://%s:%d", endpoint.tlsEnabled ? "https" : "http", endpoint.host, endpoint.port);
    }

    private void ensurePayloadIndexes(ResolvedEndpoint endpoint, String collectionName) throws IOException {
        if (!StringUtils.equals(collectionName, VectorDataQueryDefaults.SCHEMA_COLLECTION_NAME)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("field_name", "modelCode");
        body.put("field_schema", "keyword");
        executeRestRequest(endpoint, "PUT", "/collections/" + collectionName + "/index?wait=true", body);
    }

    private Points.Filter toQdrantFilter(Map<String, Object> values) {
        Points.Filter.Builder builder = Points.Filter.newBuilder();
        values.forEach((key, rawValue) -> {
            if (rawValue instanceof String value) {
                builder.addMust(matchKeyword(key, value));
            } else if (rawValue instanceof Long value) {
                builder.addMust(match(key, value));
            } else if (rawValue instanceof Integer value) {
                builder.addMust(match(key, value));
            } else if (rawValue instanceof Boolean value) {
                builder.addMust(match(key, value));
            } else if (rawValue instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof String) {
                    builder.addMust(matchKeywords(key, list.stream().map(String::valueOf).collect(Collectors.toList())));
                } else if (first instanceof Number) {
                    List<Long> numbers = list.stream().map(value -> ((Number) value).longValue()).collect(Collectors.toList());
                    builder.addMust(matchValues(key, numbers));
                }
            }
        });
        return builder.build();
    }

    private Map<String, Object> convertFilterToRest(Map<String, Object> values) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        values.forEach((key, rawValue) -> {
            Map<String, Object> condition = new LinkedHashMap<>();
            condition.put("key", key);
            Map<String, Object> match = new LinkedHashMap<>();
            if (rawValue instanceof List<?> list) {
                match.put("any", list);
            } else {
                match.put("value", rawValue);
            }
            condition.put("match", match);
            conditions.add(condition);
        });
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("must", conditions);
        return filter;
    }

    private Map<String, JsonWithInt.Value> toQdrantPayload(Map<String, Object> payload) {
        Map<String, JsonWithInt.Value> result = new LinkedHashMap<>();
        if (payload != null) {
            payload.forEach((key, value) -> {
                JsonWithInt.Value converted = toQdrantValue(value, 0);
                if (converted != null) {
                    result.put(key, converted);
                }
            });
        }
        return result;
    }

    private JsonWithInt.Value toQdrantValue(Object value, int depth) {
        if (value == null || depth > 100) {
            return null;
        }
        if (value instanceof JsonWithInt.Value qdrantValue) {
            return qdrantValue;
        }
        if (value instanceof List<?> list) {
            List<JsonWithInt.Value> values = new ArrayList<>();
            for (Object item : list) {
                values.add(toQdrantValue(item, depth + 1));
            }
            return list(values);
        }
        if (value instanceof String string) {
            return value(string);
        }
        if (value instanceof Integer integer) {
            return value(integer);
        }
        if (value instanceof Long longValue) {
            return value(longValue);
        }
        if (value instanceof Double doubleValue) {
            return value(doubleValue);
        }
        if (value instanceof Float floatValue) {
            return value(floatValue);
        }
        if (value instanceof Boolean booleanValue) {
            return value(booleanValue);
        }
        return value(JSON.toJSONString(value));
    }

    private VectorSearchHit toSearchHit(Points.ScoredPoint point) {
        Map<String, Object> payload = new LinkedHashMap<>();
        point.getPayloadMap().forEach((key, value) -> payload.put(key, value.getStringValue()));
        return new VectorSearchHit(point.getId().getUuid(), point.getScore(), payload);
    }

    private VectorSearchHit toSearchHit(JSONObject point) {
        Map<String, Object> payload = new LinkedHashMap<>();
        JSONObject payloadJson = point.getJSONObject("payload");
        if (payloadJson != null) {
            payloadJson.forEach((key, value) -> {
                if (value != null) {
                    payload.put(key, String.valueOf(value));
                }
            });
        }
        return new VectorSearchHit(String.valueOf(point.get("id")), point.getFloatValue("score"), payload);
    }

    private static final class ResolvedEndpoint {
        private final String host;
        private final int port;
        private final boolean tlsEnabled;
        private final String apiKey;
        @SuppressWarnings("unused")
        private final boolean preferGrpc;
        private final String url;

        private ResolvedEndpoint(String host, int port, boolean tlsEnabled, String apiKey,
                                 boolean preferGrpc, String url) {
            this.host = host;
            this.port = port;
            this.tlsEnabled = tlsEnabled;
            this.apiKey = apiKey;
            this.preferGrpc = preferGrpc;
            this.url = url;
        }
    }
}

package org.wwz.ai.domain.agent.reactor.service;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import okhttp3.MediaType;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConstants;
import org.wwz.ai.domain.agent.reactor.config.data.QdrantConfig;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.list;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;
import static io.qdrant.client.WithPayloadSelectorFactory.include;


/**
 * qdrant版本为v1.10.0
 */
@Slf4j
@Service
public class QdrantService implements InitializingBean, DisposableBean {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    @Autowired
    private DataAgentConfig dataAgentConfig;
    @Autowired
    private RemoteHttpPort remoteHttpPort;

    private volatile QdrantClient client;
    public static final int maxLimitSize = 5000;

    public void setDataAgentConfig(DataAgentConfig dataAgentConfig) {
        this.dataAgentConfig = dataAgentConfig;
    }

    public synchronized QdrantClient getClient() {
        if (client == null) {
            client = createClient();
        }
        return client;
    }

    public ResolvedQdrantEndpoint resolveEndpoint() {
        return resolveEndpoint(dataAgentConfig.getQdrantConfig());
    }

    public ResolvedQdrantEndpoint resolveEndpoint(QdrantConfig qdrantConfig) {
        String url = StringUtils.trimToNull(qdrantConfig.getUrl());
        Integer configuredPort = qdrantConfig.getPort();
        if (configuredPort == null || configuredPort <= 0) {
            throw new IllegalStateException("Qdrant port is blank");
        }
        int port = configuredPort;
        Boolean preferGrpc = qdrantConfig.getPreferGrpc();
        if (preferGrpc == null) {
            throw new IllegalStateException("Qdrant preferGrpc is blank");
        }
        if (StringUtils.isNotBlank(url)) {
            if (url.contains("://")) {
                URI uri = URI.create(url);
                String host = uri.getHost();
                if (StringUtils.isBlank(host)) {
                    throw new IllegalStateException("Qdrant url host is blank");
                }
                int resolvedPort = uri.getPort() > 0 ? uri.getPort() : port;
                boolean tlsEnabled = "https".equalsIgnoreCase(uri.getScheme());
                return new ResolvedQdrantEndpoint(host, resolvedPort, tlsEnabled, qdrantConfig.getApiKey(), preferGrpc, url);
            }
            return new ResolvedQdrantEndpoint(url, port, false, qdrantConfig.getApiKey(), preferGrpc, url);
        }
        String host = StringUtils.trimToNull(qdrantConfig.getHost());
        if (StringUtils.isBlank(host)) {
            throw new IllegalStateException("Qdrant host is blank");
        }
        return new ResolvedQdrantEndpoint(host, port, false, qdrantConfig.getApiKey(), preferGrpc, null);
    }

    private QdrantClient createClient() {
        ResolvedQdrantEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            throw new IllegalStateException("当前 Qdrant 配置已切换为 REST 模式，不创建 gRPC Client");
        }
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                endpoint.getHost(),
                endpoint.getPort(),
                endpoint.isTlsEnabled()
        );
        if (StringUtils.isNotBlank(endpoint.getApiKey())) {
            builder.withApiKey(endpoint.getApiKey());
        }
        return new QdrantClient(builder.build());
    }

    public boolean isCollectionExist(String collectionName) throws ExecutionException, InterruptedException {
        ResolvedQdrantEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            try {
                return listCollectionsByRest(endpoint).contains(collectionName);
            } catch (IOException e) {
                throw new RuntimeException("Qdrant REST 查询集合失败", e);
            }
        }
        return getClient().listCollectionsAsync().get().contains(collectionName);
    }

    public void createCosineCollection(String collectionName, int dimension) throws ExecutionException, InterruptedException {
        ResolvedQdrantEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            try {
                if (isCollectionExist(collectionName)) {
                    ensurePayloadIndexes(endpoint, collectionName);
                    log.info("集合已存在，无需创建");
                    return;
                }
                Map<String, Object> body = new LinkedHashMap<>();
                Map<String, Object> vectors = new LinkedHashMap<>();
                vectors.put("size", dimension);
                vectors.put("distance", "Cosine");
                body.put("vectors", vectors);
                executeRestRequest(endpoint, "PUT", "/collections/" + collectionName, body);
                ensurePayloadIndexes(endpoint, collectionName);
                return;
            } catch (IOException e) {
                throw new RuntimeException("Qdrant REST 创建集合失败", e);
            }
        }
        if (isCollectionExist(collectionName)) {
            log.info("集合已存在，无需创建");
            return;
        }
        getClient().createCollectionAsync(
                collectionName,
                Collections.VectorParams.newBuilder().setDistance(Collections.Distance.Cosine).setSize(dimension).build()
        ).get();
    }

    public void recreateCosineCollection(String collectionName, int dimension) throws ExecutionException, InterruptedException {
        ResolvedQdrantEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            try {
                if (listCollectionsByRest(endpoint).contains(collectionName)) {
                    executeRestRequest(endpoint, "DELETE", "/collections/" + collectionName, null);
                }
                createCosineCollection(collectionName, dimension);
                return;
            } catch (IOException e) {
                throw new RuntimeException("Qdrant REST 重建集合失败", e);
            }
        }
        if (isCollectionExist(collectionName)) {
            getClient().deleteCollectionAsync(collectionName).get();
        }
        createCosineCollection(collectionName, dimension);
    }

    @Override
    public void afterPropertiesSet() {
        if (Boolean.TRUE.equals(dataAgentConfig.getQdrantConfig().getEnable())) {
            try {
                if (!shouldUseRestApi(resolveEndpoint())) {
                    getClient();
                }
            } catch (Exception e) {
                log.warn("Qdrant client lazy init skipped: {}", e.getMessage());
            }
        }
    }

    @Override
    public void destroy() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    public List<Points.ScoredPoint> search(String collectionName, List<Float> vector, int limit, Points.Filter filter, List<String> payloads, Long timeout, TimeUnit timeUnit, Float scoreThreshold) throws ExecutionException, InterruptedException, TimeoutException {
        if (StringUtils.isBlank(collectionName)) {
            throw new IllegalArgumentException("collectionName is empty");
        }
        if (CollectionUtils.isEmpty(vector)) {
            throw new IllegalArgumentException("vector is empty");
        }
        ResolvedQdrantEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vector", vector);
            body.put("limit", Math.min(limit, maxLimitSize));
            body.put("with_payload", CollectionUtils.isNotEmpty(payloads) ? java.util.Collections.singletonMap("include", payloads) : true);
            if (Objects.nonNull(filter)) {
                body.put("filter", convertFilterToRest(filter));
            }
            if (Objects.nonNull(scoreThreshold)) {
                body.put("score_threshold", scoreThreshold);
            }
            try {
                JSONObject result = JSON.parseObject(executeRestRequest(endpoint, "POST", "/collections/" + collectionName + "/points/search", body));
                JSONArray points = result.getJSONArray("result");
                if (points == null) {
                    return new ArrayList<>();
                }
                List<Points.ScoredPoint> scoredPoints = new ArrayList<>(points.size());
                for (int i = 0; i < points.size(); i++) {
                    scoredPoints.add(toScoredPoint(points.getJSONObject(i)));
                }
                return scoredPoints;
            } catch (IOException e) {
                throw new RuntimeException("Qdrant REST 检索失败", e);
            }
        }
        Points.SearchPoints.Builder requestBuilder = Points.SearchPoints.newBuilder();
        requestBuilder.setCollectionName(collectionName);
        requestBuilder.addAllVector(vector);
        requestBuilder.setLimit(Math.min(limit, maxLimitSize));
        if (Objects.nonNull(payloads)) {
            requestBuilder.setWithPayload(include(payloads));
        } else {
            requestBuilder.setWithPayload(enable(true));
        }
        if (Objects.nonNull(filter)) {
            requestBuilder.setFilter(filter);
        }

        if (Objects.nonNull(scoreThreshold)) {
            requestBuilder.setScoreThreshold(scoreThreshold);
        }

        if (Objects.nonNull(timeout) && Objects.nonNull(timeUnit)) {
            return getClient().searchAsync(requestBuilder.build()).get(timeout, timeUnit);
        } else {
            return getClient().searchAsync(requestBuilder.build()).get();
        }
    }


    public Points.ScrollResponse scroll(String collectionName, Points.PointId offset, int size, Points.Filter filter) throws ExecutionException, InterruptedException {
        return getClient().scrollAsync(
                Points.ScrollPoints.newBuilder()
                        .setCollectionName(collectionName)
                        .setFilter(filter)
                        .setOffset(offset)
                        .setLimit(size)
                        .setWithPayload(enable(true))
                        .build()).get();
    }

    public void deletePointsSync(String collectionName, List<Points.PointId> ids) throws ExecutionException, InterruptedException {
        ResolvedQdrantEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("points", ids.stream().map(this::convertPointIdToRest).collect(Collectors.toList()));
            try {
                executeRestRequest(endpoint, "POST", "/collections/" + collectionName + "/points/delete?wait=true", body);
                return;
            } catch (IOException e) {
                throw new RuntimeException("Qdrant REST 删除点失败", e);
            }
        }
        getClient().deleteAsync(collectionName, ids).get();
    }

    public void deleteByFilterSync(String collectionName, Points.Filter filter) throws ExecutionException, InterruptedException {
        ResolvedQdrantEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("filter", convertFilterToRest(filter));
            try {
                executeRestRequest(endpoint, "POST", "/collections/" + collectionName + "/points/delete?wait=true", body);
                return;
            } catch (IOException e) {
                throw new RuntimeException("Qdrant REST 按过滤条件删除失败", e);
            }
        }
        getClient().deleteAsync(collectionName, filter).get();
    }

    public Points.UpdateResult upsertVectors(String collectionName, List<List<Float>> vectors, List<Map<String, JsonWithInt.Value>> payloads) throws ExecutionException, InterruptedException {
        if (StringUtils.isBlank(collectionName)) {
            throw new RuntimeException("集合名为空！");
        }

        if (CollectionUtils.isEmpty(vectors)) {
            throw new RuntimeException("向量集合为空！");
        }

        if (CollectionUtils.isEmpty(payloads)) {
            throw new RuntimeException("元数据集合为空！");
        }

        if (vectors.size() != payloads.size()) {
            throw new RuntimeException("向量集合大小与元数据集合大小不一致，vectorSize：" + vectors.size() + "，payloadSize：" + payloads.size());
        }

        List<Points.PointStruct> pointStructList = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            Points.PointStruct pointStruct = Points.PointStruct.newBuilder()
                    .setId(id(UUID.randomUUID()))
                    .setVectors(vectors(vectors.get(i)))
                    .putAllPayload(payloads.get(i))
                    .build();
            pointStructList.add(pointStruct);
        }

        return getClient().upsertAsync(collectionName, pointStructList).get();
    }

    public Points.UpdateResult upsertVectors(String collectionName, List<String> idList, List<List<Float>> vectors, List<Map<String, JsonWithInt.Value>> payloads) throws ExecutionException, InterruptedException {
        if (StringUtils.isBlank(collectionName)) {
            throw new RuntimeException("集合名为空！");
        }

        if (CollectionUtils.isEmpty(idList)) {
            throw new RuntimeException("向量id集合为空！");
        }

        if (CollectionUtils.isEmpty(vectors)) {
            throw new RuntimeException("向量集合为空！");
        }

        if (CollectionUtils.isEmpty(payloads)) {
            throw new RuntimeException("元数据集合为空！");
        }

        if (vectors.size() != payloads.size()) {
            throw new RuntimeException("向量集合大小与元数据集合大小不一致，vectorSize：" + vectors.size() + "，payloadSize：" + payloads.size());
        }

        List<Points.PointStruct> pointStructList = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            Points.PointStruct pointStruct = Points.PointStruct.newBuilder()
                    .setId(id(UUID.fromString(idList.get(i))))
                    .setVectors(vectors(vectors.get(i)))
                    .putAllPayload(payloads.get(i))
                    .build();
            pointStructList.add(pointStruct);
        }

        return getClient().upsertAsync(collectionName, pointStructList).get();
    }

    public Points.UpdateResult upsertVectorsPayloadTrans(String collectionName, List<String> idList, List<List<Float>> vectors, List<Map<String, Object>> payloads) throws ExecutionException, InterruptedException {
        if (StringUtils.isBlank(collectionName)) {
            throw new RuntimeException("集合名为空！");
        }

        if (CollectionUtils.isEmpty(idList)) {
            throw new RuntimeException("向量id集合为空！");
        }

        if (CollectionUtils.isEmpty(vectors)) {
            throw new RuntimeException("向量集合为空！");
        }

        if (CollectionUtils.isEmpty(payloads)) {
            throw new RuntimeException("元数据集合为空！");
        }

        if (vectors.size() != payloads.size()) {
            throw new RuntimeException("向量集合大小与元数据集合大小不一致，vectorSize：" + vectors.size() + "，payloadSize：" + payloads.size());
        }
        ResolvedQdrantEndpoint endpoint = resolveEndpoint();
        if (shouldUseRestApi(endpoint)) {
            List<Map<String, Object>> pointList = new ArrayList<>();
            for (int i = 0; i < vectors.size(); i++) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("id", idList.get(i));
                point.put("vector", vectors.get(i));
                point.put("payload", payloads.get(i));
                pointList.add(point);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("points", pointList);
            try {
                executeRestRequest(endpoint, "PUT", "/collections/" + collectionName + "/points?wait=true", body);
                return Points.UpdateResult.newBuilder().setStatus(Points.UpdateStatus.Completed).build();
            } catch (IOException e) {
                throw new RuntimeException("Qdrant REST 写入向量失败", e);
            }
        }

        List<Map<String, JsonWithInt.Value>> maps = transPayloadMap(payloads);

        List<Points.PointStruct> pointStructList = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            Points.PointStruct pointStruct = Points.PointStruct.newBuilder()
                    .setId(id(UUID.fromString(idList.get(i))))
                    .setVectors(vectors(vectors.get(i)))
                    .putAllPayload(maps.get(i))
                    .build();
            pointStructList.add(pointStruct);
        }

        return getClient().upsertAsync(collectionName, pointStructList).get();
    }

    private boolean shouldUseRestApi(ResolvedQdrantEndpoint endpoint) {
        return StringUtils.isNotBlank(endpoint.getUrl());
    }

    private List<String> listCollectionsByRest(ResolvedQdrantEndpoint endpoint) throws IOException {
        JSONObject body = JSON.parseObject(executeRestRequest(endpoint, "GET", "/collections", null));
        JSONObject result = body.getJSONObject("result");
        if (result == null) {
            return new ArrayList<>();
        }
        JSONArray collections = result.getJSONArray("collections");
        if (collections == null) {
            return new ArrayList<>();
        }
        List<String> collectionNames = new ArrayList<>(collections.size());
        for (int i = 0; i < collections.size(); i++) {
            collectionNames.add(collections.getJSONObject(i).getString("name"));
        }
        return collectionNames;
    }

    private String executeRestRequest(ResolvedQdrantEndpoint endpoint, String method, String path, Object body) throws IOException {
        String requestUrl = buildRestBaseUrl(endpoint) + path;
        Map<String, String> headers = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(endpoint.getApiKey())) {
            headers.put("api-key", endpoint.getApiKey());
        }
        if (body != null) {
            headers.put("Content-Type", JSON_MEDIA_TYPE.toString());
        }
        return remoteHttpPort.execute(RemoteHttpRequest.builder()
                .method(method)
                .url(requestUrl)
                .headers(headers)
                .body(body == null ? null : JSON.toJSONString(body))
                .build());
    }

    private String buildRestBaseUrl(ResolvedQdrantEndpoint endpoint) {
        if (StringUtils.isNotBlank(endpoint.getUrl())) {
            return StringUtils.removeEnd(endpoint.getUrl(), "/");
        }
        String scheme = endpoint.isTlsEnabled() ? "https" : "http";
        return String.format("%s://%s:%d", scheme, endpoint.getHost(), endpoint.getPort());
    }

    private void ensurePayloadIndexes(ResolvedQdrantEndpoint endpoint, String collectionName) throws IOException {
        if (!StringUtils.equals(collectionName, DataAgentConstants.SCHEMA_COLLECTION_NAME)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("field_name", "modelCode");
        body.put("field_schema", "keyword");
        executeRestRequest(endpoint, "PUT", "/collections/" + collectionName + "/index?wait=true", body);
    }

    private Map<String, Object> convertFilterToRest(Points.Filter filter) {
        Map<String, Object> filterMap = new LinkedHashMap<>();
        if (filter.getMustCount() > 0) {
            filterMap.put("must", filter.getMustList().stream().map(this::convertConditionToRest).collect(Collectors.toList()));
        }
        if (filter.getShouldCount() > 0) {
            filterMap.put("should", filter.getShouldList().stream().map(this::convertConditionToRest).collect(Collectors.toList()));
        }
        if (filter.getMustNotCount() > 0) {
            filterMap.put("must_not", filter.getMustNotList().stream().map(this::convertConditionToRest).collect(Collectors.toList()));
        }
        if (filter.hasMinShould()) {
            Map<String, Object> minShould = new LinkedHashMap<>();
            minShould.put("conditions", filter.getMinShould().getConditionsList().stream().map(this::convertConditionToRest).collect(Collectors.toList()));
            minShould.put("min_count", filter.getMinShould().getMinCount());
            filterMap.put("min_should", minShould);
        }
        return filterMap;
    }

    private Map<String, Object> convertConditionToRest(Points.Condition condition) {
        if (condition.hasField()) {
            return convertFieldConditionToRest(condition.getField());
        }
        if (condition.hasFilter()) {
            Map<String, Object> nested = new LinkedHashMap<>();
            nested.put("filter", convertFilterToRest(condition.getFilter()));
            return nested;
        }
        throw new IllegalArgumentException("暂不支持的 Qdrant 过滤条件类型: " + condition.getConditionOneOfCase());
    }

    private Map<String, Object> convertFieldConditionToRest(Points.FieldCondition fieldCondition) {
        Map<String, Object> conditionMap = new LinkedHashMap<>();
        conditionMap.put("key", fieldCondition.getKey());
        if (fieldCondition.hasMatch()) {
            conditionMap.put("match", convertMatchToRest(fieldCondition.getMatch()));
            return conditionMap;
        }
        if (fieldCondition.hasRange()) {
            Map<String, Object> range = new LinkedHashMap<>();
            if (fieldCondition.getRange().hasLt()) {
                range.put("lt", fieldCondition.getRange().getLt());
            }
            if (fieldCondition.getRange().hasLte()) {
                range.put("lte", fieldCondition.getRange().getLte());
            }
            if (fieldCondition.getRange().hasGt()) {
                range.put("gt", fieldCondition.getRange().getGt());
            }
            if (fieldCondition.getRange().hasGte()) {
                range.put("gte", fieldCondition.getRange().getGte());
            }
            conditionMap.put("range", range);
            return conditionMap;
        }
        throw new IllegalArgumentException("暂不支持的字段过滤类型");
    }

    private Map<String, Object> convertMatchToRest(Points.Match match) {
        Map<String, Object> matchMap = new LinkedHashMap<>();
        if (match.hasKeyword()) {
            matchMap.put("value", match.getKeyword());
            return matchMap;
        }
        if (match.hasInteger()) {
            matchMap.put("value", match.getInteger());
            return matchMap;
        }
        if (match.hasBoolean()) {
            matchMap.put("value", match.getBoolean());
            return matchMap;
        }
        if (match.hasText()) {
            matchMap.put("text", match.getText());
            return matchMap;
        }
        if (match.hasKeywords()) {
            matchMap.put("any", match.getKeywords().getStringsList());
            return matchMap;
        }
        if (match.hasIntegers()) {
            matchMap.put("any", match.getIntegers().getIntegersList());
            return matchMap;
        }
        if (match.hasExceptKeywords()) {
            matchMap.put("except", match.getExceptKeywords().getStringsList());
            return matchMap;
        }
        if (match.hasExceptIntegers()) {
            matchMap.put("except", match.getExceptIntegers().getIntegersList());
            return matchMap;
        }
        throw new IllegalArgumentException("暂不支持的 match 条件类型: " + match.getMatchValueCase());
    }

    private Object convertPointIdToRest(Points.PointId pointId) {
        switch (pointId.getPointIdOptionsCase()) {
            case UUID:
                return pointId.getUuid();
            case NUM:
                return pointId.getNum();
            default:
                throw new IllegalArgumentException("pointId 为空");
        }
    }

    private Points.ScoredPoint toScoredPoint(JSONObject point) {
        Points.ScoredPoint.Builder builder = Points.ScoredPoint.newBuilder();
        builder.setId(buildPointId(point.get("id")));
        builder.setScore(point.getFloatValue("score"));
        JSONObject payload = point.getJSONObject("payload");
        if (payload != null) {
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                JsonWithInt.Value value = getValue(entry.getValue(), 0);
                if (value != null) {
                    builder.putPayload(entry.getKey(), value);
                }
            }
        }
        return builder.build();
    }

    private Points.PointId buildPointId(Object rawId) {
        if (rawId instanceof Number) {
            return Points.PointId.newBuilder().setNum(((Number) rawId).longValue()).build();
        }
        return Points.PointId.newBuilder().setUuid(String.valueOf(rawId)).build();
    }

    public Points.UpdateResult upsertVector(String collectionName, List<Float> vector, Map<String, JsonWithInt.Value> payload) throws ExecutionException, InterruptedException {
        if (StringUtils.isBlank(collectionName)) {
            throw new RuntimeException("集合名为空！");
        }

        if (Objects.isNull(vector)) {
            throw new RuntimeException("向量为空！");
        }

        if (Objects.isNull(payload)) {
            throw new RuntimeException("元数据为空！");
        }


        List<Points.PointStruct> pointStructList = new ArrayList<>();

        Points.PointStruct pointStruct = Points.PointStruct.newBuilder()
                .setId(id(UUID.randomUUID()))
                .setVectors(vectors(vector))
                .putAllPayload(payload)
                .build();
        pointStructList.add(pointStruct);

        return getClient().upsertAsync(collectionName, pointStructList).get();
    }

    private List<Map<String, JsonWithInt.Value>> transPayloadMap(List<Map<String, Object>> payloads) {
        List<Map<String, JsonWithInt.Value>> mapList = new ArrayList<>();

        for (Map<String, Object> payload : payloads) {
            Map<String, JsonWithInt.Value> map = new HashMap<>();
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                JsonWithInt.Value value = getValue(entry.getValue(), 0);
                if (value != null) {
                    map.put(entry.getKey(), value);
                }
            }
            mapList.add(map);
        }

        return mapList;
    }

    private JsonWithInt.Value getValue(Object obj, int cur) {
        if (cur > 100) {
            log.warn("getValue exceed max deep: cur:{}", cur);
            return null;
        }
        if (obj == null) {
            return null;
        }
        if (obj instanceof JsonWithInt.Value) {
            return (JsonWithInt.Value) obj;
        }
        if (obj instanceof List) {
            List<JsonWithInt.Value> result = new ArrayList<>();
            for (Object o : (List<?>) obj) {
                result.add(getValue(o, cur + 1));
            }
            return list(result);
        }
        if (obj instanceof String) {
            return value(obj.toString());
        } else if (obj instanceof Integer) {
            return value((int) obj);
        } else if (obj instanceof Double) {
            return value((double) obj);
        } else if (obj instanceof Float) {
            return value((float) obj);
        } else if (obj instanceof Long) {
            return value((long) obj);
        } else if (obj instanceof Boolean) {
            return value((boolean) obj);
        } else {
            return value(JSON.toJSONString(obj));
        }
    }

    @Data
    public static class ResolvedQdrantEndpoint {
        private final String host;
        private final int port;
        private final boolean tlsEnabled;
        private final String apiKey;
        private final boolean preferGrpc;
        private final String url;
    }
}

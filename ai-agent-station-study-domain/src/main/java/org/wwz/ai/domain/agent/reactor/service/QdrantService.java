package org.wwz.ai.domain.agent.reactor.service;


import com.alibaba.fastjson.JSON;
import org.wwz.ai.domain.agent.reactor.config.data.DataAgentConfig;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    @Autowired
    private DataAgentConfig dataAgentConfig;

    private static QdrantClient client;
    public static final int maxLimitSize = 5000;

    public void setDataAgentConfig(DataAgentConfig dataAgentConfig) {
        this.dataAgentConfig = dataAgentConfig;
    }

    public QdrantClient getClient() {
        return client;
    }

    public boolean isCollectionExist(String collectionName) throws ExecutionException, InterruptedException {
        return client.listCollectionsAsync().get().contains(collectionName);
    }

    public void createCosineCollection(String collectionName, int dimension) throws ExecutionException, InterruptedException {
        if (isCollectionExist(collectionName)) {
            log.info("集合已存在，无需创建");
            return;
        }
        client.createCollectionAsync(collectionName,
                Collections.VectorParams.newBuilder().setDistance(Collections.Distance.Cosine).setSize(dimension).build()).get();
    }

    @Override
    public void afterPropertiesSet() {
        if (client == null) {
            if (StringUtils.isNotBlank(dataAgentConfig.getQdrantConfig().getApiKey())) {
                client = new QdrantClient(QdrantGrpcClient.newBuilder(dataAgentConfig.getQdrantConfig().getHost(), dataAgentConfig.getQdrantConfig().getPort(), false).withApiKey(dataAgentConfig.getQdrantConfig().getApiKey()).build());
            } else {
                client = new QdrantClient(QdrantGrpcClient.newBuilder(dataAgentConfig.getQdrantConfig().getHost(), dataAgentConfig.getQdrantConfig().getPort(), false).build());
            }
        }
    }

    @Override
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }

    public List<Points.ScoredPoint> search(String collectionName, List<Float> vector, int limit, Points.Filter filter, List<String> payloads, Long timeout, TimeUnit timeUnit, Float scoreThreshold) throws ExecutionException, InterruptedException, TimeoutException {
        if (StringUtils.isBlank(collectionName)) {
            throw new IllegalArgumentException("collectionName is empty");
        }
        if (CollectionUtils.isEmpty(vector)) {
            throw new IllegalArgumentException("vector is empty");
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
            return client.searchAsync(requestBuilder.build()).get(timeout, timeUnit);
        } else {
            return client.searchAsync(requestBuilder.build()).get();
        }
    }


    public Points.ScrollResponse scroll(String collectionName, Points.PointId offset, int size, Points.Filter filter) throws ExecutionException, InterruptedException {
        return client.scrollAsync(
                Points.ScrollPoints.newBuilder()
                        .setCollectionName(collectionName)
                        .setFilter(filter)
                        .setOffset(offset)
                        .setLimit(size)
                        .setWithPayload(enable(true))
                        .build()).get();
    }

    public void deletePointsSync(String collectionName, List<Points.PointId> ids) throws ExecutionException, InterruptedException {
        client.deleteAsync(collectionName, ids).get();
    }

    public void deleteByFilterSync(String collectionName, Points.Filter filter) throws ExecutionException, InterruptedException {
        client.deleteAsync(collectionName, filter).get();
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

        return client.upsertAsync(collectionName, pointStructList).get();
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

        return client.upsertAsync(collectionName, pointStructList).get();
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

        return client.upsertAsync(collectionName, pointStructList).get();
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

        return client.upsertAsync(collectionName, pointStructList).get();
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
            for (Object o : (List) obj) {
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
}

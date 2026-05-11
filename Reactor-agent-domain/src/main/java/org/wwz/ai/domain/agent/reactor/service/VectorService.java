package org.wwz.ai.domain.agent.reactor.service;


import com.alibaba.fastjson.JSONObject;
import org.wwz.ai.domain.agent.reactor.data.dto.VectorModelSchema;
import org.wwz.ai.domain.agent.reactor.data.dto.VectorRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.VectorSaveReq;
import org.wwz.ai.domain.agent.ledger.entity.ChatModelSchema;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.runtime.executor.AgentExecutorSupport;
import org.wwz.ai.types.agent.config.AgentExecutorNames;

import jakarta.annotation.Resource;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.qdrant.client.ConditionFactory.*;


@Service
@Slf4j
public class VectorService {

    private EmbeddingService embeddingService;
    private QdrantService qdrantService;

    @Resource(name = AgentExecutorNames.TOOL_EXECUTOR)
    private Executor toolExecutor;

    @Autowired
    public void setEmbeddingService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Autowired
    public void setQdrantService(QdrantService qdrantService) {
        this.qdrantService = qdrantService;
    }


    public List<Map<String, Object>> vectorRecall(VectorRecallReq req) {
        if (StringUtils.isBlank(req.getCollectionName())) {
            throw new RuntimeException("集合名称为空！");
        }
        if (StringUtils.isBlank(req.getQuery())) {
            throw new RuntimeException("查询query为空！");
        }

        CompletableFuture<List<Map<String, Object>>> future = null;
        try {
            future = AgentExecutorSupport.supplyAsync(toolExecutor, "vectorRecall", () -> recall(req));
            future.exceptionally(throwable -> null);
            List<Map<String, Object>> maps = future.get(req.getTimeout(), TimeUnit.MILLISECONDS);
            if (maps == null || maps.isEmpty()) {
                log.error("vectorRecall empty: req:{}", JSONObject.toJSONString(req));
                return new ArrayList<>();
            }
            return maps;
        } catch (Exception e) {
            log.error("vectorRecall error: req:{}", JSONObject.toJSONString(req), e);
            if (future != null) {
                try {
                    future.cancel(true);
                } catch (Exception e1) {
                    log.error(e1.getMessage(), e1);
                }
            }
        }

        return new ArrayList<>();
    }

    private List<Map<String, Object>> recall(VectorRecallReq req) {
        try {
            List<Float> vector = embeddingService.getVector(req.getQuery());
            if (CollectionUtils.isEmpty(vector)) {
                log.error("vectorRecall error: vector is empty, req:{}", JSONObject.toJSONString(req));
                throw new RuntimeException("向量生成失败！");
            }

            Points.Filter filter = null;
            if (Objects.nonNull(req.getKeywordFilterMap()) && !req.getKeywordFilterMap().isEmpty()) {
                Points.Filter.Builder filterBuilder = Points.Filter.newBuilder();
                req.getKeywordFilterMap().forEach((k, v) -> {
                    if (v instanceof String) {
                        filterBuilder.addMust(matchKeyword(k, (String) v));
                    } else if (v instanceof Long) {
                        filterBuilder.addMust(match(k, (long) v));
                    } else if (v instanceof Integer) {
                        filterBuilder.addMust(match(k, (int) v));
                    } else if (v instanceof Boolean) {
                        filterBuilder.addMust(match(k, (boolean) v));
                    } else if (v instanceof List) {
                        List<Object> list = (List<Object>) v;
                        if (CollectionUtils.isNotEmpty(list)) {
                            Object type = list.get(0);
                            if (type instanceof String) {
                                filterBuilder.addMust(matchKeywords(k, (List<String>) v));
                            } else if (type instanceof Long || type instanceof Integer) {
                                filterBuilder.addMust(matchValues(k, (List<Long>) v));
                            }
                        }
                    }
                });
                filter = filterBuilder.build();
            }

            List<Points.ScoredPoint> scoredPoints = qdrantService.search(req.getCollectionName(), vector, req.getLimit(), filter, req.getPayloads(), req.getTimeout(), TimeUnit.MILLISECONDS, req.getScoreThreshold());
            return scoredPoints.stream().map(p -> {
                Map<String, Object> hashMap = new HashMap<>();
                Map<String, JsonWithInt.Value> payloadMap = p.getPayloadMap();
                payloadMap.forEach((k, v) -> hashMap.put(k, v.getStringValue()));
                hashMap.put("score", p.getScore());
                hashMap.put("_id", p.getId().getUuid());
                return hashMap;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("vectorRecall error: req:{}", JSONObject.toJSONString(req), e);
            throw new RuntimeException(e);
        }
    }

    public Boolean saveVector(VectorSaveReq vectorSaveReq) {
        try {
            if (StringUtils.isBlank(vectorSaveReq.getCollectionName())) {
                throw new RuntimeException("collectionName is null!");
            }
            if (CollectionUtils.isEmpty(vectorSaveReq.getDataList())) {
                throw new RuntimeException("dataList is null!");
            }

            List<String> textList = vectorSaveReq.getDataList().stream().map(VectorSaveReq.VectorData::getEmbeddingText).collect(Collectors.toList());
            List<List<Float>> vector = embeddingService.getVectorBatch(textList);
            List<String> idList = vectorSaveReq.getDataList().stream().map(data -> {
                if (StringUtils.isNotBlank(data.getUuid()) && isUuid(data.getUuid())) {
                    return data.getUuid();
                }
                return UUID.randomUUID().toString();
            }).collect(Collectors.toList());
            List<Map<String, Object>> payloads = vectorSaveReq.getDataList().stream().map(VectorSaveReq.VectorData::getPayloads).collect(Collectors.toList());
            qdrantService.upsertVectorsPayloadTrans(vectorSaveReq.getCollectionName(), idList, vector, payloads);
            return true;
        } catch (Exception e) {
            log.error("saveVector error: req:{}", JSONObject.toJSONString(vectorSaveReq), e);
            return false;
        }
    }

    public Boolean deleteVector(String collectionName, List<String> vectorIdList) {
        if (StringUtils.isBlank(collectionName)) {
            throw new RuntimeException("collectionName is null!");
        }
        if (CollectionUtils.isEmpty(vectorIdList)) {
            throw new RuntimeException("vectorIdList is null!");
        }

        try {
            List<Points.PointId> pointIds = vectorIdList.stream().map(vId -> PointIdFactory.id(UUID.fromString(vId))).collect(Collectors.toList());
            qdrantService.deletePointsSync(collectionName, pointIds);
            return true;
        } catch (Exception e) {
            log.error("vector delete failed, collectionName:{}", collectionName, e);
            return false;
        }
    }

    public Boolean deleteVector(String collectionName, Points.Filter filter) {
        if (StringUtils.isBlank(collectionName)) {
            throw new RuntimeException("collectionName is null!");
        }
        if (filter == null) {
            throw new RuntimeException("filter is null!");
        }
        try {
            qdrantService.deleteByFilterSync(collectionName, filter);
            return true;
        } catch (Exception e) {
            log.error("vector delete failed, collectionName:{}", collectionName, e);
            return false;
        }
    }

    private boolean isUuid(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

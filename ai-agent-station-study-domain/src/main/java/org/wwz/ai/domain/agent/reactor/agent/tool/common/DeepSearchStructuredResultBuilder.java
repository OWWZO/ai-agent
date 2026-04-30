package org.wwz.ai.domain.agent.reactor.agent.tool.common;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.agent.dto.DeepSearchrResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * deep_search 结构化结果构建器。
 * 负责把流式三阶段事件归并成完整 JSON 字符串。
 */
public class DeepSearchStructuredResultBuilder {

    private final LinkedHashSet<String> decomposedQueries = new LinkedHashSet<>();
    private final LinkedHashMap<String, List<DeepSearchrResponse.SearchDoc>> searchResults = new LinkedHashMap<>();
    private final Map<String, Set<String>> searchResultDedup = new LinkedHashMap<>();
    private String query;
    private String finalAnswer;

    public DeepSearchStructuredResultBuilder(String query) {
        this.query = StringUtils.trimToEmpty(query);
    }

    /**
     * 记录 deep_search 的阶段事件。
     */
    public void recordEvent(DeepSearchrResponse response) {
        if (response == null) {
            return;
        }
        if (StringUtils.isNotBlank(response.getQuery())) {
            this.query = response.getQuery().trim();
        }
        String messageType = StringUtils.defaultString(response.getMessageType());
        if ("extend".equals(messageType)) {
            recordExtend(response.getSearchResult());
            return;
        }
        if ("search".equals(messageType)) {
            recordSearch(response.getSearchResult());
            return;
        }
        if ("report".equals(messageType)) {
            recordReportChunk(response.getAnswer());
        }
    }

    /**
     * 在最终结束事件时回写完整报告，避免只保留增量 chunk。
     */
    public void recordFinalAnswer(String query, String answer) {
        if (StringUtils.isNotBlank(query)) {
            this.query = query.trim();
        }
        if (StringUtils.isNotBlank(answer)) {
            this.finalAnswer = answer;
        }
    }

    /**
     * 产出统一结构化结果。
     */
    public String buildJson(String fallbackAnswer) {
        String normalizedAnswer = StringUtils.defaultIfBlank(finalAnswer, StringUtils.defaultString(fallbackAnswer));
        DeepSearchStructuredOutput output = buildOutput(normalizedAnswer);
        return JSON.toJSONString(output);
    }

    private void recordExtend(DeepSearchrResponse.SearchResult searchResult) {
        if (searchResult == null || CollectionUtils.isEmpty(searchResult.getQuery())) {
            return;
        }
        for (String item : searchResult.getQuery()) {
            String normalizedQuery = StringUtils.trimToNull(item);
            if (normalizedQuery != null) {
                decomposedQueries.add(normalizedQuery);
            }
        }
    }

    private void recordSearch(DeepSearchrResponse.SearchResult searchResult) {
        if (searchResult == null || CollectionUtils.isEmpty(searchResult.getQuery())) {
            return;
        }
        List<String> queries = searchResult.getQuery();
        List<List<DeepSearchrResponse.SearchDoc>> docsList = searchResult.getDocs();
        for (int idx = 0; idx < queries.size(); idx++) {
            String normalizedQuery = StringUtils.trimToNull(queries.get(idx));
            if (normalizedQuery == null) {
                continue;
            }
            decomposedQueries.add(normalizedQuery);
            List<DeepSearchrResponse.SearchDoc> docsBucket = searchResults.computeIfAbsent(normalizedQuery, key -> new ArrayList<>());
            Set<String> dedupBucket = searchResultDedup.computeIfAbsent(normalizedQuery, key -> new HashSet<>());
            if (docsList == null || idx >= docsList.size() || docsList.get(idx) == null) {
                continue;
            }
            for (DeepSearchrResponse.SearchDoc doc : docsList.get(idx)) {
                if (doc == null) {
                    continue;
                }
                String dedupKey = buildDocDedupKey(doc);
                if (dedupBucket.add(dedupKey)) {
                    docsBucket.add(copyDoc(doc));
                }
            }
        }
    }

    private void recordReportChunk(String answerChunk) {
        if (StringUtils.isBlank(answerChunk)) {
            return;
        }
        if (finalAnswer == null) {
            finalAnswer = answerChunk;
            return;
        }
        finalAnswer = finalAnswer + answerChunk;
    }

    private DeepSearchStructuredOutput buildOutput(String normalizedAnswer) {
        List<DeepSearchStageOutput> stages = new ArrayList<>();
        if (!decomposedQueries.isEmpty()) {
            stages.add(DeepSearchStageOutput.builder()
                    .stage("extend")
                    .queries(new ArrayList<>(decomposedQueries))
                    .build());
        }
        if (!searchResults.isEmpty()) {
            List<DeepSearchQueryResult> results = new ArrayList<>();
            for (Map.Entry<String, List<DeepSearchrResponse.SearchDoc>> entry : searchResults.entrySet()) {
                results.add(DeepSearchQueryResult.builder()
                        .query(entry.getKey())
                        .docs(new ArrayList<>(entry.getValue()))
                        .build());
            }
            stages.add(DeepSearchStageOutput.builder()
                    .stage("search")
                    .results(results)
                    .build());
        }
        if (StringUtils.isNotBlank(normalizedAnswer)) {
            stages.add(DeepSearchStageOutput.builder()
                    .stage("report")
                    .answer(normalizedAnswer)
                    .build());
        }
        return DeepSearchStructuredOutput.builder()
                .tool("deep_search")
                .query(query)
                .stages(stages)
                .build();
    }

    private String buildDocDedupKey(DeepSearchrResponse.SearchDoc doc) {
        return StringUtils.defaultString(doc.getLink())
                + "|"
                + StringUtils.defaultString(doc.getTitle())
                + "|"
                + StringUtils.defaultString(doc.getContent());
    }

    private DeepSearchrResponse.SearchDoc copyDoc(DeepSearchrResponse.SearchDoc doc) {
        return DeepSearchrResponse.SearchDoc.builder()
                .doc_type(doc.getDoc_type())
                .content(doc.getContent())
                .title(doc.getTitle())
                .link(doc.getLink())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeepSearchStructuredOutput {
        private String tool;
        private String query;
        private List<DeepSearchStageOutput> stages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeepSearchStageOutput {
        private String stage;
        private List<String> queries;
        private List<DeepSearchQueryResult> results;
        private String answer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeepSearchQueryResult {
        private String query;
        private List<DeepSearchrResponse.SearchDoc> docs;
    }
}

package org.wwz.ai.domain.agent.reactor.agent.tool.common;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.agent.dto.DeepSearchrResponse;
import org.wwz.ai.domain.agent.reactor.agent.tool.ToolResultPayload;

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

    /**
     * 每个子查询最多保留多少条命中文档到主智能体 observation。
     */
    private static final int OBSERVATION_DOC_LIMIT_PER_QUERY = 3;

    /**
     * 每条文档摘要最大长度，避免再次膨胀成大 JSON。
     */
    private static final int OBSERVATION_DOC_SUMMARY_MAX_LEN = 180;

    /**
     * 最终回答摘要最大长度。
     */
    private static final int OBSERVATION_ANSWER_MAX_LEN = 240;

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

    /**
     * 同时产出结构化 output_json 与主智能体消费的紧凑 observation。
     */
    public ToolResultPayload buildPayload(String fallbackAnswer) {
        String normalizedAnswer = StringUtils.defaultIfBlank(finalAnswer, StringUtils.defaultString(fallbackAnswer));
        DeepSearchStructuredOutput output = buildOutput(normalizedAnswer);
        String outputJson = JSON.toJSONString(output);
        return ToolResultPayload.builder()
                .toolResult(outputJson)
                .llmObservation(buildLlmObservation(normalizedAnswer))
                .outputJson(outputJson)
                .build();
    }

    /**
     * 生成给主智能体使用的紧凑 observation。
     * 只保留查询拆解、来源标题/链接和内容摘要，不再透传完整 stages payload。
     */
    public String buildLlmObservation(String fallbackAnswer) {
        String normalizedAnswer = StringUtils.defaultIfBlank(finalAnswer, StringUtils.defaultString(fallbackAnswer));
        DeepSearchObservationOutput observation = DeepSearchObservationOutput.builder()
                .tool("deep_search")
                .query(query)
                .subQueries(new ArrayList<>(decomposedQueries))
                .results(buildObservationResults())
                .answerSummary(truncate(normalizedAnswer, OBSERVATION_ANSWER_MAX_LEN))
                .build();
        return JSON.toJSONString(observation);
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
                .schemaVersion(1)
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

    private List<DeepSearchObservationQueryResult> buildObservationResults() {
        List<DeepSearchObservationQueryResult> results = new ArrayList<>();
        for (Map.Entry<String, List<DeepSearchrResponse.SearchDoc>> entry : searchResults.entrySet()) {
            List<DeepSearchObservationDoc> docs = new ArrayList<>();
            List<DeepSearchrResponse.SearchDoc> rawDocs = entry.getValue();
            if (rawDocs != null) {
                int docLimit = Math.min(rawDocs.size(), OBSERVATION_DOC_LIMIT_PER_QUERY);
                for (int idx = 0; idx < docLimit; idx++) {
                    DeepSearchrResponse.SearchDoc rawDoc = rawDocs.get(idx);
                    if (rawDoc == null) {
                        continue;
                    }
                    docs.add(DeepSearchObservationDoc.builder()
                            .title(StringUtils.defaultString(rawDoc.getTitle()))
                            .link(StringUtils.defaultString(rawDoc.getLink()))
                            .summary(truncate(StringUtils.defaultString(rawDoc.getContent()), OBSERVATION_DOC_SUMMARY_MAX_LEN))
                            .build());
                }
            }
            results.add(DeepSearchObservationQueryResult.builder()
                    .query(entry.getKey())
                    .docs(docs)
                    .build());
        }
        return results;
    }

    private String truncate(String text, int maxLen) {
        String normalized = StringUtils.trimToEmpty(text);
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen) + "...";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeepSearchStructuredOutput {
        private Integer schemaVersion;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeepSearchObservationOutput {
        private String tool;
        private String query;
        private List<String> subQueries;
        private List<DeepSearchObservationQueryResult> results;
        private String answerSummary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeepSearchObservationQueryResult {
        private String query;
        private List<DeepSearchObservationDoc> docs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeepSearchObservationDoc {
        private String title;
        private String link;
        private String summary;
    }
}

package org.wwz.ai.domain.agent.reactor.service.replay.projector.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.model.ledger.ArtifactView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.replay.ProjectedReplayEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * deep_search projector。
 */
public class DeepSearchToolInvocationProjector extends AbstractToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return "deep_search".equals(toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        JsonNode root = readJson(invocation == null ? null : invocation.getOutputJson());
        List<ProjectedReplayEvent> events = new ArrayList<>();
        for (JsonNode stage : root.path("stages")) {
            String stageType = stage.path("stage").asText();
            if (StringUtils.isBlank(stageType)) {
                continue;
            }
            Map<String, Object> resultMap = switch (stageType) {
                case "extend" -> buildExtendResult(root, stage);
                case "search" -> buildSearchResult(root, stage);
                case "report" -> buildReportResult(root, stage);
                default -> Map.of();
            };
            if (resultMap.isEmpty()) {
                continue;
            }
            events.add(buildTaskEvent(
                    state,
                    invocation,
                    "deep_search",
                    buildStructuredToolResponse(invocation, "deep_search", resultMap),
                    buildArtifactRefs(artifacts)
            ));
        }
        return events;
    }

    private Map<String, Object> buildExtendResult(JsonNode root, JsonNode stage) {
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("messageType", "extend");
        resultMap.put("isFinal", true);
        resultMap.put("searchFinish", false);
        resultMap.put("query", root.path("query").asText(""));
        Map<String, Object> searchResult = new LinkedHashMap<>();
        searchResult.put("query", readStringList(stage.path("queries")));
        searchResult.put("docs", List.of());
        resultMap.put("searchResult", searchResult);
        return resultMap;
    }

    private Map<String, Object> buildSearchResult(JsonNode root, JsonNode stage) {
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("messageType", "search");
        resultMap.put("isFinal", true);
        resultMap.put("searchFinish", true);
        resultMap.put("query", root.path("query").asText(""));

        List<String> queries = new ArrayList<>();
        List<List<Map<String, Object>>> docs = new ArrayList<>();
        for (JsonNode result : stage.path("results")) {
            queries.add(result.path("query").asText(""));
            List<Map<String, Object>> docList = new ArrayList<>();
            for (JsonNode doc : result.path("docs")) {
                Map<String, Object> docMap = new LinkedHashMap<>();
                docMap.put("title", doc.path("title").asText(""));
                docMap.put("link", doc.path("link").asText(""));
                if (StringUtils.isNotBlank(doc.path("content").asText())) {
                    docMap.put("content", doc.path("content").asText());
                }
                docList.add(docMap);
            }
            docs.add(docList);
        }
        Map<String, Object> searchResult = new LinkedHashMap<>();
        searchResult.put("query", queries);
        searchResult.put("docs", docs);
        resultMap.put("searchResult", searchResult);
        return resultMap;
    }

    private Map<String, Object> buildReportResult(JsonNode root, JsonNode stage) {
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("messageType", "report");
        resultMap.put("isFinal", true);
        resultMap.put("query", root.path("query").asText(""));
        resultMap.put("answer", stage.path("answer").asText(""));
        return resultMap;
    }

    private List<String> readStringList(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return result;
        }
        for (JsonNode item : arrayNode) {
            result.add(item.asText(""));
        }
        return result;
    }
}

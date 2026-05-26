package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearchDoc;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearchQueryResult;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearchStage;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearchToolOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        DeepSearchToolOutput output = invocation != null && invocation.getStructuredOutput() instanceof DeepSearchToolOutput structuredOutput
                ? structuredOutput
                : null;
        if (output == null || output.getStages() == null || output.getStages().isEmpty()) {
            return List.of();
        }

        List<ProjectedReplayEvent> events = new ArrayList<>();
        for (DeepSearchStage stage : output.getStages()) {
            String stageType = stage == null ? null : stage.getStage();
            if (StringUtils.isBlank(stageType)) {
                continue;
            }
            Map<String, Object> resultMap = switch (stageType) {
                case "extend" -> buildExtendResult(output, stage);
                case "search" -> buildSearchResult(output, stage);
                case "report" -> buildReportResult(output, stage);
                default -> Map.of();
            };
            if (resultMap.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> stageArtifactRefs = resolveStageArtifactRefs(stageType, artifacts);
            events.add(buildTaskEvent(
                    state,
                    invocation,
                    "deep_search",
                    buildStructuredToolResponse(invocation, "deep_search", resultMap),
                    stageArtifactRefs
            ));
        }
        return events;
    }

    private Map<String, Object> buildExtendResult(DeepSearchToolOutput output, DeepSearchStage stage) {
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("messageType", "extend");
        resultMap.put("isFinal", true);
        resultMap.put("searchFinish", false);
        resultMap.put("query", output.getQuery());
        Map<String, Object> searchResult = new LinkedHashMap<>();
        searchResult.put("query", stage.getQueries() == null ? List.of() : stage.getQueries());
        searchResult.put("docs", List.of());
        resultMap.put("searchResult", searchResult);
        return resultMap;
    }

    private Map<String, Object> buildSearchResult(DeepSearchToolOutput output, DeepSearchStage stage) {
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("messageType", "search");
        resultMap.put("isFinal", true);
        resultMap.put("searchFinish", true);
        resultMap.put("query", output.getQuery());

        List<String> queries = new ArrayList<>();
        List<List<Map<String, Object>>> docs = new ArrayList<>();
        for (DeepSearchQueryResult item : stage.getResults()) {
            if (item == null) {
                continue;
            }
            queries.add(StringUtils.defaultString(item.getQuery()));
            List<Map<String, Object>> docList = new ArrayList<>();
            for (DeepSearchDoc doc : item.getDocs()) {
                if (doc == null) {
                    continue;
                }
                Map<String, Object> docMap = new LinkedHashMap<>();
                docMap.put("title", StringUtils.defaultString(doc.getTitle()));
                docMap.put("link", StringUtils.defaultString(doc.getLink()));
                if (StringUtils.isNotBlank(doc.getSummary())) {
                    docMap.put("content", doc.getSummary());
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

    private Map<String, Object> buildReportResult(DeepSearchToolOutput output, DeepSearchStage stage) {
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("messageType", "report");
        resultMap.put("isFinal", true);
        resultMap.put("query", output.getQuery());
        resultMap.put("answer", StringUtils.defaultString(stage.getAnswer()));
        return resultMap;
    }

    private List<Map<String, Object>> resolveStageArtifactRefs(String stageType, List<ArtifactView> artifacts) {
        if (!"report".equals(stageType) || artifacts == null || artifacts.isEmpty()) {
            return List.of();
        }
        List<ArtifactView> finalArtifacts = artifacts.stream()
                .filter(artifact -> artifact != null && StringUtils.isNotBlank(artifact.getFileName()))
                .filter(artifact -> !StringUtils.endsWithIgnoreCase(artifact.getFileName(), "_search_result.txt"))
                .collect(Collectors.toList());
        return buildArtifactRefs(finalArtifacts);
    }
}

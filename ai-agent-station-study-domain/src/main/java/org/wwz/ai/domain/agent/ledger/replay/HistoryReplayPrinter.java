package org.wwz.ai.domain.agent.ledger.replay;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.model.constant.Constants;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史回放打印收口。
 */
public class HistoryReplayPrinter {

    public List<GptProcessResult> ensureReadableConclusion(DialogueRunView run, List<GptProcessResult> frames) {
        List<GptProcessResult> result = frames == null ? new ArrayList<>() : new ArrayList<>(frames);
        if (hasReadableConclusion(result) || run == null || StringUtils.isBlank(run.getFinalSummaryText())) {
            return result;
        }
        result.add(buildFallbackConclusion(run));
        return result;
    }

    private boolean hasReadableConclusion(List<GptProcessResult> frames) {
        if (CollectionUtils.isEmpty(frames)) {
            return false;
        }
        for (GptProcessResult frame : frames) {
            if (frame == null || frame.getResultMap() == null) {
                continue;
            }
            Object eventData = frame.getResultMap().get("eventData");
            if (!(eventData instanceof Map<?, ?> eventDataMap)) {
                continue;
            }
            Object resultMap = eventDataMap.get("resultMap");
            if (!(resultMap instanceof Map<?, ?> nestedMap)) {
                continue;
            }
            Object messageType = nestedMap.get("messageType");
            if ("result".equals(messageType) || "task_summary".equals(messageType)) {
                return true;
            }
        }
        return false;
    }

    private GptProcessResult buildFallbackConclusion(DialogueRunView run) {
        SummaryReplayResultResolver.ResolvedSummary resolvedSummary =
                SummaryReplayResultResolver.resolve(run.getFinalSummaryText(), run.getArtifactSummaries());
        Map<String, Object> nestedResultMap = new LinkedHashMap<>();
        nestedResultMap.put("messageType", "result");
        nestedResultMap.put("isFinal", true);
        nestedResultMap.put("taskSummary", resolvedSummary.getSummaryText());
        nestedResultMap.put("result", resolvedSummary.getSummaryText());
        if (!resolvedSummary.getFileList().isEmpty()) {
            nestedResultMap.put("fileList", resolvedSummary.getFileList());
        }

        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("taskId", run.getRequestId() + "-summary");
        eventData.put("taskOrder", 1);
        eventData.put("messageType", "task");
        eventData.put("messageOrder", 1);
        eventData.put("messageId", run.getRequestId() + "-summary");
        if (!resolvedSummary.getArtifactRefs().isEmpty()) {
            eventData.put("artifactRefs", resolvedSummary.getArtifactRefs());
        }
        eventData.put("resultMap", nestedResultMap);

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("agentType", "history");
        resultMap.put("multiAgent", new LinkedHashMap<>());
        resultMap.put("eventData", eventData);

        return GptProcessResult.builder()
                .status(Constants.SUCCESS)
                .finished(true)
                .reqId(run.getRequestId())
                .resultMap(resultMap)
                .response(resolvedSummary.getSummaryText())
                .responseAll(resolvedSummary.getSummaryText())
                .build();
    }
}

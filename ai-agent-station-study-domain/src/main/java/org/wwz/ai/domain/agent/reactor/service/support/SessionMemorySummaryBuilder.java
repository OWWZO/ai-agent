package org.wwz.ai.domain.agent.reactor.service.support;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryFact;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionTurnMemory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话压缩摘要构造器
 */
@Component
public class SessionMemorySummaryBuilder {

    public String buildSummary(String existingSummary,
                               List<SessionTurnMemory> turnsToCompact,
                               int maxLength) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(existingSummary)) {
            builder.append("此前摘要：").append(existingSummary.trim());
        }

        for (SessionTurnMemory turn : turnsToCompact) {
            if (turn == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("- 第")
                    .append(turn.getSortOrder() == null ? "?" : turn.getSortOrder())
                    .append("轮：用户提出“")
                    .append(abbreviate(turn.getUserMessage(), 80))
                    .append("”；系统已回应“")
                    .append(abbreviate(turn.getAssistantMessage(), 120))
                    .append("”。");
        }

        String summary = builder.toString().trim();
        if (summary.length() <= maxLength) {
            return summary;
        }
        return summary.substring(0, maxLength);
    }

    public List<SessionMemoryFact> buildFacts(List<SessionMemoryFact> existingFacts,
                                              List<SessionTurnMemory> turnsToCompact) {
        Map<String, Set<String>> factBuckets = new LinkedHashMap<>();
        appendFacts(factBuckets, existingFacts);

        for (SessionTurnMemory turn : turnsToCompact) {
            if (turn == null) {
                continue;
            }
            String userMessage = normalize(turn.getUserMessage());
            String assistantMessage = normalize(turn.getAssistantMessage());
            if (userMessage != null) {
                addFact(factBuckets, "goal", abbreviate(userMessage, 120));
                if (containsConstraintSignal(userMessage)) {
                    addFact(factBuckets, "constraint", abbreviate(userMessage, 120));
                }
            }
            if (assistantMessage != null) {
                addFact(factBuckets, "conclusion", abbreviate(assistantMessage, 160));
            }
        }

        List<SessionMemoryFact> facts = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : factBuckets.entrySet()) {
            for (String content : entry.getValue()) {
                facts.add(SessionMemoryFact.builder()
                        .category(entry.getKey())
                        .content(content)
                        .build());
            }
        }
        return facts;
    }

    private void appendFacts(Map<String, Set<String>> factBuckets, List<SessionMemoryFact> facts) {
        if (CollectionUtils.isEmpty(facts)) {
            return;
        }
        for (SessionMemoryFact fact : facts) {
            if (fact == null || !StringUtils.hasText(fact.getContent())) {
                continue;
            }
            addFact(factBuckets, fact.getCategory(), fact.getContent().trim());
        }
    }

    private void addFact(Map<String, Set<String>> factBuckets, String category, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        factBuckets.computeIfAbsent(category == null ? "fact" : category, key -> new LinkedHashSet<>())
                .add(content.trim());
    }

    private boolean containsConstraintSignal(String userMessage) {
        return userMessage.contains("必须")
                || userMessage.contains("请用")
                || userMessage.contains("只保留")
                || userMessage.contains("不要")
                || userMessage.contains("格式")
                || userMessage.contains("中文")
                || userMessage.contains("表格");
    }

    private String abbreviate(String text, int maxLength) {
        String normalized = normalize(text);
        if (normalized == null) {
            return "";
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim().replaceAll("\\s+", " ");
    }
}

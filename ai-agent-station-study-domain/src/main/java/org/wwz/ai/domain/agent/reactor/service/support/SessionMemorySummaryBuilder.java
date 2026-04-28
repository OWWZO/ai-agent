package org.wwz.ai.domain.agent.reactor.service.support;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryFact;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionTurnMemory;

import java.util.ArrayList;
import java.util.Arrays;
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

    private static final List<String> REQUIRED_SECTIONS = Arrays.asList(
            "Session Title",
            "Current State",
            "Task specification",
            "Files and Functions",
            "Workflow",
            "Errors & Corrections",
            "Codebase and System Documentation",
            "Learnings",
            "Key results",
            "Worklog");

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
            String userMessage = StringUtil.firstNonBlank(turn.getUserInputText(), "无用户输入");
            String assistantMessage = StringUtil.firstNonBlank(turn.getAssistantAnswerText(), "无最终回答");
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("- 第")
                    .append(turn.getSortOrder() == null ? "?" : turn.getSortOrder())
                    .append("轮：用户提出“")
                    .append(StringUtil.abbreviate(userMessage, 80, true))
                    .append("“”；系统已回应“”")
                    .append(StringUtil.abbreviate(assistantMessage, 120, true))
                    .append("”。");
        }

        String summary = builder.toString().trim();
        if (summary.length() <= maxLength) {
            return summary;
        }
        return summary.substring(0, maxLength);
    }

    /**
     * 对 LLM 输出做结构校正，确保 summary_text 始终维持 free-code 风格固定 section。
     */
    public String normalizeStructuredSummary(String generatedSummary, Integer maxLength) {
        String normalized = StringUtils.hasText(generatedSummary)
                ? generatedSummary.replace("\r\n", "\n").trim()
                : "";
        Map<String, StringBuilder> sections = extractSections(normalized);
        if (sections.isEmpty() && StringUtils.hasText(normalized)) {
            sections.put("Current State", new StringBuilder(normalized));
        }

        StringBuilder builder = new StringBuilder();
        for (String section : REQUIRED_SECTIONS) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("# ").append(section).append('\n');
            String content = sections.containsKey(section)
                    ? sections.get(section).toString().trim()
                    : "";
            if (StringUtils.hasText(content)) {
                builder.append(content);
            }
        }
        String summary = builder.toString().trim();
        if (maxLength == null || maxLength <= 0 || summary.length() <= maxLength) {
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
            String userMessage = normalize(turn.getUserInputText());
            String assistantMessage = normalize(turn.getAssistantAnswerText());
            if (userMessage != null) {
                addFact(factBuckets, "goal", StringUtil.abbreviate(userMessage, 120, true));
                if (containsConstraintSignal(userMessage)) {
                    addFact(factBuckets, "constraint", StringUtil.abbreviate(userMessage, 120, true));
                }
            }
            if (assistantMessage != null) {
                addFact(factBuckets, "conclusion", StringUtil.abbreviate(assistantMessage, 160, true));
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

    private Map<String, StringBuilder> extractSections(String generatedSummary) {
        Map<String, StringBuilder> sections = new LinkedHashMap<>();
        if (!StringUtils.hasText(generatedSummary)) {
            return sections;
        }

        String currentSection = null;
        for (String line : generatedSummary.split("\n")) {
            if (line.startsWith("# ")) {
                String title = line.substring(2).trim();
                if (REQUIRED_SECTIONS.contains(title)) {
                    currentSection = title;
                    sections.putIfAbsent(title, new StringBuilder());
                    continue;
                }
            }
            if (currentSection == null) {
                continue;
            }
            if (sections.get(currentSection).length() > 0) {
                sections.get(currentSection).append('\n');
            }
            sections.get(currentSection).append(line);
        }
        return sections;
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

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim().replaceAll("\\s+", " ");
    }
}

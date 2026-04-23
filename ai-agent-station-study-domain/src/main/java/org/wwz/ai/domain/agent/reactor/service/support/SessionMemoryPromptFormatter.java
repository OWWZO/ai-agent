package org.wwz.ai.domain.agent.reactor.service.support;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryFact;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionTurnMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptBlockType;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptContextBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * 把工作记忆格式化为可注入 prompt 的历史摘要文本
 */
@Component
public class SessionMemoryPromptFormatter {

    public String format(SessionWorkingMemory workingMemory) {
        if (workingMemory == null) {
            return "";
        }

        List<String> sections = new ArrayList<>();
        if (StringUtils.hasText(workingMemory.getSummaryText())) {
            sections.add("## 历史摘要\n" + workingMemory.getSummaryText().trim());
        }

        String factsSection = formatFacts(workingMemory.getFacts());
        if (StringUtils.hasText(factsSection)) {
            sections.add(factsSection);
        }

        String filesSection = formatFiles(workingMemory.getRestoredFiles());
        if (StringUtils.hasText(filesSection)) {
            sections.add(filesSection);
        }

        if (sections.isEmpty() && !CollectionUtils.isEmpty(workingMemory.getRecentTurns())) {
            sections.add(formatRecentTurns(workingMemory.getRecentTurns()));
        }

        return String.join("\n\n", sections).trim();
    }

    private String formatFacts(List<SessionMemoryFact> facts) {
        if (CollectionUtils.isEmpty(facts)) {
            return "";
        }

        StringBuilder builder = new StringBuilder("## 关键事实");
        for (SessionMemoryFact fact : facts) {
            if (fact == null || !StringUtils.hasText(fact.getContent())) {
                continue;
            }
            builder.append("\n- ")
                    .append(resolveCategoryLabel(fact.getCategory()))
                    .append("：")
                    .append(fact.getContent().trim());
        }
        return builder.toString();
    }

    private String formatFiles(List<FileInformation> files) {
        if (CollectionUtils.isEmpty(files)) {
            return "";
        }

        StringBuilder builder = new StringBuilder("## 可继续复用的历史文件");
        for (FileInformation file : files) {
            if (file == null || !StringUtils.hasText(file.getFileName())) {
                continue;
            }
            builder.append("\n- ")
                    .append(file.getFileName());
            if (StringUtils.hasText(file.getFileDesc())) {
                builder.append("：").append(file.getFileDesc().trim());
            }
        }
        return builder.toString();
    }

    private String formatRecentTurns(List<SessionTurnMemory> recentTurns) {
        StringBuilder builder = new StringBuilder("## 最近对话片段");
        for (SessionTurnMemory turn : recentTurns) {
            if (turn == null) {
                continue;
            }
            builder.append("\n- 第")
                    .append(turn.getSortOrder() == null ? "?" : turn.getSortOrder())
                    .append("轮 用户：")
                    .append(defaultText(turn.getUserMessage()))
                    .append("；助手：")
                    .append(defaultText(StringUtil.firstNonBlank(turn.getFinalAnswer(), turn.getAssistantMessage())));
            String transcriptSummary = summarizeBlocks(turn.getBlocks());
            if (StringUtils.hasText(transcriptSummary)) {
                builder.append("；链路：").append(transcriptSummary);
            }
        }
        return builder.toString();
    }

    private String summarizeBlocks(List<TranscriptContextBlock> blocks) {
        if (CollectionUtils.isEmpty(blocks)) {
            return "";
        }

        List<String> summaries = new ArrayList<>();
        for (TranscriptContextBlock block : blocks) {
            if (block == null || block.getBlockType() == null) {
                continue;
            }
            if (TranscriptBlockType.USER_INPUT == block.getBlockType()
                    || TranscriptBlockType.ASSISTANT_ANSWER == block.getBlockType()) {
                continue;
            }
            String label = switch (block.getBlockType()) {
                case ASSISTANT_THOUGHT -> "思考";
                case TOOL_USE -> "工具调用";
                case TOOL_RESULT -> Boolean.TRUE.equals(block.getReferenceOnly()) ? "工具结果(引用)" : "工具结果";
                case ARTIFACT_REFERENCE -> "产物引用";
                default -> null;
            };
            if (StringUtils.hasText(label)) {
                summaries.add(label);
            }
        }
        return summaries.isEmpty() ? "" : String.join(" -> ", summaries);
    }

    private String resolveCategoryLabel(String category) {
        if (!StringUtils.hasText(category)) {
            return "事实";
        }
        return switch (category) {
            case "goal" -> "目标";
            case "constraint" -> "约束";
            case "conclusion" -> "结论";
            case "pending" -> "待续";
            default -> "事实";
        };
    }

    private String defaultText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "无";
    }

}

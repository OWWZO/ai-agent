package org.wwz.ai.domain.agent.reactor.service.support;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.dto.Message;
import org.wwz.ai.domain.agent.reactor.agent.enums.ConversationAgentType;
import org.wwz.ai.domain.agent.reactor.agent.llm.LLM;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionTurnMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptContextBlock;
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;

import javax.annotation.Resource;
import java.util.List;
import java.util.UUID;

/**
 * 使用现有 LLM 装配生成 free-code 风格结构化会话记忆。
 */
@Slf4j
@Component
public class LlmSessionMemorySummaryGenerator implements SessionMemorySummaryGenerator {

    private static final int MAX_TURN_TEXT_LENGTH = 600;
    private static final int MAX_BLOCK_TEXT_LENGTH = 500;
    private static final int MAX_ARTIFACT_REF_LENGTH = 1500;

    @Resource
    private ReactorConfig reactorConfig;
    @Resource
    private SessionMemorySummaryBuilder summaryBuilder;

    @Override
    public String generate(GenerationRequest request) throws Exception {
        AgentContext context = AgentContext.builder()
                .requestId(StringUtils.hasText(request.getRequestId()) ? request.getRequestId() : UUID.randomUUID().toString())
                .sessionId(request.getSessionId())
                .query("session-memory-compact")
                .agentType(request.getAgentType())
                .isStream(false)
                .build();
        LLM llm = new LLM(resolveModelName(request.getAgentType()), "");
        String rawSummary = llm.ask(
                        context,
                        List.of(Message.userMessage(buildPrompt(request), null)),
                        List.of(),
                        false,
                        0.1)
                .get();
        log.info("生成结构化会话记忆完成 sessionId={}, boundarySortOrder={}",
                request.getSessionId(),
                request.getBoundarySortOrder());
        return summaryBuilder.normalizeStructuredSummary(rawSummary, request.getMaxLength());
    }

    private String resolveModelName(Integer agentType) {
        if (agentType != null && agentType.equals(ConversationAgentType.PLAN_SOLVE.getCode())) {
            return reactorConfig.getPlannerModelName();
        }
        return reactorConfig.getReactModelName();
    }

    private String buildPrompt(GenerationRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                你是会话压缩器，需要把一段长对话重写成可继续工作的结构化 session memory。
                你必须只输出 Markdown，不要输出解释、前言或代码块。

                输出必须严格包含以下一级标题，并保持该顺序：
                # Session Title
                # Current State
                # Task specification
                # Files and Functions
                # Workflow
                # Errors & Corrections
                # Codebase and System Documentation
                # Learnings
                # Key results
                # Worklog

                写作要求：
                1. 以“当前仍在做什么、下一步做什么”为中心更新 Current State。
                2. 基于已有记忆增量更新，不要把新旧轮次简单串成流水账。
                3. 对超长输出、长报告、diff、stdout/stderr 只保留关键结果和稳定引用，不要回灌全文。
                4. 保留用户的关键约束、文件产物、重要工作流、错误修正、系统知识和最近工作轨迹。
                5. 信息密度要高，但不要写空洞套话。
                6. 若某节暂时没有新内容，可保留为空白或极简描述，不要写“无”之类占位废话。
                7. 总长度尽量控制在 %d 字以内。
                """.formatted(request.getMaxLength() == null ? reactorConfig.getSessionMemorySummaryMaxLength() : request.getMaxLength()));

        builder.append("\n\n## 现有结构化记忆\n");
        if (StringUtils.hasText(request.getExistingSummary())) {
            builder.append(request.getExistingSummary().trim());
        } else {
            builder.append("（当前无旧记忆，需要从新增历史构建首版记忆）");
        }

        builder.append("\n\n## 新增已完成历史 transcript\n");
        if (CollectionUtils.isEmpty(request.getTurnsToCompact())) {
            builder.append("（没有新增历史）");
        } else {
            for (SessionTurnMemory turn : request.getTurnsToCompact()) {
                builder.append(renderTurn(turn));
            }
        }

        builder.append("\n\n## 稳定 artifact 引用\n");
        if (CollectionUtils.isEmpty(request.getArtifactRefs())) {
            builder.append("[]");
        } else {
            builder.append(StringUtil.abbreviate(JSON.toJSONString(request.getArtifactRefs(), true), MAX_ARTIFACT_REF_LENGTH, true));
        }

        builder.append("\n\n## 边界信息\n");
        builder.append("本次压缩完成后，最新边界 sortOrder=").append(request.getBoundarySortOrder());
        return builder.toString();
    }

    private String renderTurn(SessionTurnMemory turn) {
        StringBuilder builder = new StringBuilder();
        String userInputText = StringUtil.firstNonBlank(turn.getUserInputText(), "无用户输入");
        String assistantAnswerText = StringUtil.firstNonBlank(turn.getAssistantAnswerText(), "无最终回答");
        builder.append("\n### Turn ")
                .append(turn.getSortOrder() == null ? "?" : turn.getSortOrder())
                .append('\n');
        builder.append("- User: ").append(StringUtil.abbreviate(userInputText, MAX_TURN_TEXT_LENGTH, true)).append('\n');
        if (!CollectionUtils.isEmpty(turn.getBlocks())) {
            builder.append("- Transcript:\n");
            for (TranscriptContextBlock block : turn.getBlocks()) {
                if (block == null) {
                    continue;
                }
                builder.append("  - ")
                        .append(block.getBlockType() == null ? "UNKNOWN" : block.getBlockType().name())
                        .append(": ")
                        .append(StringUtil.abbreviate(block.getText(), MAX_BLOCK_TEXT_LENGTH, true));
                if (StringUtils.hasText(block.getToolName())) {
                    builder.append(" | tool=").append(block.getToolName());
                }
                if (StringUtils.hasText(block.getToolArgumentsJson())) {
                    builder.append(" | args=").append(StringUtil.abbreviate(block.getToolArgumentsJson(), MAX_BLOCK_TEXT_LENGTH, true));
                }
                if (!CollectionUtils.isEmpty(block.getArtifactRefs())) {
                    builder.append(" | refs=")
                            .append(StringUtil.abbreviate(JSON.toJSONString(block.getArtifactRefs()), MAX_BLOCK_TEXT_LENGTH, true));
                }
                builder.append('\n');
            }
        }
        builder.append("- Final answer: ")
                .append(StringUtil.abbreviate(assistantAnswerText, MAX_TURN_TEXT_LENGTH, true))
                .append('\n');
        return builder.toString();
    }

}

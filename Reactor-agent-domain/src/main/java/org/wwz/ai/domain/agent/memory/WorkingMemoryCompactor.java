package org.wwz.ai.domain.agent.memory;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.llm.TokenCounter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 工作记忆压缩纯算法（对齐 cc-haha 分层）：
 * microcompact → session-memory compact → full compact 辅助 → drop-oldest。
 * 切片永不拆开 tool_use/tool_result。
 */
public final class WorkingMemoryCompactor {

    private final TokenCounter tokenCounter;

    public WorkingMemoryCompactor() {
        this(new TokenCounter());
    }

    public WorkingMemoryCompactor(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter == null ? new TokenCounter() : tokenCounter;
    }

    public int estimateTokens(List<Message> messages) {
        return tokenCounter.estimateMessages(messages);
    }

    public boolean shouldCompact(List<Message> messages, CompactionBudget budget) {
        if (budget == null || !budget.isEnabled() || messages == null || messages.isEmpty()) {
            return false;
        }
        return estimateTokens(messages) >= budget.threshold();
    }

    /**
     * Microcompact：清掉较早的 TOOL 结果正文（保留最近 N 条完整/截断），对齐 cc-haha time-based MC 的 content clear。
     */
    public List<Message> microcompact(List<Message> messages, CompactionBudget budget) {
        if (messages == null || messages.isEmpty() || budget == null || !budget.isMicroEnabled()) {
            return messages == null ? List.of() : List.copyOf(messages);
        }
        int keepRecent = Math.max(budget.getMicroKeepRecentToolResults(), 0);
        int maxChars = Math.max(budget.getMicroToolResultMaxChars(), 0);

        List<Integer> toolIndexes = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (m != null && m.getRole() == RoleType.TOOL) {
                toolIndexes.add(i);
            }
        }
        if (toolIndexes.isEmpty()) {
            return List.copyOf(messages);
        }

        Set<Integer> keepFull = new HashSet<>();
        int from = Math.max(0, toolIndexes.size() - keepRecent);
        for (int i = from; i < toolIndexes.size(); i++) {
            keepFull.add(toolIndexes.get(i));
        }

        List<Message> result = new ArrayList<>(messages.size());
        boolean changed = false;
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (m == null || m.getRole() != RoleType.TOOL) {
                result.add(m);
                continue;
            }
            String content = m.getContent();
            if (!keepFull.contains(i)) {
                if (!CompactionBudget.CLEARED_TOOL_RESULT.equals(content)) {
                    changed = true;
                    result.add(Message.builder()
                            .role(RoleType.TOOL)
                            .content(CompactionBudget.CLEARED_TOOL_RESULT)
                            .toolCallId(m.getToolCallId())
                            .base64Image(null)
                            .build());
                    continue;
                }
                result.add(m);
                continue;
            }
            if (maxChars > 0 && content != null && content.length() > maxChars) {
                changed = true;
                result.add(Message.builder()
                        .role(RoleType.TOOL)
                        .content(content.substring(0, maxChars) + "\n...[truncated tool result]")
                        .toolCallId(m.getToolCallId())
                        .base64Image(null)
                        .build());
            } else {
                result.add(m);
            }
        }
        return changed ? List.copyOf(result) : List.copyOf(messages);
    }

    /**
     * Session-memory style compact：用已有摘要/notes + recent tail，避免再打全量 LLM。
     * @return 成功且低于阈值时返回新列表；否则 empty optional 语义用 null
     */
    public List<Message> trySessionMemoryCompact(List<Message> messages,
                                                 String sessionNotes,
                                                 CompactionBudget budget) {
        if (messages == null || messages.isEmpty() || budget == null || !budget.isSessionMemoryEnabled()) {
            return null;
        }
        String notes = StringUtils.trimToEmpty(sessionNotes);
        List<Message> body = messages;
        if (StringUtils.isBlank(notes) && isCompactSummaryMessage(messages.get(0))) {
            notes = messages.get(0).getContent();
            body = messages.size() > 1 ? messages.subList(1, messages.size()) : List.of();
        }
        if (StringUtils.isBlank(notes)) {
            return null;
        }

        List<Message> keep = keepRecentTail(body, budget);
        // 若 body 本身就是 full list 且 notes 来自外部，keep 从 body 切
        String reinject = notes;
        if (!notes.contains("This session is being continued from a previous conversation")) {
            reinject = CompactionPrompt.wrapSummaryForReinject(
                    CompactionPrompt.formatCompactSummary(notes), !keep.isEmpty());
        } else if (!keep.isEmpty() && !notes.contains("Recent messages are preserved verbatim")) {
            reinject = notes + "\n\nRecent messages are preserved verbatim.";
        }
        List<Message> post = buildPostCompactMessages(reinject, keep);
        if (estimateTokens(post) >= budget.threshold()) {
            return null;
        }
        return post;
    }

    public boolean isCompactSummaryMessage(Message message) {
        if (message == null || message.getRole() != RoleType.USER) {
            return false;
        }
        String content = message.getContent();
        return content != null && content.contains("This session is being continued from a previous conversation");
    }

    /**
     * 从已压缩前缀抽出可复用的 session notes 正文。
     */
    public String extractSessionNotes(List<Message> messages) {
        if (messages == null || messages.isEmpty() || !isCompactSummaryMessage(messages.get(0))) {
            return null;
        }
        return messages.get(0).getContent();
    }

    /**
     * P0：从最旧侧整段丢弃，直到低于阈值；永不拆开 tool_use/tool_result。
     */
    public List<Message> dropOldestToFit(List<Message> messages, CompactionBudget budget) {
        if (messages == null || messages.isEmpty() || budget == null) {
            return messages == null ? List.of() : messages;
        }
        int threshold = budget.threshold();
        if (estimateTokens(messages) <= threshold) {
            return List.copyOf(messages);
        }

        List<Message> current = new ArrayList<>(messages);
        // 至少保留尾部 keepMin 规模；若仍超阈值则继续砍到至少 1 条
        int floor = Math.max(1, findKeepStartIndex(current, budget));
        while (estimateTokens(current) > threshold && current.size() > floor) {
            int cut = nextSafeDropCount(current);
            if (cut <= 0) {
                break;
            }
            // 不要砍穿 keep floor
            int maxDrop = current.size() - floor;
            cut = Math.min(cut, maxDrop);
            if (cut <= 0) {
                break;
            }
            current = new ArrayList<>(current.subList(cut, current.size()));
        }

        // 仍超阈值：允许突破 floor，但保留至少 1 条且 tool-safe
        while (estimateTokens(current) > threshold && current.size() > 1) {
            int cut = nextSafeDropCount(current);
            if (cut <= 0 || cut >= current.size()) {
                // 强制丢最前一条（若是 assistant tool 则扩到完整 pair）
                cut = Math.max(1, expandForwardToCloseToolPairs(current, 1));
                cut = Math.min(cut, current.size() - 1);
            }
            current = new ArrayList<>(current.subList(cut, current.size()));
        }
        return List.copyOf(current);
    }

    /**
     * 计算应保留的 recent tail 起始下标（含）。
     * 对齐 cc-haha calculateMessagesToKeepIndex：满足 minTokens + minTextMsgs，不超过 maxTokens。
     */
    public int findKeepStartIndex(List<Message> messages, CompactionBudget budget) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int n = messages.size();
        int minTokens = Math.max(budget.getKeepMinTokens(), 0);
        int minText = Math.max(budget.getKeepMinTextMessages(), 0);
        int maxTokens = Math.max(budget.getKeepMaxTokens(), minTokens);

        int start = n;
        int tokens = 0;
        int textMsgs = 0;
        while (start > 0) {
            int candidate = start - 1;
            int add = tokenCounter.estimateOneMessage(messages.get(candidate));
            if (tokens + add > maxTokens && tokens >= minTokens && textMsgs >= minText) {
                break;
            }
            start = candidate;
            tokens += add;
            if (isTextMessage(messages.get(candidate))) {
                textMsgs++;
            }
            if (tokens >= minTokens && textMsgs >= minText) {
                // 继续向后扩一点直到刚好跨过 min，然后停（已满足）
                // 若还有空间且未到 0，允许再吃直到 max
                if (tokens >= maxTokens) {
                    break;
                }
            }
        }
        // 若还没满足 min，继续向后扩到 0 或 max
        while (start > 0 && (tokens < minTokens || textMsgs < minText)) {
            int candidate = start - 1;
            int add = tokenCounter.estimateOneMessage(messages.get(candidate));
            if (tokens + add > maxTokens && tokens > 0) {
                break;
            }
            start = candidate;
            tokens += add;
            if (isTextMessage(messages.get(candidate))) {
                textMsgs++;
            }
        }
        return adjustIndexToPreserveToolPairs(messages, start);
    }

    public List<Message> keepRecentTail(List<Message> messages, CompactionBudget budget) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int start = findKeepStartIndex(messages, budget);
        if (start <= 0) {
            return List.copyOf(messages);
        }
        return List.copyOf(messages.subList(start, messages.size()));
    }

    /**
     * 组装 post-compact 消息：summary + recent tail。
     */
    public List<Message> buildPostCompactMessages(String summaryContent, List<Message> messagesToKeep) {
        List<Message> result = new ArrayList<>();
        if (StringUtils.isNotBlank(summaryContent)) {
            result.add(Message.userMessage(summaryContent, null));
        }
        if (messagesToKeep != null && !messagesToKeep.isEmpty()) {
            result.addAll(messagesToKeep);
        }
        return List.copyOf(result);
    }

    /**
     * 为摘要调用准备消息：截断过长 content，图片改为标记。
     */
    public List<Message> prepareMessagesForSummarizer(List<Message> messages, int contentCharLimit) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int limit = contentCharLimit <= 0 ? 4000 : contentCharLimit;
        List<Message> prepared = new ArrayList<>(messages.size());
        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            String content = message.getContent();
            if (content != null && content.length() > limit) {
                content = content.substring(0, limit) + "\n...[truncated for compaction]";
            }
            if (StringUtils.isNotBlank(message.getBase64Image())) {
                content = (content == null ? "" : content) + "\n[image]";
            }
            prepared.add(Message.builder()
                    .role(message.getRole())
                    .content(content)
                    .toolCallId(message.getToolCallId())
                    .toolCalls(message.getToolCalls())
                    .build());
        }
        return prepared;
    }

    /**
     * 保证 start 不会落在 tool_result 之前缺少 tool_use，也不会拆开 assistant+tool 组。
     */
    public int adjustIndexToPreserveToolPairs(List<Message> messages, int start) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int n = messages.size();
        int idx = Math.max(0, Math.min(start, n));
        if (idx == 0 || idx >= n) {
            return idx;
        }

        // 收集 keep 区间内 tool_result 所需的 tool_call_id
        Set<String> neededToolCallIds = new HashSet<>();
        for (int i = idx; i < n; i++) {
            Message m = messages.get(i);
            if (m != null && m.getRole() == RoleType.TOOL && StringUtils.isNotBlank(m.getToolCallId())) {
                neededToolCallIds.add(m.getToolCallId());
            }
        }
        if (neededToolCallIds.isEmpty()) {
            return idx;
        }

        // 向前扩展直到所有 tool_call_id 都有对应 assistant tool_calls
        int expanded = idx;
        Set<String> provided = new HashSet<>();
        for (int i = idx; i < n; i++) {
            Message m = messages.get(i);
            if (m == null || m.getRole() != RoleType.ASSISTANT || m.getToolCalls() == null) {
                continue;
            }
            for (ToolCall tc : m.getToolCalls()) {
                if (tc != null && StringUtils.isNotBlank(tc.getId())) {
                    provided.add(tc.getId());
                }
            }
        }
        while (expanded > 0 && !provided.containsAll(neededToolCallIds)) {
            expanded--;
            Message m = messages.get(expanded);
            if (m != null && m.getRole() == RoleType.ASSISTANT && m.getToolCalls() != null) {
                for (ToolCall tc : m.getToolCalls()) {
                    if (tc != null && StringUtils.isNotBlank(tc.getId())) {
                        provided.add(tc.getId());
                    }
                }
            }
            if (m != null && m.getRole() == RoleType.TOOL && StringUtils.isNotBlank(m.getToolCallId())) {
                neededToolCallIds.add(m.getToolCallId());
            }
        }
        return expanded;
    }

    private int nextSafeDropCount(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        // 丢掉从 0 开始的最小安全前缀：若首条是 assistant(with tools)，扩到其全部 tool results 之后
        return expandForwardToCloseToolPairs(messages, 1);
    }

    private int expandForwardToCloseToolPairs(List<Message> messages, int endExclusive) {
        int n = messages.size();
        int end = Math.max(0, Math.min(endExclusive, n));
        if (end == 0) {
            return 0;
        }
        Set<String> openToolCalls = new HashSet<>();
        for (int i = 0; i < end; i++) {
            Message m = messages.get(i);
            if (m == null) {
                continue;
            }
            if (m.getRole() == RoleType.ASSISTANT && m.getToolCalls() != null) {
                for (ToolCall tc : m.getToolCalls()) {
                    if (tc != null && StringUtils.isNotBlank(tc.getId())) {
                        openToolCalls.add(tc.getId());
                    }
                }
            }
            if (m.getRole() == RoleType.TOOL && StringUtils.isNotBlank(m.getToolCallId())) {
                openToolCalls.remove(m.getToolCallId());
            }
        }
        while (end < n && !openToolCalls.isEmpty()) {
            Message m = messages.get(end);
            end++;
            if (m == null) {
                continue;
            }
            if (m.getRole() == RoleType.ASSISTANT && m.getToolCalls() != null) {
                for (ToolCall tc : m.getToolCalls()) {
                    if (tc != null && StringUtils.isNotBlank(tc.getId())) {
                        openToolCalls.add(tc.getId());
                    }
                }
            }
            if (m.getRole() == RoleType.TOOL && StringUtils.isNotBlank(m.getToolCallId())) {
                openToolCalls.remove(m.getToolCallId());
            }
        }
        return end;
    }

    private boolean isTextMessage(Message message) {
        if (message == null || message.getRole() == null) {
            return false;
        }
        if (message.getRole() == RoleType.USER) {
            return true;
        }
        if (message.getRole() == RoleType.ASSISTANT) {
            return message.getToolCalls() == null || message.getToolCalls().isEmpty();
        }
        return false;
    }
}

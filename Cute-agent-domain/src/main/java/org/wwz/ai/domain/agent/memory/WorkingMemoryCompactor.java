package org.wwz.ai.domain.agent.memory;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.llm.TokenCounter;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 工作记忆压缩纯算法：
 * microcompact → session-memory compact → full compact 辅助 → drop-oldest。
 * 切片永不拆开 tool_use/tool_result。
 */
public final class WorkingMemoryCompactor {

    public record CompactionWindow(int headEnd, int tailStart, String previousSummary) {
        public int middleStart() { return headEnd; }
    }

    private static final int MAX_TAIL_MESSAGE_FLOOR = 8;

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

    /**
     * 整包粗估：system + tools + messages，与 Hermes / ContextRing 同一口径。
     */
    public int estimateRequestTokens(String systemPrompt, List<Message> messages, ToolCollection tools) {
        Message system = StringUtils.isBlank(systemPrompt) ? null : Message.systemMessage(systemPrompt, null);
        return tokenCounter.estimatePrompt(system, messages, tools).getEstimatedTotalTokens();
    }

    public int estimateFixedPromptTokens(String systemPrompt, ToolCollection tools) {
        Message system = StringUtils.isBlank(systemPrompt) ? null : Message.systemMessage(systemPrompt, null);
        int systemTokens = system == null ? 0 : tokenCounter.estimateTokens(system.getContent());
        return systemTokens + tokenCounter.estimateTools(tools);
    }

    public boolean shouldCompact(List<Message> messages, CompactionBudget budget) {
        return shouldCompact(messages, budget, null, null);
    }

    public boolean shouldCompact(List<Message> messages,
                                 CompactionBudget budget,
                                 String systemPrompt,
                                 ToolCollection tools) {
        if (budget == null || !budget.isEnabled() || messages == null || messages.isEmpty()) {
            return false;
        }
        return estimateRequestTokens(systemPrompt, messages, tools) >= budget.threshold();
    }

    public boolean shouldCompact(int currentTokens, CompactionBudget budget) {
        return budget != null && budget.isEnabled() && currentTokens >= budget.threshold();
    }

    /**
     * Microcompact：清掉较早的 TOOL 结果正文（保留最近 N 条完整/截断）。
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
        // 只保留最近的工具结果正文，其余结果清空但保留 toolCallId，保证消息协议仍然成对。
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
                            .originMessageKey(m.getOriginMessageKey())
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
                        .originMessageKey(m.getOriginMessageKey())
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
        int handoff = findFirstHandoff(messages);
        if (StringUtils.isBlank(notes) && handoff >= 0) {
            notes = messages.get(handoff).getContent();
            body = new ArrayList<>(messages);
            body.remove(handoff);
        }
        if (StringUtils.isBlank(notes)) {
            return null;
        }

        List<Message> keep = keepRecentTail(body, budget);
        // 已包装的 notes 再清洗一次，防止历史轮次残留 <analysis>/<summary> 污染主 Agent。
        String reinject;
        if (notes.contains(CompactionPrompt.CONTEXT_COMPACTION_PREFIX)) {
            reinject = CompactionPrompt.wrapSummaryForReinject(
                    CompactionPrompt.unwrapHandoffBody(notes), !keep.isEmpty());
        } else if (notes.contains(CompactionPrompt.LEGACY_CONTINUATION)) {
            reinject = CompactionPrompt.wrapSummaryForReinject(
                    CompactionPrompt.unwrapHandoffBody(notes), !keep.isEmpty());
        } else {
            reinject = CompactionPrompt.wrapSummaryForReinject(
                    CompactionPrompt.formatCompactSummary(notes), !keep.isEmpty());
        }
        // 摘要放在前缀、最近消息原样保留在后缀，既缩短上下文又维持最近工具调用的可解释性。
        List<Message> post = buildPostCompactMessages(reinject, keep);
        if (estimateTokens(post) >= budget.threshold()) {
            return null;
        }
        return post;
    }

    public boolean isCompactSummaryMessage(Message message) {
        if (message == null || (message.getRole() != RoleType.USER && message.getRole() != RoleType.ASSISTANT)) {
            return false;
        }
        String content = message.getContent();
        return content != null && (content.contains(CompactionPrompt.CONTEXT_COMPACTION_PREFIX)
                || content.contains(CompactionPrompt.END_MARKER)
                || content.contains(CompactionPrompt.MERGE_DELIMITER)
                || content.contains(CompactionPrompt.LEGACY_CONTINUATION));
    }

    /**
     * 从已压缩前缀抽出可复用的 session notes 正文。
     */
    public String extractSessionNotes(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        int index = findFirstHandoff(messages);
        return index < 0 ? null : messages.get(index).getContent();
    }

    private int findFirstHandoff(List<Message> messages) {
        int limit = Math.min(messages.size(), 12);
        for (int i = 0; i < limit; i++) if (isCompactSummaryMessage(messages.get(i))) return i;
        return -1;
    }

    /**
     * P0：从最旧侧整段丢弃，直到低于阈值；永不拆开 tool_use/tool_result。
     */
    public List<Message> dropOldestToFit(List<Message> messages, CompactionBudget budget) {
        return dropOldestToFit(messages, budget, false);
    }

    public List<Message> dropOldestToFit(List<Message> messages, CompactionBudget budget, boolean protectHandoff) {
        return dropOldestToFit(messages, budget, protectHandoff, 0);
    }

    public List<Message> dropOldestToFit(List<Message> messages,
                                         CompactionBudget budget,
                                         boolean protectHandoff,
                                         int extraFixedTokens) {
        if (messages == null || messages.isEmpty() || budget == null) {
            return messages == null ? List.of() : messages;
        }
        int threshold = Math.max(budget.threshold() - Math.max(extraFixedTokens, 0), 1);
        if (estimateTokens(messages) <= threshold) {
            return List.copyOf(messages);
        }
        if (protectHandoff) {
            int handoff = findFirstHandoff(messages);
            if (handoff >= 0) {
                return dropOldestPreservingHandoff(messages, threshold, handoff);
            }
        }

        List<Message> current = new ArrayList<>(messages);
        // 至少保留尾部 keepMin 规模；若仍超阈值则继续砍到至少 1 条
        int floor = Math.max(1, findKeepStartIndex(current, budget));
        while (estimateTokens(current) > threshold && current.size() > floor) {
            // 每轮按 tool pair 计算安全删除长度，不能只删除 assistant tool_use 而留下孤立 tool_result。
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
        return sanitizeToolProtocol(current);
    }

    private List<Message> dropOldestPreservingHandoff(List<Message> messages, int threshold, int handoffIdx) {
        List<Message> current = new ArrayList<>(messages);
        int handoff = Math.max(0, Math.min(handoffIdx, current.size() - 1));
        while (estimateTokens(current) > threshold && handoff > 0) {
            int cut = nextSafeDropCount(current.subList(0, handoff));
            if (cut <= 0) {
                break;
            }
            cut = Math.min(cut, handoff);
            current = new ArrayList<>(current.subList(cut, current.size()));
            handoff -= cut;
        }
        while (estimateTokens(current) > threshold && current.size() > handoff + 1) {
            int from = handoff + 1;
            List<Message> suffix = current.subList(from, current.size());
            int cut = nextSafeDropCount(suffix);
            if (cut <= 0 || cut >= suffix.size()) {
                cut = Math.max(1, expandForwardToCloseToolPairs(suffix, 1));
                cut = Math.min(cut, suffix.size());
            }
            if (cut <= 0) {
                break;
            }
            List<Message> next = new ArrayList<>(current.subList(0, from));
            if (cut < suffix.size()) {
                next.addAll(suffix.subList(cut, suffix.size()));
            }
            current = next;
        }
        return sanitizeToolProtocol(current);
    }

    /**
     * 计算应保留的 recent tail 起始下标（含）。
     * 计算应保留的消息起始位置：满足 minTokens + minTextMsgs，不超过 maxTokens。
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
            // 从尾部向前累计，先满足最小 token 和文本消息条件，再在最大预算内尽量多保留上下文。
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

    public int effectiveProtectFirstN(List<Message> messages, CompactionBudget budget) {
        if (findFirstHandoff(messages == null ? List.of() : messages) >= 0) return 0;
        return Math.max(0, budget == null ? 0 : budget.getProtectFirstN());
    }

    public int protectHeadSize(List<Message> messages, CompactionBudget budget) {
        int wanted = effectiveProtectFirstN(messages, budget);
        if (wanted <= 0 || messages == null || messages.isEmpty()) {
            return 0;
        }
        int seen = 0;
        for (int i = 0; i < messages.size() && seen < wanted; i++) {
            Message message = messages.get(i);
            if (message != null && message.getRole() != RoleType.SYSTEM) seen++;
            if (seen == wanted) return i + 1;
        }
        return messages.size();
    }

    public int findTailCutByTokens(List<Message> messages, CompactionBudget budget) {
        return findTailCutByTokens(messages, budget, 0);
    }

    /**
     * 从尾部按 token 预算回切。protectLastN 是「至少留最后 N 条」（封顶 8），不是从 0 起的下标帽。
     * 始终保证 tailStart 大于 headEnd，给 middle 至少留 1 条。
     */
    public int findTailCutByTokens(List<Message> messages, CompactionBudget budget, int headEnd) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int n = messages.size();
        int head = Math.max(0, Math.min(headEnd, n));
        if (n <= head + 1) {
            return n;
        }
        int target = (int) Math.ceil(Math.max(0.0d, budget.getSummaryTargetRatio()) * budget.threshold());
        int ceiling = Math.max(target, (int) Math.ceil(target * 1.5d));
        int availableTail = Math.max(0, n - head - 1);
        int minTailFloor = Math.min(Math.max(0, budget.getProtectLastN()), MAX_TAIL_MESSAGE_FLOOR);
        int minTail = Math.min(minTailFloor, availableTail);

        int cut = n;
        int tokens = 0;
        for (int i = n - 1; i >= head; i--) {
            int add = tokenCounter.estimateOneMessage(messages.get(i));
            if (tokens + add > ceiling && (n - i) >= Math.max(minTail, 1)) {
                break;
            }
            tokens += add;
            cut = i;
        }
        if (cut <= head && tokens <= ceiling && tokens > 0) {
            int raw = 0;
            cut = n;
            for (int i = n - 1; i >= head; i--) {
                int add = tokenCounter.estimateOneMessage(messages.get(i));
                if (raw + add > target && (n - i) >= Math.max(minTail, 1)) {
                    break;
                }
                raw += add;
                cut = i;
            }
        }
        int fallbackCut = n - minTail;
        cut = Math.min(cut, fallbackCut);
        if (cut <= head) {
            cut = Math.max(fallbackCut, head + 1);
        }
        return Math.min(n, Math.max(cut, head + 1));
    }

    public CompactionWindow splitForFullCompact(List<Message> messages, CompactionBudget budget) {
        if (messages == null || messages.isEmpty()) {
            return new CompactionWindow(0, 0, null);
        }
        int handoff = findFirstHandoff(messages);
        String previous = handoff < 0 ? null : CompactionPrompt.unwrapHandoffBody(messages.get(handoff).getContent());
        List<Message> body = new ArrayList<>(messages);
        if (handoff >= 0) {
            body.remove(handoff);
        }
        int headWanted = handoff >= 0 ? 0 : protectHeadSize(body, budget);
        int headEnd = Math.min(headWanted, Math.max(0, body.size() - 1));
        headEnd = adjustHeadEndToPreserveToolPairs(body, headEnd, body.size());
        int tailStart = findTailCutByTokens(body, budget, headEnd);
        tailStart = ensureLastUserMessageInTail(body, tailStart, headEnd);
        tailStart = ensureLastAssistantMessageInTail(body, tailStart, headEnd);
        tailStart = alignBoundaryForward(body, Math.max(tailStart, headEnd + 1));
        if (tailStart < headEnd) {
            tailStart = alignBoundaryForward(body, Math.min(body.size(), headEnd + 1));
        }
        return new CompactionWindow(headEnd, tailStart, previous);
    }

    /**
     * 切点落在连续 tool_result 上时向前推到组外，避免 assistant 在 middle、result 在 tail。
     * 后缀全是 TOOL 时返回 n，整组进 middle。
     */
    public int alignBoundaryForward(List<Message> messages, int idx) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int n = messages.size();
        int i = Math.max(0, Math.min(idx, n));
        while (i < n && isToolAt(messages, i)) {
            i++;
        }
        return i;
    }

    private boolean isToolAt(List<Message> messages, int idx) {
        if (messages == null || idx < 0 || idx >= messages.size()) {
            return false;
        }
        Message m = messages.get(idx);
        return m != null && m.getRole() == RoleType.TOOL;
    }

    /**
     * 保证 headEnd 不会把 assistant(tool_calls) 留在 head、对应 tool_result 留在 middle。
     * 优先向前扩到闭合（不超过 maxEnd）；仍无法闭合则回退到开对之前。
     */
    public int adjustHeadEndToPreserveToolPairs(List<Message> messages, int headEnd, int maxEnd) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int limit = Math.max(0, Math.min(maxEnd, messages.size()));
        int end = Math.max(0, Math.min(headEnd, limit));
        Set<String> open = openToolCallsInPrefix(messages, end);
        while (end < limit && !open.isEmpty()) {
            Message m = messages.get(end);
            end++;
            applyToolPairDelta(open, m);
        }
        while (end > 0 && !openToolCallsInPrefix(messages, end).isEmpty()) {
            end--;
        }
        return end;
    }

    private Set<String> openToolCallsInPrefix(List<Message> messages, int endExclusive) {
        Set<String> open = new HashSet<>();
        int end = Math.max(0, Math.min(endExclusive, messages.size()));
        for (int i = 0; i < end; i++) {
            applyToolPairDelta(open, messages.get(i));
        }
        return open;
    }

    private void applyToolPairDelta(Set<String> open, Message m) {
        if (m == null || open == null) {
            return;
        }
        if (m.getRole() == RoleType.ASSISTANT && m.getToolCalls() != null) {
            for (ToolCall tc : m.getToolCalls()) {
                if (tc != null && StringUtils.isNotBlank(tc.getId())) {
                    open.add(tc.getId());
                }
            }
        }
        if (m.getRole() == RoleType.TOOL && StringUtils.isNotBlank(m.getToolCallId())) {
            open.remove(m.getToolCallId());
        }
    }

    public int ensureLastUserMessageInTail(List<Message> messages, int tailStart, CompactionBudget budget) {
        return ensureLastUserMessageInTail(messages, tailStart, 0);
    }

    public int ensureLastUserMessageInTail(List<Message> messages, int tailStart, int headEnd) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int n = messages.size();
        int cut = Math.max(0, Math.min(tailStart, n));
        int head = Math.max(0, Math.min(headEnd, n));
        int lastUser = -1;
        for (int i = n - 1; i >= head; i--) {
            Message m = messages.get(i);
            if (m != null && m.getRole() == RoleType.USER && StringUtils.isNotBlank(m.getContent()) && !isCompactSummaryMessage(m)) {
                lastUser = i;
                break;
            }
        }
        if (lastUser < 0 || lastUser >= cut) {
            return cut;
        }
        int adjusted = Math.max(lastUser, head + 1);
        if (adjusted > lastUser) {
            return Math.max(findTurnPairEnd(messages, lastUser), head + 1);
        }
        return adjusted;
    }

    public int ensureLastAssistantMessageInTail(List<Message> messages, int tailStart, CompactionBudget budget) {
        return ensureLastAssistantMessageInTail(messages, tailStart, 0);
    }

    public int ensureLastAssistantMessageInTail(List<Message> messages, int tailStart, int headEnd) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int n = messages.size();
        int cut = Math.max(0, Math.min(tailStart, n));
        int head = Math.max(0, Math.min(headEnd, n));
        int lastAsst = -1;
        for (int i = n - 1; i >= head; i--) {
            if (isVisibleAssistant(messages.get(i))) {
                lastAsst = i;
                break;
            }
        }
        if (lastAsst < 0 || lastAsst >= cut) {
            return cut;
        }
        int aligned = adjustIndexToPreserveToolPairs(messages, lastAsst);
        return Math.max(aligned, head + 1);
    }

    int findTurnPairEnd(List<Message> messages, int userIdx) {
        int n = messages.size();
        int idx = userIdx + 1;
        if (idx >= n) {
            return idx;
        }
        Message next = messages.get(idx);
        if (next == null || next.getRole() != RoleType.ASSISTANT) {
            return idx;
        }
        idx++;
        while (idx < n && isToolAt(messages, idx)) {
            idx++;
        }
        return idx;
    }

    private boolean isVisibleAssistant(Message m) {
        return m != null
                && m.getRole() == RoleType.ASSISTANT
                && StringUtils.isNotBlank(m.getContent())
                && !isCompactSummaryMessage(m)
                && (m.getToolCalls() == null || m.getToolCalls().isEmpty());
    }

    public String serializeMiddleForSummarizer(List<Message> middle, CompactionBudget budget) {
        if (middle == null || middle.isEmpty()) return "";
        StringBuilder all = new StringBuilder();
        int max = Math.max(1, budget.getContentMaxChars());
        for (Message m : middle) {
            if (m == null || m.getRole() == RoleType.SYSTEM) continue;
            String label = m.getRole() == RoleType.TOOL ? "[TOOL RESULT " + StringUtils.defaultString(m.getToolCallId()) + "]: " : "[" + m.getRole().name() + "]: ";
            String text = StringUtils.defaultString(m.getContent());
            if (StringUtils.isNotBlank(m.getBase64Image())) text += " [image]";
            if (m.getRole() == RoleType.ASSISTANT && m.getToolCalls() != null) {
                for (ToolCall call : m.getToolCalls()) {
                    if (call == null) continue;
                    String args = call.getFunction() == null ? "" : StringUtils.defaultString(call.getFunction().getArguments());
                    int argLimit = Math.max(64, max / 2);
                    text += " [tool " + StringUtils.defaultString(call.getFunction() == null ? null : call.getFunction().getName())
                            + " args=" + (args.length() > argLimit ? args.substring(0, argLimit) + "...[truncated]" : args) + "]";
                }
            }
            if (text.length() > max) {
                int head = Math.min(Math.max(0, budget.getContentHeadChars()), text.length());
                int tail = Math.min(Math.max(0, budget.getContentTailChars()), Math.max(0, text.length() - head));
                text = text.substring(0, head) + "\n...[truncated]...\n" + text.substring(text.length() - tail);
                if (text.length() > max) {
                    text = text.substring(0, max);
                }
            }
            all.append(label).append(text).append('\n');
        }
        int limit = Math.max(1, budget.getSummaryInputMaxChars());
        if (all.length() <= limit) return all.toString().trim();
        int tail = Math.min(limit / 3, all.length());
        return all.substring(0, limit - tail) + "\n...[middle input omitted]...\n" + all.substring(all.length() - tail);
    }

    public String buildStaticFallbackSummary(List<Message> middle, String previousSummary, String latestUser) {
        StringBuilder out = new StringBuilder();
        out.append("## Historical Task Snapshot\n").append(StringUtils.defaultString(latestUser, "Earlier conversation context")).append('\n');
        out.append("## Goal\n").append(StringUtils.defaultString(previousSummary, "Continue the active task.")).append('\n');
        out.append("## Constraints & Preferences\nPreserve user requirements and existing behavior.\n## Completed Actions\n");
        out.append(serializeMiddleForSummarizer(middle, CompactionBudget.defaults())).append("\n## Active State\nContinue from the latest preserved messages.\n");
        out.append("## Blocked\nNone recorded.\n## Key Decisions\nUse the available conversation facts.\n## Resolved Questions\nNone recorded.\n## Relevant Files\nSee checkpoint content.\n## Critical Context\nEarlier turns were compacted.\n");
        return out.toString().trim();
    }

    public RoleType chooseHandoffRole(List<Message> head, List<Message> tail) {
        RoleType lastHead = visibleRole(head, true);
        RoleType firstTail = visibleRole(tail, false);
        boolean survivingUser = hasSurvivingUser(head) || hasSurvivingUser(tail);

        // 无存活 user → 强制 USER；若与 firstTail 冲突则走 merge
        if (!survivingUser) {
            return firstTail == RoleType.USER ? null : RoleType.USER;
        }
        // head 无可见角色 → 强制 leading USER
        if (lastHead == null) {
            return firstTail == RoleType.USER ? null : RoleType.USER;
        }
        // 优先与 head 交替
        RoleType preferred = lastHead == RoleType.USER ? RoleType.ASSISTANT : RoleType.USER;
        if (firstTail != null && preferred == firstTail) {
            return null;
        }
        return preferred;
    }

    public List<Message> mergeHandoffIntoTail(List<Message> tail, String wrappedSummary) {
        List<Message> result = new ArrayList<>(tail == null ? List.of() : tail);
        String summaryBlock = normalizeSummaryBlock(wrappedSummary);
        for (int i = 0; i < result.size(); i++) {
            Message m = result.get(i);
            if (m != null && m.getRole() != RoleType.TOOL) {
                String original = StringUtils.defaultString(m.getContent());
                String merged = CompactionPrompt.MERGE_PRIOR + "\n"
                        + original + "\n\n"
                        + CompactionPrompt.MERGE_DELIMITER + "\n"
                        + summaryBlock;
                result.set(i, Message.builder()
                        .role(m.getRole())
                        .originMessageKey(m.getOriginMessageKey())
                        .content(merged)
                        .toolCallId(m.getToolCallId())
                        .toolCalls(m.getToolCalls())
                        .build());
                return List.copyOf(result);
            }
        }
        // 空 tail / 纯 TOOL：强制插入 USER handoff，避免 checkpoint 丢失
        List<Message> forced = new ArrayList<>();
        forced.add(Message.userMessage(summaryBlock, null));
        forced.addAll(result);
        return List.copyOf(forced);
    }

    public List<Message> buildPostCompactMessages(List<Message> head, String handoffContent, RoleType handoffRole, List<Message> tail) {
        RoleType role = handoffRole == null ? chooseHandoffRole(head, tail) : handoffRole;
        List<Message> result = new ArrayList<>(head == null ? List.of() : head);
        if (role == null) {
            result.addAll(mergeHandoffIntoTail(tail, handoffContent));
            return sanitizeToolProtocol(result);
        }
        result.add(Message.builder().role(role).content(handoffContent).build());
        if (tail != null) {
            result.addAll(tail);
        }
        return sanitizeToolProtocol(result);
    }

    private String normalizeSummaryBlock(String wrappedSummary) {
        String summaryBlock = StringUtils.defaultString(wrappedSummary).trim();
        if (!summaryBlock.contains(CompactionPrompt.CONTEXT_COMPACTION_PREFIX)) {
            summaryBlock = CompactionPrompt.wrapSummaryForReinject(
                    CompactionPrompt.unwrapHandoffBody(summaryBlock), false);
        }
        return summaryBlock;
    }

    private RoleType visibleRole(List<Message> messages, boolean fromEnd) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int n = 0; n < messages.size(); n++) {
            Message m = messages.get(fromEnd ? messages.size() - n - 1 : n);
            if (m == null || m.getRole() == null || m.getRole() == RoleType.TOOL || m.getRole() == RoleType.SYSTEM) {
                continue;
            }
            if (isCompactSummaryMessage(m)) {
                continue;
            }
            if (m.getRole() == RoleType.USER) {
                if (StringUtils.isNotBlank(m.getContent())) {
                    return RoleType.USER;
                }
                continue;
            }
            if (m.getRole() == RoleType.ASSISTANT) {
                boolean hasTools = m.getToolCalls() != null && !m.getToolCalls().isEmpty();
                boolean hasText = StringUtils.isNotBlank(m.getContent());
                if (hasTools && !hasText) {
                    continue;
                }
                if (hasText || hasTools) {
                    return RoleType.ASSISTANT;
                }
            }
        }
        return null;
    }

    private boolean hasSurvivingUser(List<Message> messages) {
        if (messages == null) {
            return false;
        }
        for (Message m : messages) {
            if (m != null
                    && m.getRole() == RoleType.USER
                    && StringUtils.isNotBlank(m.getContent())
                    && !isCompactSummaryMessage(m)) {
                return true;
            }
        }
        return false;
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
        return sanitizeToolProtocol(result);
    }

    /**
     * 压缩请求本身 prompt-too-long 时：从待摘要前缀按 tool-safe 边界丢掉最旧一段，再重试摘要。
     * 返回 null 表示已无法再裁。
     */
    public List<Message> truncateHeadForCompactRetry(List<Message> messages) {
        if (messages == null || messages.size() <= 1) {
            return null;
        }
        int cut = nextSafeDropCount(messages);
        if (cut <= 0) {
            cut = 1;
        }
        if (cut >= messages.size()) {
            cut = messages.size() - 1;
        }
        if (cut <= 0) {
            return null;
        }
        return List.copyOf(messages.subList(cut, messages.size()));
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
                    .originMessageKey(message.getOriginMessageKey())
                    .content(content)
                    .toolCallId(message.getToolCallId())
                    .toolCalls(message.getToolCalls())
                    .build());
        }
        return prepared;
    }

    /**
     * 压缩后协议清洗（对齐 Hermes _sanitize_tool_pairs）：
     * 孤立 tool_result 直接丢弃；没有 result 的 assistant tool_call 从列表里剥掉。
     */
    public List<Message> sanitizeToolProtocol(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : List.copyOf(messages);
        }
        Set<String> survivingCallIds = new HashSet<>();
        Set<String> resultCallIds = new HashSet<>();
        for (Message m : messages) {
            if (m == null) {
                continue;
            }
            if (m.getRole() == RoleType.ASSISTANT && m.getToolCalls() != null) {
                for (ToolCall tc : m.getToolCalls()) {
                    if (tc != null && StringUtils.isNotBlank(tc.getId())) {
                        survivingCallIds.add(tc.getId());
                    }
                }
            }
            if (m.getRole() == RoleType.TOOL && StringUtils.isNotBlank(m.getToolCallId())) {
                resultCallIds.add(m.getToolCallId());
            }
        }
        Set<String> orphanedResults = new HashSet<>(resultCallIds);
        orphanedResults.removeAll(survivingCallIds);
        Set<String> missingResults = new HashSet<>(survivingCallIds);
        missingResults.removeAll(resultCallIds);
        if (orphanedResults.isEmpty() && missingResults.isEmpty()) {
            return List.copyOf(messages);
        }
        List<Message> result = new ArrayList<>(messages.size());
        for (Message m : messages) {
            if (m == null) {
                continue;
            }
            if (m.getRole() == RoleType.TOOL && orphanedResults.contains(m.getToolCallId())) {
                continue;
            }
            if (m.getRole() == RoleType.ASSISTANT && m.getToolCalls() != null && !missingResults.isEmpty()) {
                List<ToolCall> kept = new ArrayList<>();
                for (ToolCall tc : m.getToolCalls()) {
                    if (tc == null || StringUtils.isBlank(tc.getId()) || !missingResults.contains(tc.getId())) {
                        if (tc != null) {
                            kept.add(tc);
                        }
                    }
                }
                if (kept.size() != m.getToolCalls().size()) {
                    Message.MessageBuilder builder = Message.builder()
                            .role(RoleType.ASSISTANT)
                            .originMessageKey(m.getOriginMessageKey())
                            .content(m.getContent())
                            .reasoningContent(m.getReasoningContent())
                            .base64Image(m.getBase64Image());
                    if (!kept.isEmpty()) {
                        builder.toolCalls(kept);
                    } else if (StringUtils.isBlank(m.getContent())) {
                        builder.content("(tool call removed)");
                    }
                    result.add(builder.build());
                    continue;
                }
            }
            result.add(m);
        }
        return List.copyOf(result);
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

        // keep 区间可能从 tool_result 开始；先收集区间中引用的 call，再向前寻找对应 assistant tool_use。
        // 这里调整的是切片边界，不修改消息内容，因此压缩后仍能通过模型协议校验。
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
            // keep 区间如果从 tool_result 开始，就向前扩展到对应 assistant tool_use。
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
        // 计算前缀中尚未闭合的 tool_call，只有等对应 tool_result 到达后才能安全删除该前缀。
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

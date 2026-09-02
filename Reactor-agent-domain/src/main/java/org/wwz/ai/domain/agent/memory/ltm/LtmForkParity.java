package org.wwz.ai.domain.agent.memory.ltm;

import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Hermes 式 LTM fork 前缀对齐快照：system + tools[] 与父回合字节级一致，
 * 仅尾部 user directive 为新内容；执行层用 whitelist 限制真实可调用工具。
 */
public final class LtmForkParity {

    public static final Set<String> MEMORY_ONLY_WHITELIST = Set.of("memory");

    /**
     * Background review 策展候选工具（再与父会话实际 tools 取交集）。
     * skill 创作路径：workspace_* / skill_tool / bash（Skill Creator）。
     */
    public static final Set<String> CURATOR_CANDIDATE_TOOLS = Set.of(
            "memory",
            "workspace_read",
            "workspace_write",
            "workspace_edit",
            "workspace_list",
            "workspace_glob",
            "workspace_grep",
            "skill_tool",
            "bash"
    );

    private final String frozenSystemPrompt;
    private final ToolCollection parentTools;
    private final List<Message> conversationSnapshot;
    private final Set<String> dispatchWhitelist;

    private LtmForkParity(String frozenSystemPrompt,
                          ToolCollection parentTools,
                          List<Message> conversationSnapshot,
                          Set<String> dispatchWhitelist) {
        this.frozenSystemPrompt = frozenSystemPrompt;
        this.parentTools = parentTools;
        this.conversationSnapshot = conversationSnapshot == null
                ? List.of()
                : List.copyOf(new ArrayList<>(conversationSnapshot));
        this.dispatchWhitelist = dispatchWhitelist == null || dispatchWhitelist.isEmpty()
                ? MEMORY_ONLY_WHITELIST
                : Set.copyOf(dispatchWhitelist);
    }

    /** @deprecated 默认请用 {@link #forReview} / {@link #forFlush} */
    @Deprecated
    public static LtmForkParity of(String frozenSystemPrompt,
                                   ToolCollection parentTools,
                                   List<Message> conversationSnapshot) {
        return forFlush(frozenSystemPrompt, parentTools, conversationSnapshot);
    }

    /** 压缩前 flush：仅 memory。 */
    public static LtmForkParity forFlush(String frozenSystemPrompt,
                                         ToolCollection parentTools,
                                         List<Message> conversationSnapshot) {
        return new LtmForkParity(
                frozenSystemPrompt,
                parentTools,
                conversationSnapshot,
                MEMORY_ONLY_WHITELIST);
    }

    /** Background review：策展白名单 ∩ 父工具。 */
    public static LtmForkParity forReview(String frozenSystemPrompt,
                                          ToolCollection parentTools,
                                          List<Message> conversationSnapshot) {
        return new LtmForkParity(
                frozenSystemPrompt,
                parentTools,
                conversationSnapshot,
                resolveCuratorWhitelist(parentTools));
    }

    /**
     * 固定策展候选 ∩ 父会话实际工具名；保证至少含 memory（fork 侧会确保 memory 可执行）。
     */
    public static Set<String> resolveCuratorWhitelist(ToolCollection parentTools) {
        Set<String> out = new LinkedHashSet<>();
        out.add("memory");
        if (parentTools == null || parentTools.getToolMap() == null || parentTools.getToolMap().isEmpty()) {
            return Set.copyOf(out);
        }
        for (String name : parentTools.getToolMap().keySet()) {
            if (name != null && CURATOR_CANDIDATE_TOOLS.contains(name)) {
                out.add(name);
            }
        }
        return Set.copyOf(out);
    }

    public String getFrozenSystemPrompt() {
        return frozenSystemPrompt;
    }

    public ToolCollection getParentTools() {
        return parentTools;
    }

    public List<Message> getConversationSnapshot() {
        return conversationSnapshot;
    }

    public Set<String> getDispatchWhitelist() {
        return dispatchWhitelist;
    }

    public boolean hasSystemPrompt() {
        return frozenSystemPrompt != null && !frozenSystemPrompt.isBlank();
    }

    public boolean hasParentTools() {
        return parentTools != null
                && ((parentTools.getToolMap() != null && !parentTools.getToolMap().isEmpty())
                || (parentTools.getMcpToolMap() != null && !parentTools.getMcpToolMap().isEmpty()));
    }
}

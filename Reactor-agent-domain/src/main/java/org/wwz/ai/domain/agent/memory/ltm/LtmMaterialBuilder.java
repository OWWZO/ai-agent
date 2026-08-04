package org.wwz.ai.domain.agent.memory.ltm;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;

import java.util.ArrayList;
import java.util.List;

/**
 * 为 flush / review 构造截断后的对话材料。
 */
public final class LtmMaterialBuilder {

    private LtmMaterialBuilder() {
    }

    public static String buildFromMessages(List<Message> messages, int maxMessages, int maxCharsPerMsg) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        int maxMsg = Math.max(1, maxMessages);
        int maxChars = Math.max(100, maxCharsPerMsg);
        int from = Math.max(0, messages.size() - maxMsg);
        List<String> lines = new ArrayList<>();
        for (int i = from; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (m == null || StringUtils.isBlank(m.getContent())) {
                continue;
            }
            if (m.getContent().startsWith(MemoryFlushPolicy.FLUSH_NOTE_PREFIX)
                    || m.getContent().startsWith("[memory-pre-compress]")
                    || m.getContent().startsWith("[memory-flush]")) {
                continue;
            }
            RoleType role = m.getRole();
            if (role != RoleType.USER && role != RoleType.ASSISTANT) {
                continue;
            }
            String content = m.getContent().replace('\n', ' ').trim();
            if (content.length() > maxChars) {
                content = content.substring(0, maxChars) + "...";
            }
            lines.add((role == RoleType.USER ? "User: " : "Assistant: ") + content);
        }
        return String.join("\n", lines);
    }

    public static String buildFromTurnPair(String userQuery, String assistantSummary, int maxChars) {
        int max = Math.max(100, maxChars);
        String u = StringUtils.defaultString(userQuery).replace('\n', ' ').trim();
        String a = StringUtils.defaultString(assistantSummary).replace('\n', ' ').trim();
        if (u.length() > max) {
            u = u.substring(0, max) + "...";
        }
        if (a.length() > max) {
            a = a.substring(0, max) + "...";
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(u)) {
            sb.append("User: ").append(u).append('\n');
        }
        if (StringUtils.isNotBlank(a)) {
            sb.append("Assistant: ").append(a);
        }
        return sb.toString().trim();
    }
}

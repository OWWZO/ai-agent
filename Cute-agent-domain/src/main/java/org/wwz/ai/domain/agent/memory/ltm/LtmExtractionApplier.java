package org.wwz.ai.domain.agent.memory.ltm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 LLM 返回的 JSON 操作应用到 CuratedMemoryStore。
 */
public final class LtmExtractionApplier {

    private LtmExtractionApplier() {
    }

    public static List<LtmExtractionOp> parseOps(String raw) {
        if (StringUtils.isBlank(raw)) {
            return List.of();
        }
        String text = raw.trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        try {
            JSONArray arr = JSON.parseArray(text);
            if (arr == null || arr.isEmpty()) {
                return List.of();
            }
            List<LtmExtractionOp> ops = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o == null) {
                    continue;
                }
                ops.add(LtmExtractionOp.builder()
                        .action(StringUtils.defaultString(o.getString("action"), "add").trim().toLowerCase())
                        .target(StringUtils.defaultString(o.getString("target"), "curated").trim().toLowerCase())
                        .content(StringUtils.trimToEmpty(o.getString("content")))
                        .oldText(StringUtils.trimToEmpty(o.getString("old_text")))
                        .build());
            }
            return ops;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static int apply(CuratedMemoryStore store,
                            LtmOwner owner,
                            List<LtmExtractionOp> ops,
                            String sessionId,
                            String requestId,
                            String writeOrigin) {
        if (store == null || owner == null || ops == null || ops.isEmpty()) {
            return 0;
        }
        int applied = 0;
        for (LtmExtractionOp op : ops) {
            if (op == null || StringUtils.isBlank(op.getAction())) {
                continue;
            }
            CuratedMemoryScope scope;
            try {
                scope = CuratedMemoryScope.fromCode(op.getTarget());
            } catch (Exception e) {
                scope = CuratedMemoryScope.CURATED;
            }
            CuratedMemoryWriteResult result = switch (op.getAction()) {
                case "add" -> store.add(owner, scope, op.getContent(), sessionId, requestId, writeOrigin);
                case "replace" -> store.replace(owner, scope, op.getOldText(), op.getContent(),
                        sessionId, requestId, writeOrigin);
                case "remove" -> store.remove(owner, scope, op.getOldText(), sessionId, requestId, writeOrigin);
                default -> null;
            };
            if (result != null && result.isSuccess() && !result.isNoChange()) {
                applied++;
            }
        }
        return applied;
    }

    public static String flushSystemPrompt() {
        return """
                You are a memory flush assistant. The conversation window is about to be compacted.
                Extract ONLY durable facts worth long-term memory:
                - user preferences / identity / communication style → target=user
                - stable environment / project conventions → target=curated
                Do NOT store: one-off tasks, full procedures, tool noise, unresolved failures.
                Return ONLY a JSON array, no markdown:
                [{"action":"add","target":"user|curated","content":"..."}]
                If nothing durable, return [].
                Max 5 items. Each content under 200 chars.
                """;
    }

    public static String reviewSystemPrompt() {
        return """
                You are a background memory curator reviewing recent dialogue.
                Extract ONLY durable declarative memory:
                - preferences, identity, style → target=user
                - stable environment/project facts → target=curated
                Do NOT store full how-to procedures (those belong to skills, not memory).
                Do NOT store one-off tasks or failure noise.
                Return ONLY a JSON array:
                [{"action":"add","target":"user|curated","content":"..."}]
                If nothing new, return []. Max 5 items, each under 200 chars.
                """;
    }
}

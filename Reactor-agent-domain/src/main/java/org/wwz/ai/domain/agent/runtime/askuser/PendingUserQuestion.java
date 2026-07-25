package org.wwz.ai.domain.agent.runtime.askuser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 挂起中的 AskUserQuestion（对标 cc-haha permission queue 项）。
 * 工具线程 await future；Web 通过独立 HTTP 提交答案完成 future。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingUserQuestion {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ANSWERED = "answered";
    public static final String STATUS_TIMEOUT = "timeout";
    public static final String STATUS_CANCELLED = "cancelled";

    private String questionId;
    private String sessionId;
    private String requestId;
    private String toolCallId;
    private List<Map<String, Object>> questions;
    private long createdAtMs;
    private long timeoutMs;
    @Builder.Default
    private String status = STATUS_PENDING;
    /** question text → answer text */
    private Map<String, String> answers;
    @Builder.Default
    private transient CompletableFuture<Map<String, String>> future = new CompletableFuture<>();

    public Map<String, Object> toClientPayload() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("messageType", "ask_user_question");
        map.put("questionId", questionId);
        map.put("sessionId", sessionId);
        map.put("requestId", requestId);
        map.put("toolCallId", toolCallId);
        map.put("status", status);
        map.put("questions", questions);
        map.put("timeoutMs", timeoutMs);
        map.put("createdAtMs", createdAtMs);
        return map;
    }
}

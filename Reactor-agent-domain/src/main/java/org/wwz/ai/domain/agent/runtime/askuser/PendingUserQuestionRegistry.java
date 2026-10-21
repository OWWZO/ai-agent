package org.wwz.ai.domain.agent.runtime.askuser;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 全局挂起问询注册表（进程内）。
 * @deprecated AskUserQuestion 已改为 DB continuation；仅保留兼容钩子，新逻辑见 {@link IUserQuestionRepository}。
 */
@Deprecated
@Slf4j
@Component
public class PendingUserQuestionRegistry {

    /** 默认最长等待 30 分钟（Web 用户可能很久才回） */
    public static final long DEFAULT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);

    private final Map<String, PendingUserQuestion> pending = new ConcurrentHashMap<>();

    public PendingUserQuestion create(String sessionId,
                                      String requestId,
                                      String toolCallId,
                                      List<Map<String, Object>> questions,
                                      Long timeoutMs) {
        String questionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long timeout = timeoutMs != null && timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        PendingUserQuestion item = PendingUserQuestion.builder()
                .questionId(questionId)
                .sessionId(sessionId)
                .requestId(requestId)
                .toolCallId(toolCallId)
                .questions(questions)
                .createdAtMs(System.currentTimeMillis())
                .timeoutMs(timeout)
                .status(PendingUserQuestion.STATUS_PENDING)
                .future(new CompletableFuture<>())
                .build();
        pending.put(questionId, item);
        return item;
    }

    public Optional<PendingUserQuestion> get(String questionId) {
        if (StringUtils.isBlank(questionId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(pending.get(questionId.trim()));
    }

    public List<PendingUserQuestion> listBySession(String sessionId) {
        List<PendingUserQuestion> result = new ArrayList<>();
        if (StringUtils.isBlank(sessionId)) {
            return result;
        }
        for (PendingUserQuestion item : pending.values()) {
            if (sessionId.equals(item.getSessionId())
                    && PendingUserQuestion.STATUS_PENDING.equals(item.getStatus())) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 用户提交答案。返回 false 表示不存在或已结束。
     */
    public boolean answer(String questionId, Map<String, String> answers) {
        String key = StringUtils.trimToEmpty(questionId);
        PendingUserQuestion item = pending.get(key);
        if (item == null) {
            return false;
        }
        synchronized (item) {
            if (!PendingUserQuestion.STATUS_PENDING.equals(item.getStatus())) {
                return false;
            }
            item.setAnswers(answers == null ? Map.of() : answers);
            item.setStatus(PendingUserQuestion.STATUS_ANSWERED);
            item.getFuture().complete(item.getAnswers());
        }
        // 终态即驱逐：awaitAnswers 删除后，这里是 pending 唯一的清理点。
        pending.remove(key, item);
        return true;
    }

    public boolean cancel(String questionId, String reason) {
        String key = StringUtils.trimToEmpty(questionId);
        PendingUserQuestion item = pending.get(key);
        if (item == null) {
            return false;
        }
        synchronized (item) {
            if (!PendingUserQuestion.STATUS_PENDING.equals(item.getStatus())) {
                return false;
            }
            item.setStatus(PendingUserQuestion.STATUS_CANCELLED);
            item.getFuture().completeExceptionally(
                    new IllegalStateException(StringUtils.defaultIfBlank(reason, "cancelled")));
        }
        // 终态即驱逐：避免已结束的问询长期滞留在进程级 map 里。
        pending.remove(key, item);
        return true;
    }

    public void cancelByRequestId(String requestId, String reason) {
        if (StringUtils.isBlank(requestId)) {
            return;
        }
        for (PendingUserQuestion item : pending.values()) {
            if (requestId.equals(item.getRequestId())
                    && PendingUserQuestion.STATUS_PENDING.equals(item.getStatus())) {
                cancel(item.getQuestionId(), reason);
            }
        }
    }
}

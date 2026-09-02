package org.wwz.ai.infrastructure.memory;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.memory.WorkingMemorySearchMessage;
import org.wwz.ai.domain.agent.memory.ltm.SessionSearchRequest;
import org.wwz.ai.domain.agent.memory.ltm.SessionSearchService;
import org.wwz.ai.infrastructure.dao.reactor.IWorkingMemoryMessageDao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hermes 风格的消息级历史浏览器。搜索只看 main working-memory 投影；READY 与
 * INVALID 都是历史，只有 preload 仍然只读取 READY。
 * <p>
 * 出站统一经过角色过滤与字段白名单：默认隐藏 TOOL，Discovery 只返回命中索引。
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class WorkingMemorySessionSearchService implements SessionSearchService {

    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 20;
    private static final int DEFAULT_WINDOW = 5;
    private static final int MAX_WINDOW = 20;
    private static final int MAX_CONTENT_CHARS = 4000;
    /** working_memory_turn.status INVALID = compacted-away; still searchable, not in live context. */
    private static final int TURN_STATUS_INVALID = 2;

    private final IWorkingMemoryMessageDao workingMemoryMessageDao;

    @Override
    public String search(SessionSearchRequest request) {
        if (request == null) {
            return browse(null, null, DEFAULT_LIMIT);
        }
        if (request.getLimit() != null && request.getLimit() <= 0) {
            return error("limit must be >= 1");
        }
        String sessionId = trim(request.getSessionId());
        Long anchor = request.getAroundMessageId();
        String query = trim(request.getQuery());
        String visitorId = trim(request.getVisitorId());
        if (anchor != null && sessionId == null) {
            return error("around_message_id requires session_id");
        }
        if (query != null && sessionId != null && anchor != null) {
            return error("query cannot be combined with session_id and around_message_id");
        }

        int limit = resolveLimit(request.getLimit());
        List<String> roles = resolveRoles(request.getRoleFilter());
        if (sessionId != null && anchor != null) {
            if (request.getWindow() != null && request.getWindow() <= 0) {
                return error("window must be >= 1");
            }
            return scroll(sessionId, visitorId, anchor, resolveWindow(request.getWindow()), roles);
        }
        if (query != null && sessionId != null) {
            return discover(sessionId, request.getCurrentSessionId(), visitorId, query, limit, "session", roles);
        }
        if (sessionId != null) {
            return read(sessionId, visitorId, roles);
        }
        if (query == null) {
            return browse(visitorId, request.getCurrentSessionId(), limit);
        }
        return discover(null, request.getCurrentSessionId(), visitorId, query, limit, request.getScope(), roles);
    }

    /** Legacy callers remain supported; the tool uses the request overload above. */
    @Override
    public String search(String sessionId, String visitorId, String query, int limit, String scope) {
        return search(SessionSearchRequest.builder()
                .currentSessionId(sessionId)
                .visitorId(visitorId)
                .query(query)
                .limit(limit <= 0 ? null : limit)
                .scope(scope)
                .build());
    }

    private String discover(String requestedSessionId,
                            String currentSessionId,
                            String visitorId,
                            String query,
                            int limit,
                            String scope,
                            List<String> roles) {
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return discoverPayload(query, roles, List.of(), "none");
        }
        String normalizedScope = normalizeScope(scope);
        String sessionScopeId = firstNonBlank(requestedSessionId, currentSessionId);
        if ("session".equals(normalizedScope)) {
            if (sessionScopeId == null) {
                return error("session_id is required for scope=session");
            }
            String denied = requireOwnedSession(sessionScopeId, visitorId);
            if (denied != null) {
                return denied;
            }
        }

        int scanLimit = Math.min(100, Math.max(limit * 4, limit));
        List<WorkingMemorySearchMessage> raw;
        String backend;
        try {
            raw = "session".equals(normalizedScope)
                    ? workingMemoryMessageDao.searchFullTextBySession(sessionScopeId, query, scanLimit, roles)
                    : searchByVisitorOrSession(visitorId, currentSessionId, query, scanLimit, true, roles);
            backend = "fulltext";
        } catch (Exception e) {
            backend = "scan";
            log.warn("working-memory FULLTEXT unavailable, falling back to scan: {}", e.toString());
            try {
                raw = "session".equals(normalizedScope)
                        ? workingMemoryMessageDao.scanBySession(sessionScopeId, query, scanLimit, roles)
                        : searchByVisitorOrSession(visitorId, currentSessionId, query, scanLimit, false, roles);
            } catch (Exception scanError) {
                log.warn("working-memory scan failed: {}", scanError.toString());
                return error("failed to search message history: " + messageOf(scanError));
            }
        }

        List<WorkingMemorySearchMessage> matched = new ArrayList<>();
        for (WorkingMemorySearchMessage hit : safeList(raw)) {
            if (matchesQuery(hit.getContent(), tokens)) {
                matched.add(hit);
            }
        }
        boolean excludeLiveCurrent = !"session".equals(normalizedScope);
        List<WorkingMemorySearchMessage> hits = deduplicate(matched, limit, currentSessionId, excludeLiveCurrent);
        List<Map<String, Object>> results = new ArrayList<>();
        for (WorkingMemorySearchMessage hit : hits) {
            results.add(lightweightResult(hit, query, tokens));
        }
        return discoverPayload(query, roles, results, backend);
    }

    private String discoverPayload(String query,
                                   List<String> roles,
                                   List<Map<String, Object>> results,
                                   String backend) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("mode", "discover");
        payload.put("query", query);
        payload.put("role_filter", String.join(",", roles));
        payload.put("results", results);
        payload.put("count", results.size());
        payload.put("search_backend", backend);
        return JSON.toJSONString(payload);
    }

    private List<WorkingMemorySearchMessage> searchByVisitorOrSession(String visitorId,
                                                                       String sessionId,
                                                                       String query,
                                                                       int limit,
                                                                       boolean fullText,
                                                                       List<String> roles) {
        if (StringUtils.isNotBlank(visitorId)) {
            return fullText
                    ? workingMemoryMessageDao.searchFullTextByVisitor(visitorId.trim(), query, limit, roles)
                    : workingMemoryMessageDao.scanByVisitor(visitorId.trim(), query, limit, roles);
        }
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        return fullText
                ? workingMemoryMessageDao.searchFullTextBySession(sessionId, query, limit, roles)
                : workingMemoryMessageDao.scanBySession(sessionId, query, limit, roles);
    }

    private List<WorkingMemorySearchMessage> deduplicate(List<WorkingMemorySearchMessage> raw,
                                                         int limit,
                                                         String currentSessionId,
                                                         boolean excludeLiveCurrent) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Map<String, WorkingMemorySearchMessage> byOrigin = new LinkedHashMap<>();
        for (WorkingMemorySearchMessage hit : raw) {
            if (hit == null || StringUtils.isBlank(hit.getSessionId())) {
                continue;
            }
            if (excludeLiveCurrent && shouldSkipLiveCurrentSession(hit, currentSessionId)) {
                continue;
            }
            String key = hit.getSessionId() + ":"
                    + StringUtils.defaultIfBlank(hit.getOriginMessageKey(), hit.stableKey());
            WorkingMemorySearchMessage previous = byOrigin.get(key);
            if (previous == null || compareHistoryOrder(hit, previous) < 0) {
                byOrigin.put(key, hit);
            }
        }

        Map<String, WorkingMemorySearchMessage> bySession = new LinkedHashMap<>();
        for (WorkingMemorySearchMessage hit : byOrigin.values()) {
            bySession.putIfAbsent(hit.getSessionId(), hit);
            if (bySession.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(bySession.values());
    }

    /** Skip current-session hits that are still in the live READY window. */
    private static boolean shouldSkipLiveCurrentSession(WorkingMemorySearchMessage hit, String currentSessionId) {
        if (StringUtils.isBlank(currentSessionId) || hit == null) {
            return false;
        }
        if (!currentSessionId.equals(hit.getSessionId())) {
            return false;
        }
        return !isInvalidTurn(hit.getTurnStatus());
    }

    private static boolean isInvalidTurn(Integer turnStatus) {
        return turnStatus != null && turnStatus == TURN_STATUS_INVALID;
    }

    private Map<String, Object> lightweightResult(WorkingMemorySearchMessage hit,
                                                  String query,
                                                  List<String> tokens) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session_id", hit.getSessionId());
        result.put("match_message_id", hit.getId());
        result.put("origin_message_key", hit.getOriginMessageKey());
        result.put("stable_key", hit.stableKey());
        result.put("matched_role", hit.getRole());
        result.put("snippet", snippet(hit.getContent(), query, tokens));
        result.put("matched_terms", List.copyOf(tokens));
        if (hit.getSearchScore() != null) {
            result.put("search_score", hit.getSearchScore());
        }
        addSessionMetadata(result, hit.getSessionId());
        return result;
    }

    private String scroll(String sessionId, String visitorId, long aroundMessageId, int window, List<String> roles) {
        String denied = requireOwnedSession(sessionId, visitorId);
        if (denied != null) {
            return denied;
        }
        List<WorkingMemorySearchMessage> rawHistory;
        try {
            rawHistory = safeList(workingMemoryMessageDao.selectHistoryBySession(sessionId));
        } catch (Exception e) {
            return error("failed to load messages: " + messageOf(e));
        }
        List<WorkingMemorySearchMessage> history = applyViewFilter(rawHistory, roles);
        int anchorIndex = indexOfMessage(history, aroundMessageId);
        if (anchorIndex < 0) {
            int rawIndex = indexOfMessage(rawHistory, aroundMessageId);
            if (rawIndex >= 0) {
                return error("around_message_id " + aroundMessageId
                        + " is hidden by role_filter; pass a role_filter that includes that message");
            }
            return error("around_message_id " + aroundMessageId + " not found in session_id " + sessionId);
        }
        int from = Math.max(0, anchorIndex - window);
        int to = Math.min(history.size(), anchorIndex + window + 1);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("mode", "scroll");
        payload.put("session_id", sessionId);
        payload.put("around_message_id", aroundMessageId);
        payload.put("window", window);
        payload.put("role_filter", String.join(",", roles));
        payload.put("messages", shapeMessages(history.subList(from, to), aroundMessageId));
        payload.put("messages_before", anchorIndex - from);
        payload.put("messages_after", Math.max(0, to - anchorIndex - 1));
        addSessionMetadata(payload, sessionId);
        return JSON.toJSONString(payload);
    }

    private String read(String sessionId, String visitorId, List<String> roles) {
        String denied = requireOwnedSession(sessionId, visitorId);
        if (denied != null) {
            return denied;
        }
        List<WorkingMemorySearchMessage> history;
        try {
            history = applyViewFilter(safeList(workingMemoryMessageDao.selectHistoryBySession(sessionId)), roles);
        } catch (Exception e) {
            return error("failed to load session: " + messageOf(e));
        }
        int head = Math.min(20, history.size());
        int tail = Math.min(10, Math.max(0, history.size() - head));
        boolean truncated = head + tail < history.size();
        List<WorkingMemorySearchMessage> selected = new ArrayList<>(history.subList(0, head));
        if (truncated) {
            selected.addAll(history.subList(history.size() - tail, history.size()));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("mode", "read");
        payload.put("session_id", sessionId);
        payload.put("role_filter", String.join(",", roles));
        payload.put("message_count", history.size());
        payload.put("truncated", truncated);
        payload.put("messages", shapeMessages(selected, null));
        if (truncated) {
            payload.put("message",
                    "Session has " + history.size() + " visible messages; showing first " + head
                            + " + last " + tail
                            + ". Pass around_message_id (any id above) to scroll the middle.");
        }
        addSessionMetadata(payload, sessionId);
        return JSON.toJSONString(payload);
    }

    private String browse(String visitorId, String currentSessionId, int limit) {
        if (StringUtils.isBlank(visitorId)) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("success", true);
            empty.put("mode", "browse");
            empty.put("results", List.of());
            empty.put("count", 0);
            return JSON.toJSONString(empty);
        }
        List<Map<String, Object>> rows;
        try {
            rows = safeMapList(workingMemoryMessageDao.selectRecentSessions(trim(visitorId), limit + 1));
        } catch (Exception e) {
            return error("failed to browse sessions: " + messageOf(e));
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String sessionId = value(row, "sessionId", "session_id");
            if (StringUtils.isBlank(sessionId) || sessionId.equals(currentSessionId)) {
                continue;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("session_id", sessionId);
            result.put("title", value(row, "title"));
            result.put("preview", StringUtils.defaultIfBlank(
                    value(row, "latestQueryText", "latest_query_text"),
                    value(row, "latestSummaryText", "latest_summary_text")));
            result.put("started_at", value(row, "startedAt", "started_at"));
            result.put("last_active_at", value(row, "lastActiveAt", "last_active_at"));
            result.put("message_count", value(row, "messageCount", "message_count"));
            results.add(result);
            if (results.size() >= limit) {
                break;
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("mode", "browse");
        payload.put("results", results);
        payload.put("count", results.size());
        return JSON.toJSONString(payload);
    }

    private String requireOwnedSession(String sessionId, String visitorId) {
        if (StringUtils.isBlank(visitorId)) {
            return error("visitor identity required");
        }
        try {
            Map<String, Object> row = workingMemoryMessageDao.selectSessionSummary(sessionId);
            String owner = value(row, "visitorId", "visitor_id");
            if (row == null || row.isEmpty() || !visitorId.equals(owner)) {
                return error("session_id " + sessionId + " not found");
            }
        } catch (Exception e) {
            return error("failed to load session: " + messageOf(e));
        }
        return null;
    }

    private void addSessionMetadata(Map<String, Object> target, String sessionId) {
        try {
            Map<String, Object> row = workingMemoryMessageDao.selectSessionSummary(sessionId);
            if (row == null || row.isEmpty()) {
                return;
            }
            putIfPresent(target, "title", value(row, "title"));
            putIfPresent(target, "started_at", value(row, "startedAt", "started_at"));
            putIfPresent(target, "last_active_at", value(row, "lastActiveAt", "last_active_at"));
        } catch (Exception e) {
            log.debug("session metadata lookup failed sessionId={}: {}", sessionId, e.toString());
        }
    }

    private List<WorkingMemorySearchMessage> applyViewFilter(List<WorkingMemorySearchMessage> messages,
                                                             List<String> roles) {
        List<WorkingMemorySearchMessage> filtered = new ArrayList<>();
        for (WorkingMemorySearchMessage message : messages) {
            if (message == null) {
                continue;
            }
            if (!roleAllowed(message.getRole(), roles)) {
                continue;
            }
            if (isCompactionSummary(message.getContent())) {
                continue;
            }
            filtered.add(message);
        }
        return filtered;
    }

    private static boolean isCompactionSummary(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        String text = content.stripLeading();
        return text.startsWith("[CONTEXT COMPACTION")
                || text.startsWith("[CONTEXT SUMMARY]:")
                || text.startsWith("[COMPACTION SUMMARY]")
                || text.startsWith("[WM_COMPACTION_SUMMARY]");
    }

    private static List<Map<String, Object>> shapeMessages(List<WorkingMemorySearchMessage> messages, Long anchor) {
        List<Map<String, Object>> shaped = new ArrayList<>();
        for (WorkingMemorySearchMessage message : messages) {
            if (message == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", message.getId());
            item.put("stable_key", message.stableKey());
            item.put("role", message.getRole());
            String content = StringUtils.defaultString(message.getContent());
            if (content.length() > MAX_CONTENT_CHARS) {
                item.put("content", content.substring(0, MAX_CONTENT_CHARS) + "...");
                item.put("content_truncated", true);
                item.put("original_content_chars", content.length());
            } else {
                item.put("content", content);
            }
            item.put("seq_no", message.getSeqNo());
            if (anchor != null && anchor.equals(message.getId())) {
                item.put("anchor", true);
            }
            shaped.add(item);
        }
        return shaped;
    }

    private static String snippet(String content, String query, List<String> tokens) {
        String text = StringUtils.defaultString(content);
        String needle = StringUtils.defaultString(query);
        String lower = text.toLowerCase(Locale.ROOT);
        int match = StringUtils.isBlank(needle) ? -1 : lower.indexOf(needle.toLowerCase(Locale.ROOT));
        if (match < 0 && tokens != null) {
            for (String token : tokens) {
                if (StringUtils.isBlank(token)) {
                    continue;
                }
                match = lower.indexOf(token.toLowerCase(Locale.ROOT));
                if (match >= 0) {
                    needle = token;
                    break;
                }
            }
        }
        if (text.length() <= 280 || match < 0) {
            return text.length() <= 280 ? text : text.substring(0, 280) + "...";
        }
        int start = Math.max(0, match - 100);
        int end = Math.min(text.length(), start + 280);
        return (start > 0 ? "..." : "") + text.substring(start, end) + (end < text.length() ? "..." : "");
    }

    private static int indexOfMessage(List<WorkingMemorySearchMessage> history, long messageId) {
        for (int i = 0; i < history.size(); i++) {
            WorkingMemorySearchMessage message = history.get(i);
            if (message != null && Long.valueOf(messageId).equals(message.getId())) {
                return i;
            }
        }
        return -1;
    }

    private static int compareHistoryOrder(WorkingMemorySearchMessage left, WorkingMemorySearchMessage right) {
        return Comparator.comparing(WorkingMemorySearchMessage::getTurnSeq, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WorkingMemorySearchMessage::getSeqNo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WorkingMemorySearchMessage::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(left, right);
    }

    private static String normalizeScope(String scope) {
        return "session".equalsIgnoreCase(StringUtils.trimToEmpty(scope)) ? "session" : "user";
    }

    private static List<String> resolveRoles(String roleFilter) {
        if (StringUtils.isBlank(roleFilter)) {
            return List.of("USER", "ASSISTANT");
        }
        List<String> roles = new ArrayList<>();
        for (String token : roleFilter.split(",")) {
            String normalized = normalizeRoleToken(token);
            if (normalized != null && !roles.contains(normalized)) {
                roles.add(normalized);
            }
        }
        return roles.isEmpty() ? List.of("USER", "ASSISTANT") : roles;
    }

    private static String normalizeRoleToken(String token) {
        String value = StringUtils.trimToEmpty(token).toUpperCase(Locale.ROOT);
        return switch (value) {
            case "USER", "ASSISTANT", "TOOL", "SYSTEM" -> value;
            default -> null;
        };
    }

    private static boolean roleAllowed(String role, List<String> roles) {
        String normalized = normalizeRoleToken(role);
        return normalized != null && roles.contains(normalized);
    }

    private static List<String> tokenize(String query) {
        if (StringUtils.isBlank(query)) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String part : query.trim().split("\\s+")) {
            if (StringUtils.isNotBlank(part)) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static boolean matchesQuery(String content, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return false;
        }
        String haystack = StringUtils.defaultString(content).toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (!haystack.contains(token.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static int resolveWindow(Integer window) {
        if (window == null) {
            return DEFAULT_WINDOW;
        }
        return Math.max(1, Math.min(window, MAX_WINDOW));
    }

    private static String firstNonBlank(String first, String second) {
        String left = trim(first);
        return left != null ? left : trim(second);
    }

    private static String trim(String value) {
        return StringUtils.trimToNull(value);
    }

    private static String value(Map<String, Object> row, String... keys) {
        if (row == null) {
            return null;
        }
        for (String key : keys) {
            Object found = row.get(key);
            if (found != null) {
                return String.valueOf(found);
            }
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static List<WorkingMemorySearchMessage> safeList(List<WorkingMemorySearchMessage> value) {
        return value == null ? List.of() : value;
    }

    private static List<Map<String, Object>> safeMapList(List<Map<String, Object>> value) {
        return value == null ? List.of() : value;
    }

    private static String messageOf(Exception e) {
        return StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
    }

    private static String error(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("error", message);
        return JSON.toJSONString(payload);
    }
}

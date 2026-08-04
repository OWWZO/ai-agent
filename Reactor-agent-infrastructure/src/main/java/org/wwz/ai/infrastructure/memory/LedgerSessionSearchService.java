package org.wwz.ai.infrastructure.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.ledger.entity.ToolInvocation;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunView;
import org.wwz.ai.domain.agent.ledger.model.DialogueSessionView;
import org.wwz.ai.domain.agent.memory.ltm.SessionSearchService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * session_search：优先 MySQL ngram FULLTEXT，失败/无索引时降级为扫描 contains。
 * 不搜 working memory。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerSessionSearchService implements SessionSearchService {

    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_SNIPPET = 280;
    private static final int MAX_RUNS_SCAN = 120;
    private static final int MAX_SESSIONS = 30;

    private final IExecutionLedgerReadRepository executionLedgerReadRepository;

    /** FULLTEXT 连续失败后短期降级，避免每请求打错误日志 */
    private volatile long fullTextDisabledUntilMs = 0L;

    @Override
    public String search(String sessionId, String visitorId, String query, int limit, String scope) {
        if (StringUtils.isBlank(query)) {
            return "session_search: query is required";
        }
        String normalizedScope = normalizeScope(scope);
        int top = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 20);
        String q = query.trim();
        String resolvedVisitor = resolveVisitorId(sessionId, visitorId);

        // 1) FULLTEXT 优先。检索范围先固定为当前 session 或 visitor 的 ledger 会话集合，
        // 不把 working memory 当作搜索源，避免“提示词投影”反向冒充执行事实。
        List<DialogueRunView> fullTextHits = tryFullText(sessionId, resolvedVisitor, q, top, normalizedScope);
        if (fullTextHits != null) {
            if (fullTextHits.isEmpty()) {
                // 索引可用但无命中：仍尝试降级扫描 tool 字段（FULLTEXT 未覆盖 tool）
                String scan = scanFallback(sessionId, resolvedVisitor, q, top, normalizedScope);
                if (!scan.startsWith("session_search: no matches")) {
                    return scan + "\n(note: fulltext had 0 hits; used scan for tool fields)";
                }
                return "session_search: no matches for \"" + query + "\" scope=" + normalizedScope
                        + " mode=fulltext visitor=" + nullToEmpty(resolvedVisitor)
                        + " (ledger only; working memory is not searched)";
            }
            return formatHits(fullTextHits, sessionId, resolvedVisitor, normalizedScope, "fulltext", q);
        }

        // 2) FULLTEXT 不可用时扫描最近 run；扫描有硬上限，保证缺索引不会把一次工具调用
        // 变成无界数据库遍历。FULLTEXT 零命中时也会走一次扫描，以覆盖 tool input/observation。
        return scanFallback(sessionId, resolvedVisitor, q, top, normalizedScope);
    }

    private List<DialogueRunView> tryFullText(String sessionId,
                                              String visitorId,
                                              String query,
                                              int limit,
                                              String scope) {
        if (System.currentTimeMillis() < fullTextDisabledUntilMs) {
            return null;
        }
        try {
            List<DialogueRunView> hits;
            if ("session".equals(scope)) {
                if (StringUtils.isBlank(sessionId)) {
                    return List.of();
                }
                hits = executionLedgerReadRepository.searchRunsFullTextBySession(sessionId, query, limit);
            } else {
                if (StringUtils.isBlank(visitorId)) {
                    // 无 visitor 时退化为当前 session FULLTEXT
                    if (StringUtils.isBlank(sessionId)) {
                        return List.of();
                    }
                    hits = executionLedgerReadRepository.searchRunsFullTextBySession(sessionId, query, limit);
                } else {
                    hits = executionLedgerReadRepository.searchRunsFullTextByVisitor(visitorId, query, limit);
                }
            }
            return hits == null ? List.of() : hits;
        } catch (Exception e) {
            // 常见原因是索引未建或 ngram 未启用。短暂熔断只影响搜索方式，不影响主执行链路；
            // 下一次请求直接使用 contains 扫描，避免同一配置问题持续刷错误日志。
            fullTextDisabledUntilMs = System.currentTimeMillis() + 60_000L;
            log.warn("session_search FULLTEXT unavailable, fallback to scan for 60s: {}", e.toString());
            return null;
        }
    }

    private String formatHits(List<DialogueRunView> runs,
                              String currentSessionId,
                              String visitorId,
                              String scope,
                              String mode,
                              String query) {
        List<String> hits = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT);
        for (DialogueRunView run : runs) {
            if (run == null) {
                continue;
            }
            String queryText = nullToEmpty(run.getQueryText());
            String summary = nullToEmpty(run.getFinalSummaryText());
            StringBuilder toolHits = new StringBuilder();
            if (run.getId() != null) {
                appendToolHits(run.getId(), q, toolHits);
            }
            hits.add(formatHit(run, run.getSessionId(), currentSessionId, queryText, summary,
                    toolHits.toString(), run.getSearchScore()));
        }
        return "session_search hits (" + hits.size() + ") scope=" + scope
                + " mode=" + mode
                + " visitor=" + nullToEmpty(visitorId) + ":\n"
                + String.join("\n\n", hits);
    }

    private String scanFallback(String sessionId, String visitorId, String query, int limit, String scope) {
        String q = query.toLowerCase(Locale.ROOT);
        List<String> sessionIds = resolveSessionIds(sessionId, visitorId, scope);
        if (sessionIds.isEmpty()) {
            return "session_search: no sessions to search (sessionId/visitorId missing or empty)";
        }
        try {
            List<String> hits = new ArrayList<>();
            int scanned = 0;
            // 倒序遍历每个 session，使有限扫描预算优先覆盖最近执行；LinkedHashSet 已在
            // resolveSessionIds 中去重，当前 session 仍保留在 user 范围的第一位。
            for (String sid : sessionIds) {
                if (hits.size() >= limit || scanned >= MAX_RUNS_SCAN) {
                    break;
                }
                List<DialogueRunView> runs = executionLedgerReadRepository.queryRunsBySessionId(sid);
                if (runs == null || runs.isEmpty()) {
                    continue;
                }
                for (int i = runs.size() - 1; i >= 0 && hits.size() < limit && scanned < MAX_RUNS_SCAN; i--) {
                    DialogueRunView run = runs.get(i);
                    scanned++;
                    if (run == null) {
                        continue;
                    }
                    String hit = matchRunScan(run, q, sid, sessionId);
                    if (hit != null) {
                        hits.add(hit);
                    }
                }
            }
            if (hits.isEmpty()) {
                return "session_search: no matches for \"" + query + "\" scope=" + scope
                        + " mode=scan sessions=" + sessionIds.size() + " scannedRuns=" + scanned
                        + " (ledger only; working memory is not searched)";
            }
            return "session_search hits (" + hits.size() + ") scope=" + scope
                    + " mode=scan visitor=" + nullToEmpty(visitorId) + ":\n"
                    + String.join("\n\n", hits);
        } catch (Exception e) {
            log.warn("session_search scan failed sessionId={} visitorId={}: {}", sessionId, visitorId, e.toString());
            return "session_search failed: " + e.getMessage();
        }
    }

    private String matchRunScan(DialogueRunView run, String q, String runSessionId, String currentSessionId) {
        String queryText = nullToEmpty(run.getQueryText());
        String summary = nullToEmpty(run.getFinalSummaryText());
        boolean hit = contains(queryText, q) || contains(summary, q);
        StringBuilder toolHits = new StringBuilder();
        if (run.getId() != null) {
            hit = appendToolHits(run.getId(), q, toolHits) || hit;
        }
        if (!hit) {
            return null;
        }
        return formatHit(run, runSessionId, currentSessionId, queryText, summary, toolHits.toString(), null);
    }

    private boolean appendToolHits(Long runId, String q, StringBuilder toolHits) {
        boolean hit = false;
        try {
            // run 的 query/summary 是可直接全文索引的字段，工具名、参数和观察结果则通过
            // tool_invocation 二次读取补齐；这样不会为了支持工具字段而扩大 FULLTEXT 索引。
            List<ToolInvocation> tools = executionLedgerReadRepository.queryToolInvocationsByRunId(runId);
            if (tools == null) {
                return false;
            }
            for (ToolInvocation tool : tools) {
                if (tool == null) {
                    continue;
                }
                String name = nullToEmpty(tool.getToolName());
                String args = nullToEmpty(tool.getInputJson());
                String obs = nullToEmpty(tool.getLlmObservation());
                if (contains(name, q) || contains(args, q) || contains(obs, q)) {
                    hit = true;
                    toolHits.append("\n  - tool=").append(name)
                            .append(" toolCallId=").append(nullToEmpty(tool.getToolCallId()));
                }
            }
        } catch (Exception e) {
            log.debug("appendToolHits failed runId={}: {}", runId, e.toString());
        }
        return hit;
    }

    private List<String> resolveSessionIds(String sessionId, String visitorId, String scope) {
        Set<String> ids = new LinkedHashSet<>();
        if ("session".equals(scope)) {
            if (StringUtils.isNotBlank(sessionId)) {
                ids.add(sessionId.trim());
            }
            return new ArrayList<>(ids);
        }
        if (StringUtils.isNotBlank(sessionId)) {
            ids.add(sessionId.trim());
        }
        if (StringUtils.isNotBlank(visitorId)) {
            List<DialogueSessionView> sessions =
                    executionLedgerReadRepository.queryRecentSessions(visitorId.trim(), MAX_SESSIONS);
            if (sessions != null) {
                for (DialogueSessionView s : sessions) {
                    if (s != null && StringUtils.isNotBlank(s.getSessionId())) {
                        ids.add(s.getSessionId().trim());
                    }
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private String resolveVisitorId(String sessionId, String visitorId) {
        if (StringUtils.isNotBlank(visitorId)) {
            return visitorId.trim();
        }
        if (StringUtils.isBlank(sessionId)) {
            return null;
        }
        try {
            DialogueSession entity = executionLedgerReadRepository.querySessionEntity(sessionId);
            if (entity != null && StringUtils.isNotBlank(entity.getVisitorId())) {
                return entity.getVisitorId().trim();
            }
            DialogueSessionView view = executionLedgerReadRepository.querySession(sessionId);
            if (view != null && StringUtils.isNotBlank(view.getVisitorId())) {
                return view.getVisitorId().trim();
            }
        } catch (Exception e) {
            log.debug("resolveVisitorId failed sessionId={}: {}", sessionId, e.toString());
        }
        return null;
    }

    private static String normalizeScope(String scope) {
        if (StringUtils.isBlank(scope)) {
            return "user";
        }
        String s = scope.trim().toLowerCase(Locale.ROOT);
        if ("session".equals(s) || "this".equals(s) || "current".equals(s)) {
            return "session";
        }
        return "user";
    }

    private static String formatHit(DialogueRunView run,
                                    String runSessionId,
                                    String currentSessionId,
                                    String queryText,
                                    String summary,
                                    String toolHits,
                                    Double score) {
        StringBuilder sb = new StringBuilder();
        boolean current = StringUtils.isNotBlank(currentSessionId)
                && currentSessionId.equals(runSessionId);
        sb.append("- sessionId=").append(nullToEmpty(runSessionId))
                .append(current ? " (current)" : "")
                .append(" runId=").append(run.getId())
                .append(" requestId=").append(nullToEmpty(run.getRequestId()))
                .append(" status=").append(run.getStatus());
        if (score != null) {
            sb.append(" score=").append(String.format(Locale.ROOT, "%.4f", score));
        }
        if (StringUtils.isNotBlank(queryText)) {
            sb.append("\n  query: ").append(snippet(queryText));
        }
        if (StringUtils.isNotBlank(summary)) {
            sb.append("\n  summary: ").append(snippet(summary));
        }
        if (StringUtils.isNotBlank(toolHits)) {
            sb.append(toolHits);
        }
        return sb.toString();
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String snippet(String text) {
        String t = text.replace('\n', ' ').trim();
        if (t.length() <= MAX_SNIPPET) {
            return t;
        }
        return t.substring(0, MAX_SNIPPET) + "...";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

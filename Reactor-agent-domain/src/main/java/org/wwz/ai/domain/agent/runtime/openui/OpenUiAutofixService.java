package org.wwz.ai.domain.agent.runtime.openui;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpResponse;
import org.wwz.ai.domain.agent.runtime.tool.common.openui.OpenUiResources;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenUI Autofix：把前端判定为不可渲染的程序交给 Thesys 的修复模型，换回一份可渲染的完整程序。
 *
 * <p>为什么校验在前端、修复在后端：判断"这段 DSL 能不能渲染"必须用带 parser 的那一端，
 * 也就是浏览器；而 API key 只能待在服务端。所以链路是 前端发现无效 → 调本服务 → 换回整段程序。</p>
 *
 * <p>约束（来自 Thesys 文档，超限会 400）：程序 ≤ 100,000 字符；上下文 ≤ 20 轮且 ≤ 8,000 字符。
 * 这里主动裁剪历史，不指望服务端兜底。</p>
 */
@Slf4j
@Component
public class OpenUiAutofixService {

    /** 单次修复的程序长度上限。 */
    public static final int MAX_PROGRAM_CHARS = 100_000;
    /** 上下文轮数上限。 */
    public static final int MAX_CONTEXT_TURNS = 20;
    /** 上下文文本总长度上限。 */
    public static final int MAX_CONTEXT_CHARS = 8_000;

    /** 只接受这几种角色的上下文，工具轮与非文本 part 文档明确会被丢弃。 */
    private static final Set<String> ALLOWED_ROLES = Set.of("user", "assistant", "system", "developer");

    private final RemoteHttpPort http;
    private final boolean enabled;
    private final String apiKey;
    private final String baseUrl;
    private final long timeoutSeconds;

    public OpenUiAutofixService(RemoteHttpPort http,
                                @Value("${autobots.autoagent.openui.autofix.enabled:false}") boolean enabled,
                                @Value("${autobots.autoagent.openui.autofix.api-key:}") String apiKey,
                                @Value("${autobots.autoagent.openui.autofix.base-url:https://api.thesys.dev/v1/autofix}") String baseUrl,
                                @Value("${autobots.autoagent.openui.autofix.timeout-seconds:90}") long timeoutSeconds) {
        this.http = http;
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** 是否具备实际修复能力（开关开 + 有 key）。 */
    public boolean isAvailable() {
        return enabled && StringUtils.isNotBlank(apiKey);
    }

    /**
     * 修复一段 openui-lang 程序。
     *
     * @param program 待修复的裸程序（不含 marker 行）
     * @param context 可选对话上下文，用于让修复模型理解原意
     */
    public OpenUiFixResult fix(String program, List<OpenUiFixTurn> context) {
        if (!isAvailable()) {
            return OpenUiFixResult.disabled();
        }
        String source = program == null ? "" : program.trim();
        if (source.isEmpty()) {
            return OpenUiFixResult.error("程序为空");
        }
        if (source.length() > MAX_PROGRAM_CHARS) {
            return OpenUiFixResult.error("程序超过 " + MAX_PROGRAM_CHARS + " 字符，Autofix 不接受");
        }

        String body;
        try {
            body = JSON.toJSONString(buildRequestBody(source, context));
        } catch (Exception e) {
            log.warn("openui autofix 构造请求失败", e);
            return OpenUiFixResult.error("构造请求失败: " + e.getMessage());
        }

        RemoteHttpRequest request = RemoteHttpRequest.builder()
                .method("POST")
                .url(baseUrl)
                .headers(Map.of(
                        "Authorization", "Bearer " + apiKey,
                        "Content-Type", "application/json"))
                .body(body)
                .connectTimeoutSeconds(15L)
                .readTimeoutSeconds(timeoutSeconds)
                .callTimeoutSeconds(timeoutSeconds)
                .build();

        RemoteHttpResponse response;
        try {
            response = http.executeDetailed(request);
        } catch (Exception e) {
            log.warn("openui autofix 请求失败: {}", e.getMessage());
            return OpenUiFixResult.error("请求失败: " + e.getMessage());
        }

        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            // 401 = key 无效，429 = 没额度/账单暂停，400 = 请求不合法（含超限、stream:true）
            String detail = StringUtils.left(StringUtils.defaultString(response.getBody()), 300);
            log.warn("openui autofix HTTP {}: {}", response.getStatusCode(), detail);
            return OpenUiFixResult.error("HTTP " + response.getStatusCode() + ": " + detail);
        }

        return parseResponse(response.getBody(), source);
    }

    private Map<String, Object> buildRequestBody(String program, List<OpenUiFixTurn> context) {
        List<Map<String, String>> messages = new ArrayList<>();
        // 首条必须是携带 ]] >openui:config 的 system turn：API 只从这条读组件库，且不计入上下文限制。
        messages.add(message("system", OpenUiResources.autofixConfigBlock()));
        for (OpenUiFixTurn turn : trimContext(context)) {
            messages.add(message(turn.role(), turn.content()));
        }
        // 末条必须是 assistant turn，内容就是要修的那段程序。
        messages.add(message("assistant", program));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", messages);
        return body;
    }

    /** 用 LinkedHashMap 保证序列化顺序稳定，便于排查和复用。 */
    private static Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    /**
     * 从最近的一轮往前保留，直到轮数或字符数触顶。文档说超限直接 400，不会自动截断。
     */
    List<OpenUiFixTurn> trimContext(List<OpenUiFixTurn> context) {
        if (context == null || context.isEmpty()) {
            return List.of();
        }
        List<OpenUiFixTurn> kept = new ArrayList<>();
        int chars = 0;
        for (int i = context.size() - 1; i >= 0; i--) {
            OpenUiFixTurn turn = context.get(i);
            if (turn == null || StringUtils.isBlank(turn.content())) {
                continue;
            }
            String role = StringUtils.defaultIfBlank(turn.role(), "user").toLowerCase();
            if (!ALLOWED_ROLES.contains(role)) {
                continue;
            }
            String content = turn.content();
            if (kept.size() >= MAX_CONTEXT_TURNS || chars + content.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            kept.add(new OpenUiFixTurn(role, content));
            chars += content.length();
        }
        java.util.Collections.reverse(kept);
        return kept;
    }

    private OpenUiFixResult parseResponse(String raw, String originalProgram) {
        JSONObject root;
        try {
            root = JSON.parseObject(raw);
        } catch (Exception e) {
            log.warn("openui autofix 响应不是合法 JSON: {}", StringUtils.left(StringUtils.defaultString(raw), 200));
            return OpenUiFixResult.error("响应解析失败");
        }
        if (root == null) {
            return OpenUiFixResult.error("响应为空");
        }

        JSONObject summary = root.getJSONObject("fix_summary");
        String status = summary == null ? null : summary.getString("status");
        String content = readContent(root);
        if (status == null) {
            return OpenUiFixResult.error("响应缺少 fix_summary");
        }

        switch (status) {
            case OpenUiFixResult.FIXED:
                return content == null || content.isBlank()
                        ? OpenUiFixResult.error("修复成功但未返回内容")
                        : OpenUiFixResult.fixed(content, readErrors(summary, "fixed_errors"));
            case OpenUiFixResult.ALREADY_VALID:
                return OpenUiFixResult.alreadyValid(content == null ? originalProgram : content);
            case OpenUiFixResult.FIX_FAILED:
                return OpenUiFixResult.fixFailed(readErrors(summary, "unfixed_errors"));
            default:
                return OpenUiFixResult.error("未知修复状态: " + status);
        }
    }

    private static String readContent(JSONObject root) {
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice == null ? null : choice.getJSONObject("message");
        return message == null ? null : message.getString("content");
    }

    private static List<OpenUiFixError> readErrors(JSONObject summary, String field) {
        JSONArray array = summary.getJSONArray(field);
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<OpenUiFixError> errors = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (item == null) {
                continue;
            }
            errors.add(new OpenUiFixError(
                    item.getString("code"),
                    item.getString("message"),
                    item.getString("statementId")));
        }
        return errors;
    }
}

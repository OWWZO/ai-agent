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
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.llm.LlmCompletionPort;
import org.wwz.ai.domain.agent.runtime.llm.LlmMessage;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;
import org.wwz.ai.domain.agent.runtime.llm.LlmRequest;
import org.wwz.ai.domain.agent.runtime.llm.LlmResponse;
import org.wwz.ai.domain.agent.runtime.tool.common.openui.OpenUiResources;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenUI Autofix：把前端判定为不可渲染的程序交给修复模型。
 *
 * <p>两条链路（plans/038 v4）：</p>
 * <ul>
 *   <li><b>整段修复</b>（{@link #fix}）：Thesys Autofix 换回完整程序。v4 起前端默认不再
 *   走这条路，仅在意图修复未启用时作为回退保留。</li>
 *   <li><b>意图修复</b>（{@link #fixWithIntent}）：主 agent 模型目录出"SEARCH/REPLACE 块"，
 *   后端只解析透传，执行与验收都在前端。intent-enabled 默认关，灰度用。</li>
 * </ul>
 *
 * <p>为什么校验在前端、修复在后端：判断"这段 DSL 能不能渲染"必须用带 parser 的那一端，
 * 也就是浏览器。Thesys 链路的 key 只能待在服务端，意图链路则复用主 agent 模型目录。</p>
 *
 * <p>整段链路约束（来自 Thesys 文档，超限会 400）：程序 ≤ 100,000 字符；上下文 ≤ 20 轮且 ≤ 8,000 字符。</p>
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
    /** 上送模型的错误条数上限（诊断只是辅助，错误太多说明该重生成而不是该修）。 */
    private static final int MAX_DIAGNOSIS_ERRORS = 8;
    /** 上一轮失败反馈里块的原文截断长度（只为提示，模型不需要完整复述）。 */
    private static final int MAX_FEEDBACK_SNIPPET = 200;

    /** 只接受这几种角色的上下文，工具轮与非文本 part 文档明确会被丢弃。 */
    private static final Set<String> ALLOWED_ROLES = Set.of("user", "assistant", "system", "developer");

    private final RemoteHttpPort http;
    private final LlmCompletionPort completionPort;
    private final LlmModelCatalog modelCatalog;
    private final boolean enabled;
    private final boolean intentEnabled;
    private final String apiKey;
    private final String baseUrl;
    private final long timeoutSeconds;

    public OpenUiAutofixService(RemoteHttpPort http,
                                LlmCompletionPort completionPort,
                                LlmModelCatalog modelCatalog,
                                @Value("${autobots.autoagent.openui.autofix.enabled:false}") boolean enabled,
                                @Value("${autobots.autoagent.openui.autofix.intent-enabled:false}") boolean intentEnabled,
                                @Value("${autobots.autoagent.openui.autofix.api-key:}") String apiKey,
                                @Value("${autobots.autoagent.openui.autofix.base-url:https://api.thesys.dev/v1/autofix}") String baseUrl,
                                @Value("${autobots.autoagent.openui.autofix.timeout-seconds:90}") long timeoutSeconds) {
        this.http = http;
        this.completionPort = completionPort;
        this.modelCatalog = modelCatalog;
        this.enabled = enabled;
        this.intentEnabled = intentEnabled;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** 整段修复（Thesys）是否具备实际能力（开关开 + 有 key）。 */
    public boolean isAvailable() {
        return enabled && StringUtils.isNotBlank(apiKey);
    }

    /** 意图修复是否启用（v4 主链路的灰度开关，默认关）。 */
    public boolean isIntentAvailable() {
        return intentEnabled;
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

    /**
     * 意图修复（plans/038 v4 主链路）：整段原文 + 前端诊断 → 模型 → SEARCH/REPLACE 块。
     *
     * <p>组件库用给模型看的两份静态产物（≈24KB），不用 Thesys 那份 53KB 的 cloud-config；
     * 复用主 agent 模型目录（默认绑定），不写执行账本 —— 写账本是上层 {@code LLM} 门面的事。</p>
     *
     * @param program      待修复的裸程序
     * @param diagnosis    前端 parser 诊断（incomplete + 逐条错误）
     * @param feedbackNote 上一轮失败的反馈摘要（首轮为 null），重试成功率的主要来源
     */
    public OpenUiIntentFixResult fixWithIntent(String program, OpenUiDiagnosis diagnosis, String feedbackNote) {
        if (!isIntentAvailable()) {
            return OpenUiIntentFixResult.disabled();
        }
        String source = program == null ? "" : program.trim();
        if (source.isEmpty()) {
            return OpenUiIntentFixResult.error("程序为空");
        }
        if (source.length() > MAX_PROGRAM_CHARS) {
            return OpenUiIntentFixResult.error("程序超过 " + MAX_PROGRAM_CHARS + " 字符");
        }
        if (modelCatalog == null || completionPort == null) {
            return OpenUiIntentFixResult.error("模型出站能力未装配");
        }
        // 复用主 agent 模型目录：未配置默认绑定就不硬造，返回错误由前端降级。
        LLMSettingsHolder holder = resolveSettings();
        if (holder.settings == null) {
            return OpenUiIntentFixResult.error("模型目录无可用绑定");
        }

        List<LlmMessage> messages = List.of(
                LlmMessage.builder().role(RoleType.SYSTEM).content(buildIntentSystemPrompt()).build(),
                LlmMessage.builder().role(RoleType.USER)
                        .content(buildIntentUserPrompt(source, diagnosis, feedbackNote)).build());
        LlmRequest request = LlmRequest.builder()
                .model(holder.settings.getModel())
                .settings(holder.settings)
                .messages(messages)
                .temperature(0.0)
                .build();

        LlmResponse response;
        try {
            response = completionPort.complete(request);
        } catch (Exception e) {
            log.warn("openui 意图修复请求失败: {}", e.getMessage());
            return OpenUiIntentFixResult.error("请求失败: " + e.getMessage());
        }
        String output = response == null ? null : response.getContent();
        if (StringUtils.isBlank(output)) {
            return OpenUiIntentFixResult.error("模型返回为空");
        }
        List<OpenUiRepairIntent> blocks = OpenUiIntentParser.parse(output);
        return blocks.isEmpty()
                ? OpenUiIntentFixResult.empty(holder.settings.getModel())
                : OpenUiIntentFixResult.ok(blocks, holder.settings.getModel());
    }

    /** 中转 holder：Optional 无法携带"解析成功但 settings 为空"以外的情况区分，这里只需要 null 判断。 */
    private record LLMSettingsHolder(org.wwz.ai.domain.agent.runtime.llm.LLMSettings settings) {
    }

    private LLMSettingsHolder resolveSettings() {
        try {
            return new LLMSettingsHolder(modelCatalog.resolve(LlmModelCatalog.DEFAULT_MODEL).orElse(null));
        } catch (Exception e) {
            log.warn("openui 意图修复解析模型绑定失败: {}", e.getMessage());
            return new LLMSettingsHolder(null);
        }
    }

    String buildIntentSystemPrompt() {
        return OpenUiResources.residentPrompt() + "\n\n" + OpenUiResources.componentsCatalog();
    }

    String buildIntentUserPrompt(String program, OpenUiDiagnosis diagnosis, String feedbackNote) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("下面这段 openui-lang 程序无法渲染，请给出最小修复。\n\n");
        if (diagnosis != null) {
            prompt.append("## parser 报告的错误\n");
            if (diagnosis.incomplete()) {
                prompt.append("- 程序解析中断（可能存在非法语法），以下错误可能不完整\n");
            }
            List<OpenUiFixError> errors = diagnosis.errors();
            if (errors == null || errors.isEmpty()) {
                prompt.append("-（无逐条错误）\n");
            } else {
                for (OpenUiFixError error : errors.subList(0, Math.min(errors.size(), MAX_DIAGNOSIS_ERRORS))) {
                    prompt.append("- ").append(StringUtils.defaultIfBlank(error.code(), "error"))
                            .append(": ").append(StringUtils.defaultIfBlank(error.message(), "")).append('\n');
                }
            }
            prompt.append('\n');
        }
        prompt.append("""
                ## 输出要求
                只输出 SEARCH/REPLACE 块，格式严格如下（不要解释、不要代码围栏、不要多余文字）：
                <<<<<<< SEARCH
                原文中要被替换的片段（必须与原文逐字一致，含缩进与换行）
                =======
                替换后的片段
                >>>>>>> REPLACE

                - 最小修改：SEARCH 只圈出报错的那一小段，其他内容一律不要动
                - SEARCH 在原文中必须唯一出现；长度不超过一条语句
                - 最多 3 个块；插入用 SEARCH 当锚点，删除让 REPLACE 为空
                - 修不了的错误就不要输出块
                """);
        if (StringUtils.isNotBlank(feedbackNote)) {
            prompt.append("\n## 上一轮失败反馈（请修正这些问题）\n").append(trimFeedback(feedbackNote)).append('\n');
        }
        prompt.append("\n## 程序原文\n").append(program);
        return prompt.toString();
    }

    private static String trimFeedback(String feedbackNote) {
        String note = feedbackNote.trim();
        return note.length() <= MAX_FEEDBACK_SNIPPET * 4 ? note : note.substring(0, MAX_FEEDBACK_SNIPPET * 4) + "…";
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

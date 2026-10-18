package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiAutofixService;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiFixError;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiFixResult;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiFixTurn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * openui 程序修复入口。
 *
 * <p>前端用 lang-core 的 parser 判定"这段程序渲染不了"之后调用这里，后端把程序交给 Thesys Autofix
 * 换回一份可渲染的完整程序。API key 只留在服务端。</p>
 *
 * <p>失败不抛 5xx：修复是增强能力，调用方拿到 status 自行决定降级（显示原文 + 重试按钮）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/openui")
public class OpenUiRepairController {

    private final OpenUiAutofixService autofixService;

    public OpenUiRepairController(OpenUiAutofixService autofixService) {
        this.autofixService = autofixService;
    }

    @PostMapping("/repair")
    public ResponseEntity<?> repair(@RequestBody(required = false) Map<String, Object> body) {
        String program = body == null ? null : asString(body.get("program"));
        List<OpenUiFixTurn> context = body == null ? List.of() : parseContext(body.get("context"));

        OpenUiFixResult result = autofixService.fix(program, context);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", result.status());
        payload.put("content", result.content());
        payload.put("errors", toErrorMaps(result.errors()));
        if (result.message() != null) {
            payload.put("message", result.message());
        }
        return ResponseEntity.ok(payload);
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    @SuppressWarnings("unchecked")
    private static List<OpenUiFixTurn> parseContext(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<OpenUiFixTurn> turns = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object role = ((Map<String, Object>) map).get("role");
            Object content = ((Map<String, Object>) map).get("content");
            if (role instanceof String roleText && content instanceof String contentText) {
                turns.add(new OpenUiFixTurn(roleText, contentText));
            }
        }
        return turns;
    }

    private static List<Map<String, Object>> toErrorMaps(List<OpenUiFixError> errors) {
        if (errors == null || errors.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(errors.size());
        for (OpenUiFixError error : errors) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", error.code());
            item.put("message", error.message());
            if (error.statementId() != null) {
                item.put("statementId", error.statementId());
            }
            out.add(item);
        }
        return out;
    }
}

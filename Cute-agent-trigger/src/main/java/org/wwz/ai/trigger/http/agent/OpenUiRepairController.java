package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.domain.agent.adapter.repository.IOpenuiRepairIntentRepository;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiAutofixService;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiDiagnosis;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiFixError;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiFixResult;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiFixTurn;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiIntentFixResult;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiRepairIntent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * openui 程序修复入口。
 *
 * <p>POST /repair：前端用 lang-core 的 parser 判定"这段程序渲染不了"之后调用。
 * body 带 {@code mode:"intent"} 走意图链路（返回 SEARCH/REPLACE 块，执行与验收在前端），
 * 否则走 Thesys 整段修复（v4 起仅作意图未启用时的回退）。API key / 模型配置只留在服务端。</p>
 *
 * <p>POST /repair/intents 与 GET /repair/intents：意图落库与重放（plans/038 §10）。
 * 只应保存前端验收通过的意图；重放按 sessionId+messageId+programHash 拉全部意图。</p>
 *
 * <p>失败不抛 5xx：修复是增强能力，调用方拿到 status 自行决定降级（显示原文）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/openui")
public class OpenUiRepairController {

    private final OpenUiAutofixService autofixService;
    private final IOpenuiRepairIntentRepository repairIntentRepository;

    public OpenUiRepairController(OpenUiAutofixService autofixService,
                                  IOpenuiRepairIntentRepository repairIntentRepository) {
        this.autofixService = autofixService;
        this.repairIntentRepository = repairIntentRepository;
    }

    @PostMapping("/repair")
    public ResponseEntity<?> repair(@RequestBody(required = false) Map<String, Object> body) {
        String program = body == null ? null : asString(body.get("program"));
        String mode = body == null ? null : asString(body.get("mode"));

        if ("intent".equals(mode)) {
            return repairByIntent(body, program);
        }
        return repairWhole(program, body);
    }

    /** 意图链路：后端只解析透传 SEARCH/REPLACE 块。 */
    private ResponseEntity<?> repairByIntent(Map<String, Object> body, String program) {
        OpenUiDiagnosis diagnosis = parseDiagnosis(body == null ? null : body.get("diagnosis"));
        String feedback = parseFeedbackNote(body == null ? null : body.get("feedback"));

        OpenUiIntentFixResult result = autofixService.fixWithIntent(program, diagnosis, feedback);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", result.status());
        payload.put("mode", "intent");
        payload.put("blocks", result.blocks().stream()
                .map(block -> Map.of(
                        "searchText", block.searchText() == null ? "" : block.searchText(),
                        "replaceText", block.replaceText() == null ? "" : block.replaceText()))
                .toList());
        if (result.model() != null) {
            payload.put("model", result.model());
        }
        if (result.message() != null) {
            payload.put("message", result.message());
        }
        return ResponseEntity.ok(payload);
    }

    /** 整段链路（Thesys）：返回修好的完整程序。 */
    private ResponseEntity<?> repairWhole(String program, Map<String, Object> body) {
        List<OpenUiFixTurn> context = parseContext(body == null ? null : body.get("context"));

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

    /** 意图落库（覆盖式）。只存前端验收通过的内容；key 不全或块非法按 400 处理。 */
    @PostMapping("/repair/intents")
    public ResponseEntity<?> saveIntents(@RequestBody(required = false) Map<String, Object> body) {
        String sessionId = body == null ? null : asString(body.get("sessionId"));
        String messageId = body == null ? null : asString(body.get("messageId"));
        String programHash = body == null ? null : asString(body.get("programHash"));
        String model = body == null ? null : asString(body.get("model"));
        if (isBlank(sessionId) || isBlank(messageId) || isBlank(programHash)) {
            return ResponseEntity.badRequest().body(Map.of("message", "sessionId/messageId/programHash 必填"));
        }
        List<OpenUiRepairIntent> blocks = parseBlocks(body == null ? null : body.get("blocks"));
        if (blocks.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "blocks 为空，不落库"));
        }
        try {
            repairIntentRepository.saveAll(sessionId, messageId, programHash, blocks, model);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.warn("openui 修复意图落库失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("message", "落库失败"));
        }
        return ResponseEntity.ok(Map.of("saved", true, "count", blocks.size()));
    }

    /** 重放：按 key 拉全部意图（intent_index 升序）。 */
    @GetMapping("/repair/intents")
    public ResponseEntity<?> listIntents(@RequestParam("sessionId") String sessionId,
                                         @RequestParam("messageId") String messageId,
                                         @RequestParam("programHash") String programHash) {
        if (isBlank(sessionId) || isBlank(messageId) || isBlank(programHash)) {
            return ResponseEntity.badRequest().body(Map.of("message", "sessionId/messageId/programHash 必填"));
        }
        List<OpenUiRepairIntent> intents;
        try {
            intents = repairIntentRepository.listByProgram(sessionId, messageId, programHash);
        } catch (Exception e) {
            log.warn("openui 修复意图重放查询失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("message", "查询失败"));
        }
        List<Map<String, Object>> blocks = new ArrayList<>(intents.size());
        for (int i = 0; i < intents.size(); i++) {
            OpenUiRepairIntent intent = intents.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("intentIndex", i);
            item.put("searchText", intent.searchText());
            item.put("replaceText", intent.replaceText());
            blocks.add(item);
        }
        return ResponseEntity.ok(Map.of("blocks", blocks));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 把前端回传的上一轮失败摘要（{invalidBlocks, parseErrors}）拼成给模型看的文本。
     * 只做拼接不改写 —— 失败反馈是重试成功率的主要来源，信息保真优先。
     */
    @SuppressWarnings("unchecked")
    private static String parseFeedbackNote(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> feedback = (Map<String, Object>) map;
        StringBuilder note = new StringBuilder();
        Object invalidBlocks = feedback.get("invalidBlocks");
        if (invalidBlocks instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> blockMap)) {
                    continue;
                }
                Map<String, Object> block = (Map<String, Object>) blockMap;
                note.append("- 块 #").append(block.get("index"))
                        .append(" 被作废（").append(asString(block.get("reason"))).append("）");
                String snippet = asString(block.get("searchText"));
                if (snippet != null && !snippet.isBlank()) {
                    String compact = snippet.replaceAll("\\s+", " ").trim();
                    if (compact.length() > 120) {
                        compact = compact.substring(0, 120) + "…";
                    }
                    note.append("，SEARCH 开头：").append(compact);
                }
                note.append('\n');
            }
        }
        Object parseErrors = feedback.get("parseErrors");
        if (parseErrors instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> errorMap)) {
                    continue;
                }
                Map<String, Object> error = (Map<String, Object>) errorMap;
                note.append("- 应用后 parser 仍报错：").append(asString(error.get("code")))
                        .append(": ").append(asString(error.get("message"))).append('\n');
            }
        }
        String result = note.toString().trim();
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private static OpenUiDiagnosis parseDiagnosis(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new OpenUiDiagnosis(false, List.of());
        }
        Map<String, Object> source = (Map<String, Object>) map;
        boolean incomplete = Boolean.TRUE.equals(source.get("incomplete"));
        List<OpenUiFixError> errors = new ArrayList<>();
        Object rawErrors = source.get("errors");
        if (rawErrors instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> errorMap)) {
                    continue;
                }
                Map<String, Object> error = (Map<String, Object>) errorMap;
                errors.add(new OpenUiFixError(
                        asString(error.get("code")),
                        asString(error.get("message")),
                        asString(error.get("statementId"))));
            }
        }
        return new OpenUiDiagnosis(incomplete, errors);
    }

    @SuppressWarnings("unchecked")
    private static List<OpenUiRepairIntent> parseBlocks(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<OpenUiRepairIntent> blocks = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> block = (Map<String, Object>) map;
            String searchText = asString(block.get("searchText"));
            String replaceText = asString(block.get("replaceText"));
            if (searchText == null || searchText.isBlank()) {
                continue;
            }
            blocks.add(new OpenUiRepairIntent(searchText, replaceText == null ? "" : replaceText));
        }
        return blocks;
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

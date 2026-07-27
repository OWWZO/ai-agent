package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.GenUiExportService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * GenUI export endpoints (PDF / DOCX). report_tool is intentionally kept.
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/genui")
public class AgentGenUiExportController {

    @PostMapping("/export/pdf")
    public ResponseEntity<?> exportPdf(@RequestBody Map<String, Object> body) {
        try {
            Object tree = body == null ? null : body.get("tree");
            String mode = body == null ? "document" : String.valueOf(body.getOrDefault("mode", "document"));
            byte[] bytes = GenUiExportService.exportPdf(tree, mode);
            String filename = "genui-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes);
        } catch (IllegalArgumentException e) {
            log.warn("genui pdf export validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("code", "400", "info", e.getMessage()));
        } catch (Exception e) {
            log.error("genui pdf export failed", e);
            return ResponseEntity.internalServerError().body(Map.of("code", "500", "info", e.getMessage()));
        }
    }

    @PostMapping("/export/docx")
    public ResponseEntity<?> exportDocx(@RequestBody Map<String, Object> body) {
        try {
            Object tree = body == null ? null : body.get("tree");
            String mode = body == null ? "document" : String.valueOf(body.getOrDefault("mode", "document"));
            byte[] bytes = GenUiExportService.exportDocx(tree, mode);
            String filename = "genui-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".docx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(bytes);
        } catch (IllegalArgumentException e) {
            log.warn("genui docx export validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("code", "400", "info", e.getMessage()));
        } catch (Exception e) {
            log.error("genui docx export failed", e);
            return ResponseEntity.internalServerError().body(Map.of("code", "500", "info", e.getMessage()));
        }
    }
}

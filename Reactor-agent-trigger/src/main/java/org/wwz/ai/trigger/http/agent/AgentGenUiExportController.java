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
 * GenUI 导出入口，负责把前端提交的组件树导出为 PDF 或 DOCX。
 *
 * <p>导出器消费的是通用树结构和模式参数，Controller 只负责提取请求字段、设置
 * 下载文件名与媒体类型，并将参数错误和服务端异常转换为 HTTP 响应。该入口不取代
 * Agent 的 {@code report_tool}，两者分别服务于显式导出请求和 Agent 工具调用。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/genui")
public class AgentGenUiExportController {

    @PostMapping("/export/pdf")
    public ResponseEntity<?> exportPdf(@RequestBody Map<String, Object> body) {
        // PDF 成功响应直接返回字节流；参数错误使用 400，避免调用方把校验失败误判为导出文件。
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
        // DOCX 与 PDF 共用请求结构，但使用独立媒体类型和扩展名，便于浏览器正确下载。
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

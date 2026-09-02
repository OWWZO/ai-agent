package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.application.agent.file.WorkspaceArchiveApplicationService;
import org.wwz.ai.application.agent.visitor.SessionOwnershipDeniedException;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import jakarta.servlet.http.HttpServletResponse;

import javax.annotation.Resource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 会话工作区下载：整包 zip，路径与真实工作区一致。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/workspace")
public class AgentWorkspaceController {

    @Resource
    private WorkspaceArchiveApplicationService workspaceArchiveApplicationService;

    @GetMapping("/{sessionId}/archive")
    public void downloadArchive(@PathVariable("sessionId") String sessionId,
                                HttpServletResponse response) {
        if (!StringUtils.hasText(sessionId)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String safeName = sessionId.replaceAll("[\\\\/:*?\"<>|]", "_");
        String encoded = URLEncoder.encode("workspace-" + safeName + ".zip", StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"workspace.zip\"; filename*=UTF-8''" + encoded);
        try {
            workspaceArchiveApplicationService.writeArchive(
                    VisitorRequestContext.requireVisitorId(),
                    sessionId,
                    response.getOutputStream());
            response.flushBuffer();
        } catch (SessionOwnershipDeniedException e) {
            log.warn("workspace archive denied sessionId={}", sessionId);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } catch (IllegalStateException e) {
            log.warn("workspace archive unavailable sessionId={}: {}", sessionId, e.getMessage());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            log.error("workspace archive failed sessionId={}", sessionId, e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }
}

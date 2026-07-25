package org.wwz.ai.domain.agent.runtime.planmode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 会话计划落盘（对标 cc-haha plans/{slug}.md）。
 * 路径：{workspaceRoot}/.reactor/plan.md
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanArtifactStore {

    public static final String RELATIVE_PLAN_PATH = ".reactor/plan.md";

    private final WorkspaceService workspaceService;

    public Path resolvePlanPath(String sessionId) {
        if (workspaceService == null || !workspaceService.isEnabled()) {
            return null;
        }
        if (StringUtils.isBlank(sessionId)) {
            return null;
        }
        try {
            Path root = workspaceService.resolveAndEnsureRoot(sessionId);
            return root.resolve(RELATIVE_PLAN_PATH).normalize();
        } catch (Exception e) {
            log.warn("resolve plan path failed, sessionId={}", sessionId, e);
            return null;
        }
    }

    public Optional<String> writePlan(String sessionId, String content) {
        Path path = resolvePlanPath(sessionId);
        if (path == null) {
            return Optional.empty();
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8);
            return Optional.of(path.toString());
        } catch (Exception e) {
            log.warn("write plan failed, sessionId={}", sessionId, e);
            return Optional.empty();
        }
    }

    public Optional<String> readPlan(String sessionId) {
        Path path = resolvePlanPath(sessionId);
        if (path == null || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("read plan failed, sessionId={}", sessionId, e);
            return Optional.empty();
        }
    }

    /**
     * 判断绝对路径是否为当前会话的 plan 文件（plan 期唯一允许写入的路径）。
     */
    public boolean isSessionPlanFile(String sessionId, String absolutePath) {
        if (StringUtils.isBlank(absolutePath)) {
            return false;
        }
        Path planPath = resolvePlanPath(sessionId);
        if (planPath == null) {
            return false;
        }
        try {
            Path candidate = Path.of(absolutePath).toAbsolutePath().normalize();
            return planPath.toAbsolutePath().normalize().equals(candidate);
        } catch (Exception e) {
            return false;
        }
    }
}

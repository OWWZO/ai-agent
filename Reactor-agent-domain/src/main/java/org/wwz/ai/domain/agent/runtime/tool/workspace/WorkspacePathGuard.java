package org.wwz.ai.domain.agent.runtime.tool.workspace;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * 保证候选路径落在允许的根目录内，防止 path traversal。
 * 可读根：workspace + skill；可写根：仅 workspace。
 */
@Component
public class WorkspacePathGuard {

    public Path ensureUnderRoot(Path root, Path candidatePath) {
        if (root == null) {
            throw new WorkspaceAccessException("workspace root is not configured");
        }
        return ensureUnderAnyRoot(List.of(root), candidatePath);
    }

    public Path ensureUnderAnyRoot(Collection<Path> roots, Path candidatePath) {
        if (roots == null || roots.isEmpty()) {
            throw new WorkspaceAccessException("no allowed path roots configured");
        }
        if (candidatePath == null) {
            throw new WorkspaceAccessException("path is required");
        }
        Path normalizedCandidate = candidatePath.toAbsolutePath().normalize();
        for (Path root : roots) {
            if (root == null) {
                continue;
            }
            Path normalizedRoot = root.toAbsolutePath().normalize();
            if (normalizedCandidate.startsWith(normalizedRoot)) {
                return normalizedCandidate;
            }
        }
        throw new WorkspaceAccessException("path is outside allowed roots: " + normalizedCandidate);
    }
}

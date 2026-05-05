package org.wwz.ai.domain.agent.runtime.tool.skill;

import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Skill 路径边界校验
 */
@Component
public class SkillPathGuard {

    public Path ensureUnderRoot(Path rootPath, Path candidatePath) {
        Path normalizedRoot = rootPath.toAbsolutePath().normalize();
        Path normalizedCandidate = candidatePath.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new SkillLoadException("path escapes registered skill root: " + normalizedCandidate);
        }
        return normalizedCandidate;
    }
}

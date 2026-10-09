package org.wwz.ai.domain.agent.runtime.tool.skill;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Skill 路径边界校验。
 * <p>
 * 两道防线：<b>词法</b>（normalize + startsWith）与<b>真实路径</b>（toRealPath，
 * 拦截符号链接 / Windows junction 逃逸）。
 */
@Component
public class SkillPathGuard {

    /** Windows 盘符前缀，例如 {@code C:foo}、{@code C:\foo}。 */
    private static final Pattern WINDOWS_DRIVE = Pattern.compile("^[A-Za-z]:.*");

    /** UNC 前缀，例如 {@code //server/share}。 */
    private static final Pattern UNC_PREFIX = Pattern.compile("^//[^/].*");

    public Path ensureUnderRoot(Path rootPath, Path candidatePath) {
        Path normalizedRoot = rootPath.toAbsolutePath().normalize();
        Path normalizedCandidate = candidatePath.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new SkillLoadException("path escapes registered skill root: " + normalizedCandidate);
        }
        // 词法检查通过后，再比对真实路径，防止 root 内放一个指向外部的符号链接。
        Path realRoot = toRealPathOrNull(normalizedRoot);
        Path realCandidate = toRealPathOrNull(normalizedCandidate);
        if (realRoot != null && realCandidate != null && !realCandidate.startsWith(realRoot)) {
            throw new SkillLoadException(
                    "path escapes registered skill root via symlink: " + normalizedCandidate);
        }
        return normalizedCandidate;
    }

    /**
     * 判断相对路径是否包含穿越/绝对路径/特殊前缀。
     * 无法解析的路径（{@link InvalidPathException}）一律视为非法。
     */
    public boolean hasTraversal(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return true;
        }
        String normalized = relativePath.trim().replace('\\', '/');
        if (normalized.startsWith("/")) {
            return true;
        }
        if (normalized.startsWith("~")) {
            return true;
        }
        if (UNC_PREFIX.matcher(normalized).matches()) {
            return true;
        }
        if (WINDOWS_DRIVE.matcher(normalized).matches()) {
            return true;
        }
        if (normalized.contains(":")) {
            return true;
        }
        Path path;
        try {
            path = Path.of(normalized);
        } catch (InvalidPathException e) {
            return true;
        }
        if (path.isAbsolute()) {
            return true;
        }
        for (Path part : path) {
            if ("..".equals(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static Path toRealPathOrNull(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            // 路径不存在等情况：回退到词法检查的结果，不阻断正常流程。
            return null;
        }
    }
}

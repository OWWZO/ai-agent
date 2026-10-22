package org.wwz.ai.application.agent.file;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.authorization.ConversationSessionAuthorizationService;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 将会话工作区打成 zip，供前端「下载全部」使用。
 */
@Service
@RequiredArgsConstructor
public class WorkspaceArchiveApplicationService {

    private final ConversationSessionAuthorizationService conversationSessionAuthorizationService;
    private final WorkspaceService workspaceService;

    public void writeArchive(String userId, String sessionId, OutputStream outputStream) throws IOException {
        if (StringUtils.isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        conversationSessionAuthorizationService.ensureExistingSessionAccessible(userId, sessionId);
        if (workspaceService == null || !workspaceService.isEnabled()) {
            throw new IllegalStateException("工作区未启用");
        }
        Path root = workspaceService.resolveRoot(sessionId);
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            if (!Files.isDirectory(root)) {
                return;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    Path relative = root.relativize(file);
                    String entryName = relative.toString().replace('\\', '/');
                    if (StringUtils.isBlank(entryName) || entryName.contains("..")) {
                        continue;
                    }
                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
        }
    }
}

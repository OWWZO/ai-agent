package org.wwz.ai.domain.agent.runtime.tool.workspace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.adapter.port.FileArtifactPort;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.File;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 将会话上传附件物化到 workspace 本地目录，使 agent 能通过 workspace_* 工具读取。
 * 失败只记日志，不阻断主链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceSessionFileMaterializer {

    private static final long READ_TIMEOUT_SECONDS = 60L;
    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;

    private final WorkspaceService workspaceService;
    private final FileArtifactPort fileArtifactPort;

    public List<String> materialize(AgentContext agentContext, List<FileInformation> sessionFiles) {
        List<String> written = new ArrayList<>();
        if (agentContext == null || !workspaceService.isEnabled()) {
            return written;
        }
        String workspaceRootText = agentContext.getWorkspaceRoot();
        if (StringUtils.isBlank(workspaceRootText)) {
            return written;
        }
        if (sessionFiles == null || sessionFiles.isEmpty()) {
            return written;
        }

        Path workspaceRoot = Path.of(workspaceRootText).toAbsolutePath().normalize();
        try {
            Files.createDirectories(workspaceRoot);
        } catch (Exception e) {
            log.warn("{} create workspace root failed: {}", agentContext.getRequestId(), workspaceRoot, e);
            return written;
        }

        Set<String> usedNames = new HashSet<>();
        for (FileInformation sessionFile : sessionFiles) {
            if (sessionFile == null) {
                continue;
            }
            String fileName = resolveSafeFileName(sessionFile, usedNames);
            if (fileName == null) {
                continue;
            }
            String sourceUrl = firstNonBlank(
                    sessionFile.getOssUrl(),
                    sessionFile.getOriginOssUrl(),
                    sessionFile.getDomainUrl(),
                    sessionFile.getOriginDomainUrl(),
                    sessionFile.getOriginFileUrl()
            );
            if (StringUtils.isBlank(sourceUrl)) {
                log.info("{} skip materialize without url, fileName={}", agentContext.getRequestId(), fileName);
                continue;
            }
            try {
                String content = fileArtifactPort.readText(sourceUrl, READ_TIMEOUT_SECONDS);
                if (content == null) {
                    log.warn("{} materialize empty content, fileName={}, url={}",
                            agentContext.getRequestId(), fileName, sourceUrl);
                    continue;
                }
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_FILE_BYTES) {
                    log.warn("{} materialize skip oversized file, fileName={}, size={}",
                            agentContext.getRequestId(), fileName, bytes.length);
                    continue;
                }
                Path target = workspaceService.resolveAllowedPath(workspaceRoot, fileName);
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(target, bytes);
                written.add(fileName);
                usedNames.add(fileName.toLowerCase(Locale.ROOT));
                log.info("{} materialize session file ok, fileName={}, bytes={}",
                        agentContext.getRequestId(), fileName, bytes.length);
            } catch (Exception e) {
                log.warn("{} materialize session file failed, fileName={}, url={}",
                        agentContext.getRequestId(), fileName, sourceUrl, e);
            }
        }

        // 同步一份到 productFiles 描述，便于后续工具侧感知“本地路径”
        annotateProductFiles(agentContext, written);
        return written;
    }

    private void annotateProductFiles(AgentContext agentContext, List<String> written) {
        if (written.isEmpty() || agentContext.getProductFiles() == null) {
            return;
        }
        Set<String> writtenSet = new HashSet<>();
        for (String name : written) {
            writtenSet.add(name.toLowerCase(Locale.ROOT));
        }
        for (File productFile : agentContext.getProductFiles()) {
            if (productFile == null || StringUtils.isBlank(productFile.getFileName())) {
                continue;
            }
            String baseName = Path.of(productFile.getFileName()).getFileName().toString();
            if (!writtenSet.contains(baseName.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String desc = productFile.getDescription();
            String marker = "workspace:" + baseName;
            if (StringUtils.isBlank(desc)) {
                productFile.setDescription(marker);
            } else if (!desc.contains(marker)) {
                productFile.setDescription(desc + " | " + marker);
            }
        }
    }

    private String resolveSafeFileName(FileInformation sessionFile, Set<String> usedNames) {
        String raw = firstNonBlank(sessionFile.getFileName(), sessionFile.getOriginFileName(), "session-file.txt");
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String baseName = Path.of(raw.replace('\\', '/')).getFileName().toString().trim();
        if (baseName.isEmpty() || ".".equals(baseName) || "..".equals(baseName)) {
            baseName = "session-file.txt";
        }
        baseName = baseName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String candidate = baseName;
        int index = 1;
        while (usedNames.contains(candidate.toLowerCase(Locale.ROOT))) {
            int dot = baseName.lastIndexOf('.');
            if (dot > 0) {
                candidate = baseName.substring(0, dot) + "-" + index + baseName.substring(dot);
            } else {
                candidate = baseName + "-" + index;
            }
            index++;
        }
        return candidate;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }
}

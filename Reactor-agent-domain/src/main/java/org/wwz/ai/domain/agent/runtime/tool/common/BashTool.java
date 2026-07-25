package org.wwz.ai.domain.agent.runtime.tool.common;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.tool.common.shell.AbstractShellTool;

import java.util.List;

/**
 * Bash 工具（对标 cc-haha BashTool 的同步执行能力）。
 * Windows 上优先 Git Bash / WSL bash，否则回退到 cmd。
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class BashTool extends AbstractShellTool {

    public static final String TOOL_NAME = "Bash";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return """
                Executes a given bash command and returns its output.
                Working directory is the session workspace (or process cwd). Shell state does not persist between calls.

                IMPORTANT: Prefer dedicated workspace tools for file ops:
                - File search: workspace_glob (NOT find/ls)
                - Content search: workspace_grep (NOT grep/rg)
                - Read/Edit/Write files: workspace_read / workspace_edit / workspace_write

                Usage notes:
                - command is required
                - Optional timeout in milliseconds (default 120000, max 600000)
                - Quote paths with spaces
                - Chain dependent commands with && ; use ; when later commands should run regardless
                - Do not use interactive flags (-i) or editors that need TTY
                """;
    }

    @Override
    protected String shellLabel() {
        return "Bash";
    }

    @Override
    protected List<String> buildProcessCommand(String command) {
        String os = StringUtils.defaultString(System.getProperty("os.name")).toLowerCase();
        if (os.contains("win")) {
            String bashPath = findWindowsBash();
            if (StringUtils.isNotBlank(bashPath)) {
                return List.of(bashPath, "-lc", command);
            }
            // 无 bash 时用 cmd 兜底，保证 Windows 环境可执行
            return List.of("cmd.exe", "/c", command);
        }
        return List.of("bash", "-lc", command);
    }

    private static String findWindowsBash() {
        String[] absoluteCandidates = {
                "C:\\Program Files\\Git\\bin\\bash.exe",
                "C:\\Program Files\\Git\\usr\\bin\\bash.exe",
                "C:\\Windows\\System32\\bash.exe"
        };
        for (String candidate : absoluteCandidates) {
            if (java.nio.file.Files.isRegularFile(java.nio.file.Path.of(candidate))) {
                return candidate;
            }
        }
        return null;
    }
}

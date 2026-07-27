package org.wwz.ai.domain.agent.runtime.tool.common;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.tool.common.shell.AbstractShellTool;

import java.util.ArrayList;
import java.util.List;

/**
 * PowerShell 工具（对标 cc-haha PowerShellTool 的同步执行能力）。
 * 优先 pwsh，其次 Windows PowerShell 5.1。
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class PowerShellTool extends AbstractShellTool {

    public static final String TOOL_NAME = "PowerShell";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return """
                Executes a given PowerShell command with optional timeout.
                Working directory is the session workspace (or process cwd). Shell state (variables/functions) does not persist.

                IMPORTANT: Prefer dedicated workspace tools for file ops:
                - File search: workspace_glob (NOT Get-ChildItem -Recurse)
                - Content search: workspace_grep (NOT Select-String)
                - Read/Edit/Write files: workspace_read / workspace_edit / workspace_write

                PowerShell notes:
                - Prefer Verb-Noun cmdlets; common aliases: ls, cd, cat, rm
                - Chain with `; if ($?) { B }` on 5.1; `&&` only on pwsh 7+
                - Use -NonInteractive; never Read-Host / Get-Credential / interactive git rebase -i
                - Quote paths with spaces; use call operator for native exe: & "C:\\path\\app.exe" args
                - Optional timeout in milliseconds (default 120000, max 600000)

                CRITICAL - python one-liners (PowerShell eats unquoted args):
                - WRONG: python -c import openpyxl; print(1)
                - WRONG: python -c "import x; print('ok')" nested wrong when outer shell strips quotes
                - RIGHT (prefer here-string):
                  python -c @"
                  import openpyxl
                  import pandas as pd
                  print('ok')
                  "@
                - RIGHT (single-quoted -c for simple code, use double quotes inside Python):
                  python -c 'import openpyxl; import pandas as pd; print("ok")'
                - BETTER: write a .py file with workspace_write then `python script.py` (most reliable)
                - Paths with spaces MUST be quoted: Get-ChildItem -Path "D:\\Java Code\\ai-agent\\..."
                """;
    }

    @Override
    protected String shellLabel() {
        return "PowerShell";
    }

    @Override
    protected List<String> buildProcessCommand(String command) {
        String executable = resolvePowerShellExecutable();
        List<String> args = new ArrayList<>();
        args.add(executable);
        args.add("-NoProfile");
        args.add("-NonInteractive");
        // Windows PowerShell 5.1 用 -Command；pwsh 同样支持
        args.add("-Command");
        args.add(command);
        return args;
    }

    private static String resolvePowerShellExecutable() {
        String override = StringUtils.trimToEmpty(System.getenv("REACTOR_POWERSHELL_PATH"));
        if (StringUtils.isNotBlank(override)) {
            return override;
        }
        String[] candidates = {
                "pwsh",
                "pwsh.exe",
                "powershell",
                "powershell.exe",
                "C:\\Program Files\\PowerShell\\7\\pwsh.exe",
                "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe"
        };
        for (String candidate : candidates) {
            if (candidate.contains("\\") || candidate.contains("/")) {
                if (java.nio.file.Files.isExecutable(java.nio.file.Path.of(candidate))) {
                    return candidate;
                }
            }
        }
        String os = StringUtils.defaultString(System.getProperty("os.name")).toLowerCase();
        return os.contains("win") ? "powershell.exe" : "pwsh";
    }
}

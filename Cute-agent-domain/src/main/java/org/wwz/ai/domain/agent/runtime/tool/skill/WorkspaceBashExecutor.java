package org.wwz.ai.domain.agent.runtime.tool.skill;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 在会话工作区 cwd 下执行 shell 命令（本地进程沙箱）。
 *
 * <p>对齐参考项目 bash 工具语义：非零退出码 / 超时是业务结果（exit_code / timed_out），
 * 不是工具通道错误。真正的隔离（Docker）可在后续替换本实现而不改 BashTool 契约。
 */
@Slf4j
@Component
public class WorkspaceBashExecutor {

    private final SkillRuntimeOptions options;

    public WorkspaceBashExecutor(SkillRuntimeOptions options) {
        this.options = options;
    }

    public ExecResult exec(Path workspaceRoot, String command, long timeoutMs) {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("workspaceRoot is required");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command is required");
        }
        Path cwd = workspaceRoot.toAbsolutePath().normalize();
        try {
            Files.createDirectories(cwd);
        } catch (IOException e) {
            throw new IllegalStateException("failed to ensure workspace: " + cwd, e);
        }

        long effectiveTimeout = timeoutMs > 0
                ? Math.min(timeoutMs, options.getBashMaxTimeoutSec() * 1000L)
                : options.getBashTimeoutSec() * 1000L;
        if (effectiveTimeout <= 0) {
            effectiveTimeout = 120_000L;
        }

        List<String> argv = buildShellArgv(command);
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(false);
        Map<String, String> env = pb.environment();
        env.put("SKILL_WORKSPACE", cwd.toString());
        env.put("PYTHON", options.getRuntimePython() == null ? "python" : options.getRuntimePython());

        long started = System.currentTimeMillis();
        Process process = null;
        try {
            process = pb.start();
            StreamCollector stdout = new StreamCollector(process.getInputStream(), options.getBashOutputMaxChars());
            StreamCollector stderr = new StreamCollector(process.getErrorStream(), options.getBashOutputMaxChars());
            Thread outThread = new Thread(stdout, "bash-stdout");
            Thread errThread = new Thread(stderr, "bash-stderr");
            outThread.setDaemon(true);
            errThread.setDaemon(true);
            outThread.start();
            errThread.start();

            boolean finished = process.waitFor(effectiveTimeout, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                outThread.join(2_000);
                errThread.join(2_000);
                return ExecResult.builder()
                        .exitCode(null)
                        .stdout(stdout.text())
                        .stderr(merge(stderr.text(), "execution timed out after " + (effectiveTimeout / 1000) + "s"))
                        .truncated(stdout.truncated() || stderr.truncated())
                        .timedOut(true)
                        .durationMs(System.currentTimeMillis() - started)
                        .build();
            }
            outThread.join(2_000);
            errThread.join(2_000);
            int code = process.exitValue();
            return ExecResult.builder()
                    .exitCode(code)
                    .stdout(stdout.text())
                    .stderr(stderr.text())
                    .truncated(stdout.truncated() || stderr.truncated())
                    .timedOut(false)
                    .durationMs(System.currentTimeMillis() - started)
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            throw new IllegalStateException("bash interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("bash start failed: " + e.getMessage(), e);
        }
    }

    private static List<String> buildShellArgv(String command) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> argv = new ArrayList<>();
        if (os.contains("win")) {
            // Windows：优先 PowerShell（系统自带）；命令字符串按 bash 语义交给模型，脚本侧多用 python/node
            argv.add("powershell.exe");
            argv.add("-NoProfile");
            argv.add("-NonInteractive");
            argv.add("-Command");
            argv.add(command);
        } else {
            argv.add("bash");
            argv.add("-lc");
            argv.add(command);
        }
        return argv;
    }

    private static String merge(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        if (b == null || b.isBlank()) {
            return a;
        }
        return a + "\n" + b;
    }

    @Value
    @Builder
    public static class ExecResult {
        Integer exitCode;
        String stdout;
        String stderr;
        boolean truncated;
        boolean timedOut;
        long durationMs;
    }

    private static final class StreamCollector implements Runnable {
        private final InputStream in;
        private final int maxChars;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private volatile boolean truncated;

        StreamCollector(InputStream in, int maxChars) {
            this.in = in;
            this.maxChars = Math.max(4_096, maxChars);
        }

        @Override
        public void run() {
            byte[] chunk = new byte[4096];
            int maxBytes = maxChars * 4;
            try {
                int n;
                while ((n = in.read(chunk)) >= 0) {
                    if (buffer.size() >= maxBytes) {
                        truncated = true;
                        // drain remainder
                        while (in.read(chunk) >= 0) {
                            // discard
                        }
                        break;
                    }
                    int toWrite = Math.min(n, maxBytes - buffer.size());
                    buffer.write(chunk, 0, toWrite);
                    if (toWrite < n) {
                        truncated = true;
                    }
                }
            } catch (IOException ignored) {
                // process closed streams
            }
        }

        String text() {
            byte[] bytes = buffer.toByteArray();
            Charset charset = StandardCharsets.UTF_8;
            String raw = new String(bytes, charset);
            if (raw.length() <= maxChars) {
                return raw;
            }
            truncated = true;
            int head = maxChars / 2;
            int tail = maxChars - head - 32;
            if (tail < 0) {
                return raw.substring(0, maxChars);
            }
            return raw.substring(0, head) + "\n…[输出被截断]…\n" + raw.substring(raw.length() - tail);
        }

        boolean truncated() {
            return truncated;
        }
    }
}

package org.wwz.ai.domain.agent.runtime.tool.common.shell;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 本地进程执行器（对标 cc-haha Shell.exec 的最小同步能力）。
 */
public final class ShellProcessExecutor {

    private static final int MAX_CAPTURE_BYTES = 512 * 1024;

    private ShellProcessExecutor() {
    }

    public static ShellExecResult execute(List<String> command,
                                          Path workingDirectory,
                                          long timeoutMs) throws IOException, InterruptedException {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command is required");
        }
        long effectiveTimeout = timeoutMs <= 0 ? 120_000L : timeoutMs;
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDirectory != null && Files.isDirectory(workingDirectory)) {
            builder.directory(workingDirectory.toFile());
        }
        builder.redirectErrorStream(false);

        long start = System.currentTimeMillis();
        Process process = builder.start();
        StreamCollector stdoutCollector = new StreamCollector(process.getInputStream());
        StreamCollector stderrCollector = new StreamCollector(process.getErrorStream());
        Thread stdoutThread = new Thread(stdoutCollector, "shell-stdout");
        Thread stderrThread = new Thread(stderrCollector, "shell-stderr");
        stdoutThread.setDaemon(true);
        stderrThread.setDaemon(true);
        stdoutThread.start();
        stderrThread.start();

        boolean finished = process.waitFor(effectiveTimeout, TimeUnit.MILLISECONDS);
        boolean timedOut = !finished;
        if (timedOut) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        stdoutThread.join(2_000L);
        stderrThread.join(2_000L);

        int exitCode = timedOut ? -1 : process.exitValue();
        Charset charset = resolveCharset();
        return ShellExecResult.builder()
                .stdout(stdoutCollector.asString(charset))
                .stderr(stderrCollector.asString(charset))
                .exitCode(exitCode)
                .timedOut(timedOut)
                .durationMs(System.currentTimeMillis() - start)
                .build();
    }

    private static Charset resolveCharset() {
        String os = StringUtils.defaultString(System.getProperty("os.name")).toLowerCase();
        if (os.contains("win")) {
            return Charset.forName("GBK");
        }
        return StandardCharsets.UTF_8;
    }

    private static final class StreamCollector implements Runnable {
        private final InputStream inputStream;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private volatile boolean truncated;

        private StreamCollector(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[4096];
            try {
                int read;
                while ((read = inputStream.read(chunk)) >= 0) {
                    if (buffer.size() >= MAX_CAPTURE_BYTES) {
                        truncated = true;
                        continue;
                    }
                    int remain = MAX_CAPTURE_BYTES - buffer.size();
                    buffer.write(chunk, 0, Math.min(read, remain));
                    if (read > remain) {
                        truncated = true;
                    }
                }
            } catch (IOException ignored) {
                // process closed stream
            }
        }

        private String asString(Charset charset) {
            String text = buffer.toString(charset);
            if (truncated) {
                return text + "\n...[output truncated]";
            }
            return text;
        }
    }
}

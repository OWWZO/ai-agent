package org.wwz.ai.domain.agent.runtime.tool.workspace;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * 以有界内存读取一行，避免异常大的单行在工具进程中形成无界 String。
 */
final class WorkspaceTextReader {

    private WorkspaceTextReader() {
    }

    static Line readLine(BufferedReader reader, int maxChars) throws IOException {
        return readLine(reader, maxChars, false);
    }

    /**
     * 读取一行的有界前缀；调用方在 truncated 时会停止读取当前文件，因此不再消费剩余长行。
     */
    static Line readLineBounded(BufferedReader reader, int maxChars) throws IOException {
        return readLine(reader, maxChars, true);
    }

    private static Line readLine(BufferedReader reader, int maxChars, boolean stopAtLimit) throws IOException {
        StringBuilder line = new StringBuilder(Math.min(maxChars, 256));
        boolean consumed = false;
        boolean truncated = false;
        while (true) {
            int value = reader.read();
            if (value < 0) {
                return consumed ? new Line(line.toString(), true, truncated, line.length()) : null;
            }
            consumed = true;
            if (value == '\n' || value == '\r') {
                if (value == '\r') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next != '\n' && next >= 0) {
                        reader.reset();
                    }
                }
                return new Line(line.toString(), false, truncated, line.length());
            }
            if (line.length() < maxChars) {
                line.append((char) value);
            } else {
                truncated = true;
                if (stopAtLimit) {
                    return new Line(line.toString(), false, true, line.length());
                }
            }
        }
    }

    record Line(String text, boolean eof, boolean truncated, int characters) {
    }
}

package org.wwz.ai.domain.agent.runtime.tool.workspace;

import lombok.Builder;
import lombok.Value;

/**
 * 对齐 cchaha readFileState：记录某次 workspace_read 的 range / mtime / 内容指纹。
 * 跨轮只持久化元数据（不含全文）。
 */
@Value
@Builder(toBuilder = true)
public class WorkspaceFileReadState {

    String absolutePath;

    /**
     * 读取时的文件 mtime（毫秒）。
     */
    long mtimeMs;

    /**
     * 起始行（与 workspace_read 参数一致）。
     */
    int startLine;

    /**
     * 行数上限。
     */
    int lineCount;

    /**
     * 文件全文 SHA-256（hex），用于跨轮/mtime 抖动时的内容比对。
     */
    String contentHash;
}

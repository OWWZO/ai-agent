package org.wwz.ai.domain.agent.runtime.util;


import org.wwz.ai.domain.agent.runtime.dto.File;

import java.util.List;
import java.util.Objects;

/**
 * Agent 文件信息格式化工具。
 * <p>用于把可见产物转换成提示词文本，内部文件可按调用方要求过滤。</p>
 */
public class FileUtil {

    /**
     * 格式化文件信息
     *
     * @param files
     * @return
     */
    public static String formatFileInfo(List<File> files, Boolean filterInternalFile) {
        StringBuilder stringBuilder = new StringBuilder();
        for (File file : files) {
            if (filterInternalFile && Boolean.TRUE.equals(file.getIsInternalFile())) {
                continue;
            }
            stringBuilder.append(String.format("fileName:%s fileDesc:%s fileUrl:%s\n",
                    file.getFileName(), file.getDescription(),
                    Objects.nonNull(file.getOriginOssUrl()) && !file.getOriginOssUrl().isEmpty() ? file.getOriginOssUrl() : file.getOssUrl()));
        }
        return stringBuilder.toString();
    }

    /**
     * 将本轮可用文件清单包装为 user 消息围栏（不进 system，避免破坏 prompt cache）。
     * description 含 {@code workspace:文件名} 时表示已物化到会话工作区。
     */
    public static String formatAvailableFilesUserBlock(List<File> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        String body = formatFileInfo(files, true).trim();
        if (body.isEmpty()) {
            return "";
        }
        return "<user_uploaded_files>\n"
                + "本轮用户上传/可用的文件如下（已尝试物化到会话工作区；"
                + "可用 workspace_list / workspace_read 读取，或按类型调用 docread / 相关工具）：\n"
                + body
                + "\n</user_uploaded_files>";
    }
}

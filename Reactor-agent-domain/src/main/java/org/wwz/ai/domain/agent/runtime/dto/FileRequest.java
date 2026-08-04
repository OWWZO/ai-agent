package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件工具请求模型，承载文件操作参数和工作区上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileRequest {
    private String requestId;
    private String fileName;
    private String description;
    private String content;
    /** 本地已落盘绝对路径；register 时使用，upload 可忽略 */
    private String localPath;
}

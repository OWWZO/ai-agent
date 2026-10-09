package org.wwz.ai.domain.agent.runtime.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent runtime 使用的稳定文件引用。
 * <p>HTTP 上传字段在进入 runtime 前由 Trigger/Case mapper 转换为该模型。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionFile {

    private String fileName;
    private String fileDesc;
    private String ossUrl;
    private String domainUrl;
    private Integer fileSize;
    private String fileType;
    private String resourceKey;
    private String mimeType;
    private String originFileName;
    private String originFileUrl;
    private String originOssUrl;
    private String originDomainUrl;
}

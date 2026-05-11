package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class File {
    private String ossUrl;
    private String domainUrl;
    private String fileName;
    private Integer fileSize;
    private String description;
    private String originFileName;
    private String originOssUrl;
    private String originDomainUrl;
    private Boolean isInternalFile;
}

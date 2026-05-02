package org.wwz.ai.domain.agent.reactor.model.imagegeneration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 生图工作台生成命令。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceImageGenerationCommand {
    private String requestId;
    private String prompt;
    private String mode;
    private List<String> fileNames;
    private List<String> maskFileNames;
    private String fileName;
    private String fileDescription;
    private String model;
    private String size;
    private Integer n;
}

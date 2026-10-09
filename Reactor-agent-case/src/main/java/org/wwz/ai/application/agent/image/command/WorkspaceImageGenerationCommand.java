package org.wwz.ai.application.agent.image.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工作台生图用例输入。
 * <p>由 Trigger 从 HTTP 请求 VO 映射而来，隔离 HTTP 契约与领域命令。</p>
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
    private String size;
    private Integer n;
}

package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * canvas_publish 工具的终态结构化输出。
 *
 * 它只承载 ledger 和历史回放需要的稳定文件引用，不保存 HTML 内容本身；文件内容
 * 仍由 artifact 记录和文件服务负责管理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CanvasPublishToolOutput implements ToolStructuredOutput {

    private String title;

    private String mode;

    private String primaryFileName;

    private String previewUrl;

    private String downloadUrl;

    private Boolean openInPanel;

    private Boolean salvaged;

    @Builder.Default
    private List<ToolFileRef> fileRefs = new ArrayList<>();

    @Override
    public String getToolName() {
        return ToolOutputNames.CANVAS_PUBLISH;
    }
}

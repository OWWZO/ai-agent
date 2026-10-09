package org.wwz.ai.application.agent.image.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作台生图历史分页用例输入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceImageHistoryQuery {

    private int pageNo;
    private int pageSize;
}

package org.wwz.ai.application.agent.image.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工作台生图历史分页用例输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceImageHistoryPageView {

    private int total;
    private List<WorkspaceImageHistoryBatchView> list;
}

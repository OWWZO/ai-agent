package org.wwz.ai.application.agent.image;

import org.wwz.ai.application.agent.image.command.WorkspaceImageGenerationCommand;
import org.wwz.ai.application.agent.image.command.WorkspaceImageHistoryQuery;
import org.wwz.ai.application.agent.image.result.WorkspaceImageGenerationView;
import org.wwz.ai.application.agent.image.result.WorkspaceImageHistoryPageView;

/**
 * 生图工作台用例入口契约。
 */
public interface IWorkspaceImageGenerationApplicationService {

    /**
     * 发起一次生图工作台请求。
     */
    WorkspaceImageGenerationView generate(WorkspaceImageGenerationCommand command);

    /**
     * 分页查询工作台生图历史。
     */
    WorkspaceImageHistoryPageView queryHistory(WorkspaceImageHistoryQuery query);
}

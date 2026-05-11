package org.wwz.ai.domain.agent.reactor.service;

import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationCommand;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationHistoryPage;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationResult;

/**
 * 生图工作台服务。
 */
public interface IWorkspaceImageGenerationService {

    /**
     * 发起一次生图工作台请求。
     */
    WorkspaceImageGenerationResult generate(WorkspaceImageGenerationCommand command);

    /**
     * 分页查询工作台生图历史。
     */
    WorkspaceImageGenerationHistoryPage queryHistory(int pageNo, int pageSize);
}

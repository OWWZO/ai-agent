package org.wwz.ai.application.agent.image;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.image.command.WorkspaceImageGenerationCommand;
import org.wwz.ai.application.agent.image.command.WorkspaceImageHistoryQuery;
import org.wwz.ai.application.agent.image.mapper.WorkspaceImageMapper;
import org.wwz.ai.application.agent.image.result.WorkspaceImageGenerationView;
import org.wwz.ai.application.agent.image.result.WorkspaceImageHistoryPageView;
import org.wwz.ai.domain.agent.image.model.WorkspaceImageGenerationHistoryPage;
import org.wwz.ai.domain.agent.image.model.WorkspaceImageGenerationResult;
import org.wwz.ai.domain.agent.image.service.IWorkspaceImageGenerationService;

/**
 * 生图工作台应用服务。
 * <p>负责用例模型与领域模型之间的转换，并把生图编排委托给领域服务；Trigger 不直接依赖领域生图契约。</p>
 */
@Service
public class WorkspaceImageGenerationApplicationService implements IWorkspaceImageGenerationApplicationService {

    @Resource
    private IWorkspaceImageGenerationService workspaceImageGenerationService;

    @Override
    public WorkspaceImageGenerationView generate(WorkspaceImageGenerationCommand command) {
        WorkspaceImageGenerationResult result = workspaceImageGenerationService.generate(
                WorkspaceImageMapper.toDomainCommand(command)
        );
        return WorkspaceImageMapper.toGenerationView(result);
    }

    @Override
    public WorkspaceImageHistoryPageView queryHistory(WorkspaceImageHistoryQuery query) {
        int pageNo = query == null ? 1 : query.getPageNo();
        int pageSize = query == null ? 10 : query.getPageSize();
        WorkspaceImageGenerationHistoryPage page = workspaceImageGenerationService.queryHistory(pageNo, pageSize);
        return WorkspaceImageMapper.toHistoryPageView(page);
    }
}

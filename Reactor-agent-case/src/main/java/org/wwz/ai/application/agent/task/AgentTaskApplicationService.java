package org.wwz.ai.application.agent.task;

import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.repository.IAgentRepository;
import org.wwz.ai.domain.agent.model.valobj.AiAgentTaskScheduleVO;

import javax.annotation.Resource;
import java.util.List;

/**
 * Agent 应用层任务服务。
 */
@Service
public class AgentTaskApplicationService implements ITaskService {

    @Resource
    private IAgentRepository repository;

    @Override
    public List<AiAgentTaskScheduleVO> queryAllValidTaskSchedule() {
        return repository.queryAllValidTaskSchedule();
    }

    @Override
    public List<Long> queryAllInvalidTaskScheduleIds() {
        return repository.queryAllInvalidTaskScheduleIds();
    }
}

package org.wwz.ai.types.job.provider;

import org.wwz.ai.types.job.model.TaskScheduleVO;

import java.util.List;

/**
 * 任务调度数据提供者 SPI。
 *
 * <p>实现层只负责从自己的存储读取有效任务和失效任务 ID，不直接操作调度线程；
 * {@link org.wwz.ai.types.job.service.ITaskJobService} 负责把这些数据映射为运行中的计划任务。</p>
 */
public interface ITaskDataProvider {

    /**
     * 查询所有有效的任务调度配置
     * @return 任务调度配置列表
     */
    List<TaskScheduleVO> queryAllValidTaskSchedule();

    /**
     * 查询所有无效的任务ID
     * @return 无效任务ID列表
     */
    List<Long> queryAllInvalidTaskScheduleIds();

}

package org.wwz.ai.types.job.service;

import org.wwz.ai.types.job.model.TaskScheduleVO;

/**
 * 任务调度运行时服务契约。
 *
 * <p>服务维护“配置任务”到“ScheduledFuture”的映射；刷新负责增删改同步，清理负责移除数据源已标记失效的任务，
 * stopAllTasks 则用于应用关闭时释放调度资源。</p>
 */
public interface ITaskJobService {

    /**
     * 添加单个任务
     * @param task 任务调度配置
     * @return 是否添加成功
     */
    boolean addTask(TaskScheduleVO task);

    /**
     * 移除单个任务
     * @param taskId 任务ID
     * @return 是否移除成功
     */
    boolean removeTask(Long taskId);

    /**
     * 刷新任务调度配置
     */
    void refreshTasks();

    /**
     * 清理无效任务
     */
    void cleanInvalidTasks();

    /**
     * 停止所有任务
     */
    void stopAllTasks();

    /**
     * 获取当前活跃任务数量
     * @return 活跃任务数量
     */
    int getActiveTaskCount();

    /**
     * 初始化任务调度配置
     * 在服务启动时加载所有有效的任务调度配置
     */
    void initializeTasks();

}

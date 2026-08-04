package org.wwz.ai.types.job;

import org.wwz.ai.types.job.config.TaskJobAutoProperties;
import org.wwz.ai.types.job.service.ITaskJobService;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 任务调度作业
 * 定时获取有效的任务调度配置，并动态创建新的任务
 *
 */
public class TaskJob {

    private final TaskJobAutoProperties properties;
    private final ITaskJobService taskJobService;

    public TaskJob(TaskJobAutoProperties properties, ITaskJobService taskJobService) {
        this.properties = properties;
        this.taskJobService = taskJobService;
    }

    /**
     * 定时刷新任务调度配置
     */
    @Scheduled(fixedRateString = "${xfg.wrench.task.job.refresh-interval:60000}")
    public void refreshTasks() {
        // 刷新是配置到运行时任务的同步边界：关闭开关时不触碰现有调度器，开启后由
        // service 负责增量创建/更新，避免定时器自身持有任务状态。
        if (!properties.isEnabled()) {
            return;
        }
        taskJobService.refreshTasks();
    }

    /**
     * 定时清理无效任务
     */
    @Scheduled(cron = "${xfg.wrench.task.job.clean-invalid-tasks-cron:0 0/10 * * * ?}")
    public void cleanInvalidTasks() {
        // 清理与刷新分开调度，先由配置开关短路；具体失效判定和取消动作留在 service，
        // 保持 Job 只负责触发时机，不承担任务生命周期管理。
        if (!properties.isEnabled()) {
            return;
        }
        taskJobService.cleanInvalidTasks();
    }

}

package org.wwz.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.domain.agent.ledger.reconcile.BackgroundRunReconciler;
import org.wwz.ai.domain.agent.ledger.reconcile.IExecutionLedgerReconcileRepository;
import org.wwz.ai.domain.agent.runtime.tasklist.TasklistPersistencePort;

import javax.annotation.Resource;

/**
 * 启动完成后对账一次运行态孤儿（kill -9 / OOM / 滚动发布留下的「账本说在跑、世上已无人执行」）。
 * <p>
 * 只跑一次、失败只记日志不阻断启动。多实例 / 滚动发布部署必须把
 * {@code autobots.autoagent.background.reconcile-on-start} 置为 false：
 * 新实例的启动对账会把老实例正在跑的任务判成孤儿（判据是「本 JVM 无执行体 + 早于启动时刻」）。
 * 当前 SessionBackgroundTaskHub 本就是单实例内存设计，该约束与现状一致。
 */
@Slf4j
@Configuration
public class BackgroundRunReconcileConfiguration implements ApplicationListener<ApplicationReadyEvent> {

    @Resource
    private IExecutionLedgerReconcileRepository reconcileRepository;

    @Resource
    private TasklistPersistencePort tasklistPersistence;

    /**
     * 是否启用启动对账。单实例默认开启；多实例部署置 false（见类注释）。
     */
    @Value("${autobots.autoagent.background.reconcile-on-start:true}")
    private boolean reconcileOnStart;

    /**
     * 宽限窗：只扫 started_at 早于「启动时刻 - 宽限窗」的行，容忍上一实例 graceful shutdown 拖尾。
     */
    @Value("${autobots.autoagent.background.reconcile-grace-ms:60000}")
    private long reconcileGraceMs;

    /**
     * 单次扫描分页大小。
     */
    @Value("${autobots.autoagent.background.reconcile-batch-size:500}")
    private int reconcileBatchSize;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!reconcileOnStart) {
            log.info("reconcile.skip 启动对账已关闭（reconcile-on-start=false）");
            return;
        }
        try {
            BackgroundRunReconciler reconciler = new BackgroundRunReconciler(
                    reconcileRepository, tasklistPersistence, reconcileGraceMs, reconcileBatchSize);
            BackgroundRunReconciler.Report report = reconciler.reconcile(null);
            log.info("reconcile.done runs={} tools={} tasks={} errors={} durationMs={}",
                    report.getRunConverged(), report.getToolConverged(), report.getTaskConverged(),
                    report.getErrors().size(),
                    java.time.Duration.between(report.getStartedAt(), report.getFinishedAt()).toMillis());
        } catch (Exception e) {
            // 对账失败不能阻断启动：最坏情况回到「有僵尸」的现状。
            log.error("reconcile.failed 启动对账异常，已跳过", e);
        }
    }
}

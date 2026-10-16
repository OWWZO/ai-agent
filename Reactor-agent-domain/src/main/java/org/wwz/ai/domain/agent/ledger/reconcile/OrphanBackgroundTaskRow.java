package org.wwz.ai.domain.agent.ledger.reconcile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTask;

/**
 * 扫描出的后台任务孤儿行。
 * <p>
 * 后台任务登记行按 session 归属，收敛时 upsert 必须带 sessionId，故扫描结果不能只给任务本身。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrphanBackgroundTaskRow {

    private String sessionId;

    private RuntimeBackgroundTask task;
}

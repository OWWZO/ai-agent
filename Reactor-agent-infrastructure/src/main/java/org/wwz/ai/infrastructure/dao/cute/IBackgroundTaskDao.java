package org.wwz.ai.infrastructure.dao.cute;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.infrastructure.dao.cute.po.BackgroundTaskPO;

import java.util.List;

@Mapper
public interface IBackgroundTaskDao {

    List<BackgroundTaskPO> selectBySessionId(@Param("sessionId") String sessionId);

    BackgroundTaskPO selectBySessionAndTaskId(@Param("sessionId") String sessionId,
                                              @Param("taskId") String taskId);

    int upsert(BackgroundTaskPO row);

    /**
     * 启动对账：扫「仍 running 且早于宽限窗之前启动」的后台任务登记行。
     * <p>
     * 这些行的执行体（子 Agent Future / shell 进程）随进程消亡，永远不会有终态回调。
     * 注意 {@link org.wwz.ai.domain.agent.runtime.tasklist.RuntimeBackgroundTaskRegistry#hydrate()}
     * 也有同类收敛（懒收敛，会话被再次访问时把无 Future 的 running 判为 failed）；
     * 本查询用于启动时**提前**收敛，并以更准确的 {@code lost} 语义落库。
     */
    List<BackgroundTaskPO> selectOrphanedRunning(@Param("startedBeforeMs") long startedBeforeMs,
                                                 @Param("limit") int limit);
}

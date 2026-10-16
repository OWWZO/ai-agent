package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.ledger.entity.ToolInvocation;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;

import java.util.List;

/**
 * 工具调用账本 DAO。
 */
@Mapper
public interface IToolInvocationLedgerDao {

    int insertToolInvocation(ToolInvocation invocation);

    int updateToolInvocationFinish(ToolInvocation invocation);

    List<ToolInvocation> queryByRunId(@Param("runId") Long runId);

    List<ToolInvocation> queryByRunIds(@Param("runIds") List<Long> runIds);

    List<ToolInvocation> queryByLlmInvocationIds(@Param("llmInvocationIds") List<Long> llmInvocationIds);

    List<ToolInvocationView> queryRecentByToolName(@Param("toolName") String toolName,
                                                   @Param("limit") int limit);

    /**
     * 启动对账：扫「父 run 已终态、自身仍 running 且未结束」的工具孤儿。
     * <p>
     * 典型来源是 {@code run_in_background} 的后台子 Agent：父回合已结束并落终态，
     * 但 {@code persistLedgerSettlement} 因进程被杀从未被调到，工具行永远 running。
     * 父 run 仍 RUNNING 的工具不在此列（可能只是本轮尚未收尾），避免误伤。
     */
    List<ToolInvocation> queryOrphanedRunningToolInvocations(@Param("startedBefore") java.time.LocalDateTime startedBefore,
                                                             @Param("limit") int limit);
}

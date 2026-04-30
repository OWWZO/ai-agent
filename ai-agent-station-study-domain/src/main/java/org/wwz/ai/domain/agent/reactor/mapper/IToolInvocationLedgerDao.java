package org.wwz.ai.domain.agent.reactor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.reactor.entity.ToolInvocation;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;

import java.util.List;

/**
 * 工具调用账本 DAO。
 */
@Mapper
public interface IToolInvocationLedgerDao {

    int insertToolInvocation(ToolInvocation invocation);

    int updateToolInvocationFinish(ToolInvocation invocation);

    List<ToolInvocation> queryByRunId(@Param("runId") Long runId);

    List<ToolInvocationView> queryRecentByToolName(@Param("toolName") String toolName,
                                                   @Param("limit") int limit);
}

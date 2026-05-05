package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.ledger.entity.LlmInvocation;

import java.util.List;

/**
 * LLM 调用账本 DAO。
 */
@Mapper
public interface ILlmInvocationLedgerDao {

    int insertLlmInvocation(LlmInvocation invocation);

    int updateLlmInvocationFinish(LlmInvocation invocation);

    List<LlmInvocation> queryByRunId(@Param("runId") Long runId);

    List<LlmInvocation> queryByRunIds(@Param("runIds") List<Long> runIds);
}

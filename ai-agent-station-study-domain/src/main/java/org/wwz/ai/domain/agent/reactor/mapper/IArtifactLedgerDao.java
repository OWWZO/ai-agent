package org.wwz.ai.domain.agent.reactor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.reactor.entity.ArtifactRecord;

import java.util.List;

/**
 * 产物账本 DAO。
 */
@Mapper
public interface IArtifactLedgerDao {

    int batchInsertArtifacts(@Param("records") List<ArtifactRecord> records);

    List<ArtifactRecord> queryByRunId(@Param("runId") Long runId);

    List<ArtifactRecord> queryByRunIds(@Param("runIds") List<Long> runIds);
}

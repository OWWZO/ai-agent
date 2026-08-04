package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryEntry;

import java.util.List;

@Mapper
public interface ILtmCuratedEntryDao {

    int insert(CuratedMemoryEntry entry);

    int softDeleteById(@Param("id") Long id);

    int updateContent(@Param("id") Long id,
                      @Param("content") String content,
                      @Param("sourceSessionId") String sourceSessionId,
                      @Param("sourceRequestId") String sourceRequestId,
                      @Param("writeOrigin") String writeOrigin);

    List<CuratedMemoryEntry> selectActive(@Param("ownerType") String ownerType,
                                          @Param("ownerId") String ownerId,
                                          @Param("scope") String scope);
}

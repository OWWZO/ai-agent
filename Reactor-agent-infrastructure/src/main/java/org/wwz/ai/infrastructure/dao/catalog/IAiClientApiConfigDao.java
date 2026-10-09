package org.wwz.ai.infrastructure.dao.catalog;

import org.apache.ibatis.annotations.Mapper;
import org.wwz.ai.infrastructure.dao.po.catalog.AiClientApiPO;

import java.util.List;

/**
 * Catalog 上下文 API 配置 DAO。
 */
@Mapper
public interface IAiClientApiConfigDao {

    int insert(AiClientApiPO po);

    int updateById(AiClientApiPO po);

    int updateByApiId(AiClientApiPO po);

    int deleteById(Long id);

    int deleteByApiId(String apiId);

    AiClientApiPO queryById(Long id);

    AiClientApiPO queryByApiId(String apiId);

    List<AiClientApiPO> queryEnabled();

    List<AiClientApiPO> queryAll();
}

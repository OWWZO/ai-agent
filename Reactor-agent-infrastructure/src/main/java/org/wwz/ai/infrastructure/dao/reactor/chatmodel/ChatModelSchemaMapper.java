package org.wwz.ai.infrastructure.dao.reactor.chatmodel;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.infrastructure.dao.po.reactor.chatmodel.ChatModelSchemaPO;

/**
 * 问数模型字段 DAO。
 */
@Mapper
public interface ChatModelSchemaMapper extends BaseMapper<ChatModelSchemaPO> {

    /**
     * 物理删除模型 schema 历史版本，避免逻辑删除导致重复历史行持续累计。
     */
    @Delete("DELETE FROM chat_model_schema WHERE model_code = #{modelCode}")
    int deletePhysicalByModelCode(@Param("modelCode") String modelCode);
}

package org.wwz.ai.infrastructure.dao.reactor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.ledger.entity.ChatModelInfo;

@Mapper
public interface ChatModelInfoMapper extends BaseMapper<ChatModelInfo> {

    /**
     * DataAgent force-refresh 需要真正清空旧版本元数据，避免逻辑删除历史越积越多。
     */
    @Delete("DELETE FROM chat_model_info WHERE code = #{modelCode}")
    int deletePhysicalByCode(@Param("modelCode") String modelCode);
}

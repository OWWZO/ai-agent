package org.wwz.ai.domain.agent.reactor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.wwz.ai.domain.agent.reactor.entity.ChatModelInfo;

@Mapper
public interface ChatModelInfoMapper extends BaseMapper<ChatModelInfo> {
}

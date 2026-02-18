package org.wwz.ai.domain.agent.genie.mapper;

import org.apache.ibatis.annotations.*;
import org.wwz.ai.domain.agent.genie.entity.ChatModelInfo;

import java.util.List;

@Mapper
public interface ChatModelInfoMapper {

    @Select("SELECT id, code, type, content, name, use_prompt AS usePrompt, business_prompt AS businessPrompt, yn FROM chat_model_info WHERE yn = 0")
    @Results(id = "ChatModelInfoMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "code", property = "code"),
            @Result(column = "type", property = "type"),
            @Result(column = "content", property = "content"),
            @Result(column = "name", property = "name"),
            @Result(column = "usePrompt", property = "usePrompt"),
            @Result(column = "businessPrompt", property = "businessPrompt"),
            @Result(column = "yn", property = "yn")
    })
    List<ChatModelInfo> selectAll();

    @Select("SELECT id, code, type, content, name, use_prompt AS usePrompt, business_prompt AS businessPrompt, yn FROM chat_model_info WHERE code = #{code} AND yn = 0 LIMIT 1")
    @ResultMap("ChatModelInfoMap")
    ChatModelInfo selectByCode(@Param("code") String code);

    @Insert("INSERT INTO chat_model_info(code, type, content, name, use_prompt, business_prompt, yn) VALUES(#{code}, #{type}, #{content}, #{name}, #{usePrompt}, #{businessPrompt}, 0)")
    int insert(ChatModelInfo info);
}

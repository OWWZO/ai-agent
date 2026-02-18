package org.wwz.ai.domain.agent.genie.mapper;

import org.apache.ibatis.annotations.*;
import org.wwz.ai.domain.agent.genie.entity.ChatModelSchema;

import java.util.List;

@Mapper
public interface ChatModelSchemaMapper {

    @Select("SELECT id, model_code AS modelCode, column_id AS columnId, column_name AS columnName, column_comment AS columnComment, few_shot AS fewShot, data_type AS dataType, synonyms, vector_uuid AS vectorUuid, default_recall AS defaultRecall, analyze_suggest AS analyzeSuggest, yn FROM chat_model_schema WHERE yn = 0")
    @Results(id = "ChatModelSchemaMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "modelCode", property = "modelCode"),
            @Result(column = "columnId", property = "columnId"),
            @Result(column = "columnName", property = "columnName"),
            @Result(column = "columnComment", property = "columnComment"),
            @Result(column = "fewShot", property = "fewShot"),
            @Result(column = "dataType", property = "dataType"),
            @Result(column = "synonyms", property = "synonyms"),
            @Result(column = "vectorUuid", property = "vectorUuid"),
            @Result(column = "defaultRecall", property = "defaultRecall"),
            @Result(column = "analyzeSuggest", property = "analyzeSuggest"),
            @Result(column = "yn", property = "yn")
    })
    List<ChatModelSchema> selectAll();

    @Select("SELECT id, model_code AS modelCode, column_id AS columnId, column_name AS columnName, column_comment AS columnComment, few_shot AS fewShot, data_type AS dataType, synonyms, vector_uuid AS vectorUuid, default_recall AS defaultRecall, analyze_suggest AS analyzeSuggest, yn FROM chat_model_schema WHERE default_recall = 1 AND yn = 0")
    @ResultMap("ChatModelSchemaMap")
    List<ChatModelSchema> selectDefaultRecall();

    @Insert("INSERT INTO chat_model_schema(model_code, column_id, column_name, column_comment, few_shot, data_type, synonyms, vector_uuid, default_recall, analyze_suggest, yn) VALUES(#{modelCode}, #{columnId}, #{columnName}, #{columnComment}, #{fewShot}, #{dataType}, #{synonyms}, #{vectorUuid}, #{defaultRecall}, #{analyzeSuggest}, 0)")
    int insert(ChatModelSchema schema);
}

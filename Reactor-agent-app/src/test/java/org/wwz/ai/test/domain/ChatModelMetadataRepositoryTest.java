package org.wwz.ai.test.domain;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelInfo;
import org.wwz.ai.domain.agent.rag.model.chatmodel.ChatModelSchema;
import org.wwz.ai.infrastructure.adapter.repository.dataquery.ChatModelMetadataRepository;
import org.wwz.ai.infrastructure.dao.reactor.chatmodel.ChatModelInfoMapper;
import org.wwz.ai.infrastructure.dao.reactor.chatmodel.ChatModelSchemaMapper;
import org.wwz.ai.infrastructure.dao.po.reactor.chatmodel.ChatModelInfoPO;
import org.wwz.ai.infrastructure.dao.po.reactor.chatmodel.ChatModelSchemaPO;

import java.util.List;
import java.lang.reflect.ParameterizedType;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chat Model 元数据仓储的 Domain/PO 映射与边界测试。
 */
public class ChatModelMetadataRepositoryTest {

    @Test
    public void shouldKeepPersistenceAnnotationsOnPoInsteadOfDomainModel() {
        Assert.assertNull(ChatModelInfo.class.getAnnotation(TableName.class));
        Assert.assertNull(ChatModelSchema.class.getAnnotation(TableName.class));
        Assert.assertFalse(hasTableLogic(ChatModelInfo.class));
        Assert.assertFalse(hasTableLogic(ChatModelSchema.class));
        Assert.assertEquals("chat_model_info", ChatModelInfoPO.class.getAnnotation(TableName.class).value());
        Assert.assertEquals("chat_model_schema", ChatModelSchemaPO.class.getAnnotation(TableName.class).value());
        Assert.assertTrue(hasTableLogic(ChatModelInfoPO.class));
        Assert.assertTrue(hasTableLogic(ChatModelSchemaPO.class));
    }

    @Test
    public void shouldBindChatModelDaosOnlyToInfrastructurePos() {
        Assert.assertEquals(ChatModelInfoPO.class, mapperEntityType(ChatModelInfoMapper.class));
        Assert.assertEquals(ChatModelSchemaPO.class, mapperEntityType(ChatModelSchemaMapper.class));
    }

    @Test
    public void shouldMapModelPoToDomainAndKeepNewestDistinctRow() {
        ChatModelInfoMapper infoMapper = Mockito.mock(ChatModelInfoMapper.class);
        ChatModelSchemaMapper schemaMapper = Mockito.mock(ChatModelSchemaMapper.class);
        ChatModelInfoPO newest = ChatModelInfoPO.builder()
                .id(20L)
                .code("sales")
                .type("table")
                .content("sales_data")
                .name("销售数据")
                .usePrompt(null)
                .businessPrompt(null)
                .yn(1)
                .build();
        ChatModelInfoPO older = ChatModelInfoPO.builder()
                .id(19L)
                .code("sales")
                .type("table")
                .content("old_sales_data")
                .name("旧销售数据")
                .yn(1)
                .build();
        when(infoMapper.selectList(Mockito.<Wrapper<ChatModelInfoPO>>any()))
                .thenReturn(List.of(newest, older));

        ChatModelMetadataRepository repository = new ChatModelMetadataRepository(infoMapper, schemaMapper);

        List<ChatModelInfo> models = repository.listDistinctModels();

        Assert.assertEquals(1, models.size());
        ChatModelInfo model = models.get(0);
        Assert.assertEquals(Long.valueOf(20L), model.getId());
        Assert.assertEquals("sales", model.getCode());
        Assert.assertNull(model.getUsePrompt());
        Assert.assertNull(model.getBusinessPrompt());
        Assert.assertEquals(Integer.valueOf(1), model.getYn());
        Assert.assertFalse(ChatModelInfoPO.class.isInstance(model));
    }

    @Test
    public void shouldMapSchemaPoNullableFieldsAndStatusToDomain() {
        ChatModelInfoMapper infoMapper = Mockito.mock(ChatModelInfoMapper.class);
        ChatModelSchemaMapper schemaMapper = Mockito.mock(ChatModelSchemaMapper.class);
        ChatModelSchemaPO schemaPO = ChatModelSchemaPO.builder()
                .id(30L)
                .modelCode("sales")
                .columnId("amount")
                .columnName("金额")
                .columnComment("销售金额")
                .fewShot(null)
                .dataType(null)
                .synonyms("销售额,金额")
                .vectorUuid(null)
                .defaultRecall(null)
                .analyzeSuggest(-1)
                .yn(1)
                .build();
        when(schemaMapper.selectList(Mockito.<Wrapper<ChatModelSchemaPO>>any()))
                .thenReturn(List.of(schemaPO));

        ChatModelMetadataRepository repository = new ChatModelMetadataRepository(infoMapper, schemaMapper);

        ChatModelSchema schema = repository.listDistinctSchemas().get(0);

        Assert.assertEquals(Long.valueOf(30L), schema.getId());
        Assert.assertEquals("sales", schema.getModelCode());
        Assert.assertNull(schema.getFewShot());
        Assert.assertNull(schema.getDataType());
        Assert.assertNull(schema.getVectorUuid());
        Assert.assertEquals(0, schema.getDefaultRecall());
        Assert.assertEquals(-1, schema.getAnalyzeSuggest());
        Assert.assertEquals(Integer.valueOf(1), schema.getYn());
        Assert.assertFalse(ChatModelSchemaPO.class.isInstance(schema));
    }

    @Test
    public void shouldKeepNewestDuplicateSchemaAndNormalizeMissingRecallFlags() {
        ChatModelInfoMapper infoMapper = Mockito.mock(ChatModelInfoMapper.class);
        ChatModelSchemaMapper schemaMapper = Mockito.mock(ChatModelSchemaMapper.class);
        ChatModelSchemaPO newest = ChatModelSchemaPO.builder()
                .id(42L)
                .modelCode("sales")
                .columnId("amount")
                .columnComment("最新金额说明")
                .defaultRecall(1)
                .analyzeSuggest(0)
                .build();
        ChatModelSchemaPO older = ChatModelSchemaPO.builder()
                .id(41L)
                .modelCode("sales")
                .columnId("amount")
                .columnComment("旧金额说明")
                .defaultRecall(0)
                .analyzeSuggest(-1)
                .build();
        when(schemaMapper.selectList(Mockito.<Wrapper<ChatModelSchemaPO>>any()))
                .thenReturn(List.of(newest, older));

        ChatModelMetadataRepository repository = new ChatModelMetadataRepository(infoMapper, schemaMapper);

        List<ChatModelSchema> schemas = repository.listDistinctSchemas();

        Assert.assertEquals(1, schemas.size());
        Assert.assertEquals(Long.valueOf(42L), schemas.get(0).getId());
        Assert.assertEquals("最新金额说明", schemas.get(0).getColumnComment());
        Assert.assertEquals(1, schemas.get(0).getDefaultRecall());
    }

    @Test
    public void shouldMapDomainModelsToIndependentPosWhenSaving() {
        ChatModelInfoMapper infoMapper = Mockito.mock(ChatModelInfoMapper.class);
        ChatModelSchemaMapper schemaMapper = Mockito.mock(ChatModelSchemaMapper.class);
        ChatModelMetadataRepository repository = new ChatModelMetadataRepository(infoMapper, schemaMapper);

        ChatModelInfo model = new ChatModelInfo();
        model.setId(40L);
        model.setCode("sales");
        model.setType("sql");
        model.setContent("select * from sales_data");
        model.setName("销售查询");
        model.setUsePrompt(null);
        model.setBusinessPrompt("仅返回销售相关结果");
        model.setYn(0);
        repository.saveModelInfo(model);

        ArgumentCaptor<ChatModelInfoPO> infoCaptor = ArgumentCaptor.forClass(ChatModelInfoPO.class);
        verify(infoMapper).insert(infoCaptor.capture());
        ChatModelInfoPO savedInfo = infoCaptor.getValue();
        Assert.assertEquals(Long.valueOf(40L), savedInfo.getId());
        Assert.assertNull(savedInfo.getUsePrompt());
        Assert.assertEquals(Integer.valueOf(0), savedInfo.getYn());
        Assert.assertNotSame(model, savedInfo);

        ChatModelSchema schema = new ChatModelSchema();
        schema.setModelCode("sales");
        schema.setColumnId("amount");
        schema.setColumnName("金额");
        schema.setColumnComment("销售金额");
        schema.setFewShot("华东,华南");
        schema.setDataType("decimal");
        schema.setSynonyms("销售额,金额");
        schema.setVectorUuid("uuid-1,uuid-2,uuid-3,uuid-4");
        schema.setDefaultRecall(1);
        schema.setAnalyzeSuggest(-1);
        schema.setYn(1);
        repository.saveModelSchemas(List.of(schema));

        ArgumentCaptor<ChatModelSchemaPO> schemaCaptor = ArgumentCaptor.forClass(ChatModelSchemaPO.class);
        verify(schemaMapper).insert(schemaCaptor.capture());
        ChatModelSchemaPO savedSchema = schemaCaptor.getValue();
        Assert.assertEquals("华东,华南", savedSchema.getFewShot());
        Assert.assertEquals(Integer.valueOf(1), savedSchema.getDefaultRecall());
        Assert.assertEquals(Integer.valueOf(-1), savedSchema.getAnalyzeSuggest());
        Assert.assertEquals(Integer.valueOf(1), savedSchema.getYn());
        Assert.assertNotSame(schema, savedSchema);
    }

    private boolean hasTableLogic(Class<?> type) {
        for (java.lang.reflect.Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(TableLogic.class)) {
                return true;
            }
        }
        return false;
    }

    private Class<?> mapperEntityType(Class<?> mapperType) {
        ParameterizedType baseMapperType = (ParameterizedType) mapperType.getGenericInterfaces()[0];
        Assert.assertEquals(BaseMapper.class, baseMapperType.getRawType());
        return (Class<?>) baseMapperType.getActualTypeArguments()[0];
    }
}

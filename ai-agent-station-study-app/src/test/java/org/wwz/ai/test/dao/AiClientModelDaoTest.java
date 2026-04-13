package org.wwz.ai.test.dao;

import org.wwz.ai.infrastructure.dao.IAiClientModelDao;
import org.wwz.ai.infrastructure.dao.po.AiClientModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天模型配置表 DAO 测试
 * @author bugstack虫洞栈
 * @description 聊天模型配置表数据访问对象测试
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiClientModelDaoTest {

    @Resource
    private IAiClientModelDao aiClientModelDao;

    /**
     * 为写操作生成唯一模型ID，避免重复执行测试时污染唯一索引。
     */
    private String nextModelId() {
        return "test_model_" + System.nanoTime();
    }

    @Test
    public void test_insert() {
        String modelId = nextModelId();
        AiClientModel aiClientModel = AiClientModel.builder()
                .modelId(modelId)
                .apiId("1001")
                .modelName("gpt-4o-mini")
                .modelType("openai")
                .modelUsage("chat")
                .status(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientModelDao.insert(aiClientModel);
        log.info("插入结果: {}, 生成ID: {}", result, aiClientModel.getId());
    }

    @Test
    public void test_updateById() {
        AiClientModel aiClientModel = AiClientModel.builder()
                .id(1L)
                .modelId("test_model_001")
                .apiId("1001")
                .modelName("gpt-4o-mini-updated")
                .modelType("openai")
                .modelUsage("chat")
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientModelDao.updateById(aiClientModel);
        log.info("更新结果: {}", result);
    }

    @Test
    public void test_updateByModelId() {
        AiClientModel aiClientModel = AiClientModel.builder()
                .modelId("test_model_001")
                .apiId("1001")
                .modelName("根据模型ID更新的模型")
                .modelType("openai")
                .modelUsage("chat")
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientModelDao.updateByModelId(aiClientModel);
        log.info("根据模型ID更新结果: {}", result);
    }

    @Test
    public void test_deleteById() {
        int result = aiClientModelDao.deleteById(1L);
        log.info("删除结果: {}", result);
    }

    @Test
    public void test_deleteByModelId() {
        int result = aiClientModelDao.deleteByModelId("test_model_001");
        log.info("根据模型ID删除结果: {}", result);
    }

    @Test
    public void test_queryById() {
        AiClientModel aiClientModel = aiClientModelDao.queryById(1L);
        log.info("根据ID查询结果: {}", aiClientModel);
    }

    @Test
    public void test_queryByModelId() {
        AiClientModel aiClientModel = aiClientModelDao.queryByModelId("2001");
        log.info("根据模型ID查询结果: {}", aiClientModel);
    }

    @Test
    public void test_queryByApiId() {
        List<AiClientModel> aiClientModels = aiClientModelDao.queryByApiId("1001");
        log.info("根据API配置ID查询结果数量: {}", aiClientModels.size());
        aiClientModels.forEach(model -> log.info("模型配置: {}", model));
    }

    @Test
    public void test_queryByModelType() {
        List<AiClientModel> aiClientModels = aiClientModelDao.queryByModelType("openai");
        log.info("根据模型类型查询结果数量: {}", aiClientModels.size());
        aiClientModels.forEach(model -> log.info("模型配置: {}", model));
    }

    @Test
    public void test_queryEnabledModels() {
        List<AiClientModel> aiClientModels = aiClientModelDao.queryEnabledModels();
        log.info("查询启用的模型配置数量: {}", aiClientModels.size());
        aiClientModels.forEach(model -> log.info("启用的模型配置: {}", model));
    }

    @Test
    public void test_queryAll() {
        List<AiClientModel> aiClientModels = aiClientModelDao.queryAll();
        log.info("查询所有模型配置数量: {}", aiClientModels.size());
        aiClientModels.forEach(model -> log.info("模型配置: {}", model));
    }

}

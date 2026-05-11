package org.wwz.ai.test.dao;

import org.wwz.ai.infrastructure.dao.IAiClientConfigDao;
import org.wwz.ai.infrastructure.dao.po.AiClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI客户端统一关联配置表 DAO 测试
 * @author bugstack虫洞栈
 * @description AI客户端统一关联配置表数据访问对象测试
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiClientConfigDaoTest {

    @Resource
    private IAiClientConfigDao aiClientConfigDao;

    @Test
    public void test_insert() {
        AiClientConfig aiClientConfig = AiClientConfig.builder()
                .sourceType("model")
                .sourceId("test_model_001")
                .targetType("tool_mcp")
                .targetId("test_tool_001")
                .extParam("{}")
                .status(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientConfigDao.insert(aiClientConfig);
        log.info("插入结果: {}, 生成ID: {}", result, aiClientConfig.getId());
    }

    @Test
    public void test_updateById() {
        AiClientConfig aiClientConfig = AiClientConfig.builder()
                .id(1L)
                .sourceType("model")
                .sourceId("test_model_001")
                .targetType("tool_mcp")
                .targetId("updated_tool_001")
                .extParam("{\"updated\": true}")
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientConfigDao.updateById(aiClientConfig);
        log.info("更新结果: {}", result);
    }

    @Test
    public void test_queryById() {
        AiClientConfig aiClientConfig = aiClientConfigDao.queryById(1L);
        log.info("查询结果: {}", aiClientConfig);
    }

    @Test
    public void test_queryBySourceId() {
        List<AiClientConfig> aiClientConfigs = aiClientConfigDao.queryBySourceId("2001");
        log.info("根据源ID查询结果: {}", aiClientConfigs);
    }

    @Test
    public void test_queryByTargetId() {
        List<AiClientConfig> aiClientConfigs = aiClientConfigDao.queryByTargetId("5001");
        log.info("根据目标ID查询结果: {}", aiClientConfigs);
    }

    @Test
    public void test_queryBySourceTypeAndId() {
        List<AiClientConfig> aiClientConfigs = aiClientConfigDao.queryBySourceTypeAndId("model", "2001");
        log.info("根据源类型和源ID查询结果: {}", aiClientConfigs);
    }

    @Test
    public void test_queryByTargetTypeAndId() {
        List<AiClientConfig> aiClientConfigs = aiClientConfigDao.queryByTargetTypeAndId("tool_mcp", "5001");
        log.info("根据目标类型和目标ID查询结果: {}", aiClientConfigs);
    }

    @Test
    public void test_queryEnabledConfigs() {
        List<AiClientConfig> aiClientConfigs = aiClientConfigDao.queryEnabledConfigs();
        log.info("查询启用状态配置结果: {}", aiClientConfigs);
    }

    @Test
    public void test_queryAll() {
        List<AiClientConfig> aiClientConfigs = aiClientConfigDao.queryAll();
        log.info("查询所有配置结果: {}", aiClientConfigs);
    }

    @Test
    public void test_deleteById() {
        int result = aiClientConfigDao.deleteById(1L);
        log.info("删除结果: {}", result);
    }

    @Test
    public void test_deleteBySourceId() {
        int result = aiClientConfigDao.deleteBySourceId("test_model_001");
        log.info("根据源ID删除结果: {}", result);
    }

}
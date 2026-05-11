package org.wwz.ai.test.dao;

import org.wwz.ai.infrastructure.dao.IAiClientApiDao;
import org.wwz.ai.infrastructure.dao.po.AiClientApi;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI客户端API配置表 DAO 测试
 * @author bugstack虫洞栈
 * @description AI客户端API配置表数据访问对象测试
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiClientApiDaoTest {

    @Resource
    private IAiClientApiDao aiClientApiDao;

    @Test
    public void test_insert() {
        AiClientApi aiClientApi = AiClientApi.builder()
                .apiId("test_api_001")
                .baseUrl("https://api.openai.com")
                .apiKey("sk-test123456789")
                .completionsPath("/v1/chat/completions")
                .embeddingsPath("/v1/embeddings")
                .status(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientApiDao.insert(aiClientApi);
        log.info("插入结果: {}, 生成ID: {}", result, aiClientApi.getId());
    }

    @Test
    public void test_updateById() {
        AiClientApi aiClientApi = AiClientApi.builder()
                .id(1L)
                .apiId("test_api_001")
                .baseUrl("https://api.openai.com")
                .apiKey("sk-updated123456789")
                .completionsPath("/v1/chat/completions")
                .embeddingsPath("/v1/embeddings")
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientApiDao.updateById(aiClientApi);
        log.info("更新结果: {}", result);
    }

    @Test
    public void test_updateByApiId() {
        AiClientApi aiClientApi = AiClientApi.builder()
                .apiId("test_api_001")
                .baseUrl("https://api.openai.com")
                .apiKey("sk-updated-by-api-id")
                .completionsPath("/v1/chat/completions")
                .embeddingsPath("/v1/embeddings")
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientApiDao.updateByApiId(aiClientApi);
        log.info("根据API ID更新结果: {}", result);
    }

    @Test
    public void test_deleteById() {
        int result = aiClientApiDao.deleteById(1L);
        log.info("删除结果: {}", result);
    }

    @Test
    public void test_deleteByApiId() {
        int result = aiClientApiDao.deleteByApiId("test_api_001");
        log.info("根据API ID删除结果: {}", result);
    }

    @Test
    public void test_queryById() {
        AiClientApi aiClientApi = aiClientApiDao.queryById(1L);
        log.info("查询结果: {}", aiClientApi);
    }

    @Test
    public void test_queryByApiId() {
        AiClientApi aiClientApi = aiClientApiDao.queryByApiId("openai-gpt-4o");
        log.info("根据API ID查询结果: {}", aiClientApi);
    }

    @Test
    public void test_queryEnabledApis() {
        List<AiClientApi> aiClientApis = aiClientApiDao.queryEnabledApis();
        log.info("查询启用的API配置: {}", aiClientApis);
    }

    @Test
    public void test_queryAll() {
        List<AiClientApi> aiClientApis = aiClientApiDao.queryAll();
        log.info("查询所有API配置: {}", aiClientApis);
    }

}
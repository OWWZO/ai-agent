package org.wwz.ai.test.dao;

import org.wwz.ai.infrastructure.dao.IAiClientSystemPromptDao;
import org.wwz.ai.infrastructure.dao.po.AiClientSystemPrompt;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 系统提示词配置表 DAO 测试
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiClientSystemPromptDaoTest {

    @Resource
    private IAiClientSystemPromptDao aiClientSystemPromptDao;

    @Test
    public void test_insert() {
        AiClientSystemPrompt aiClientSystemPrompt = AiClientSystemPrompt.builder()
                .promptId("test_001")
                .promptName("测试提示词")
                .promptContent("这是一个测试提示词内容")
                .description("测试描述")
                .status(1)
                .build();

        aiClientSystemPromptDao.insert(aiClientSystemPrompt);
        log.info("插入系统提示词配置完成，ID: {}", aiClientSystemPrompt.getId());
    }

    @Test
    public void test_updateById() {
        AiClientSystemPrompt aiClientSystemPrompt = AiClientSystemPrompt.builder()
                .id(6L)
                .promptName("更新后的提示词名称")
                .description("更新后的描述")
                .status(0)
                .build();

        int count = aiClientSystemPromptDao.updateById(aiClientSystemPrompt);
        log.info("更新系统提示词配置完成，影响行数: {}", count);
    }

    @Test
    public void test_updateByPromptId() {
        AiClientSystemPrompt aiClientSystemPrompt = AiClientSystemPrompt.builder()
                .promptId("6001")
                .promptName("更新后的提示词名称")
                .description("更新后的描述")
                .status(0)
                .build();

        int count = aiClientSystemPromptDao.updateByPromptId(aiClientSystemPrompt);
        log.info("根据提示词ID更新系统提示词配置完成，影响行数: {}", count);
    }

    @Test
    public void test_deleteById() {
        int count = aiClientSystemPromptDao.deleteById(6L);
        log.info("删除系统提示词配置完成，影响行数: {}", count);
    }

    @Test
    public void test_deleteByPromptId() {
        int count = aiClientSystemPromptDao.deleteByPromptId("test_001");
        log.info("根据提示词ID删除系统提示词配置完成，影响行数: {}", count);
    }

    @Test
    public void test_queryById() {
        AiClientSystemPrompt aiClientSystemPrompt = aiClientSystemPromptDao.queryById(6L);
        log.info("查询系统提示词配置: {}", aiClientSystemPrompt);
    }

    @Test
    public void test_queryByPromptId() {
        AiClientSystemPrompt aiClientSystemPrompt = aiClientSystemPromptDao.queryByPromptId("6001");
        log.info("根据提示词ID查询系统提示词配置: {}", aiClientSystemPrompt);
    }

    @Test
    public void test_queryEnabledPrompts() {
        List<AiClientSystemPrompt> prompts = aiClientSystemPromptDao.queryEnabledPrompts();
        log.info("查询启用的系统提示词配置: {}", prompts);
    }

    @Test
    public void test_queryByPromptName() {
        List<AiClientSystemPrompt> prompts = aiClientSystemPromptDao.queryByPromptName("提示词");
        log.info("根据提示词名称查询系统提示词配置: {}", prompts);
    }

    @Test
    public void test_queryAll() {
        List<AiClientSystemPrompt> prompts = aiClientSystemPromptDao.queryAll();
        log.info("查询所有系统提示词配置: {}", prompts);
    }

}
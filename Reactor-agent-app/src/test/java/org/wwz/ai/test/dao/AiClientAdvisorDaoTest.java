package org.wwz.ai.test.dao;

import org.wwz.ai.infrastructure.dao.IAiClientAdvisorDao;
import org.wwz.ai.infrastructure.dao.po.AiClientAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 顾问配置表 DAO 测试
 * @author bugstack虫洞栈
 * @description 顾问配置表数据访问对象测试
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiClientAdvisorDaoTest {

    @Resource
    private IAiClientAdvisorDao aiClientAdvisorDao;

    @Test
    public void test_insert() {
        AiClientAdvisor aiClientAdvisor = AiClientAdvisor.builder()
                .advisorId("advisor_001")
                .advisorName("测试顾问")
                .advisorType("PromptChatMemory")
                .orderNum(1)
                .extParam("{\"key\":\"value\"}")
                .status(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientAdvisorDao.insert(aiClientAdvisor);
        log.info("插入结果: {}, 生成ID: {}", result, aiClientAdvisor.getId());
    }

    @Test
    public void test_updateById() {
        AiClientAdvisor aiClientAdvisor = AiClientAdvisor.builder()
                .id(1L)
                .advisorId("advisor_001")
                .advisorName("更新后的测试顾问")
                .advisorType("RagAnswer")
                .orderNum(2)
                .extParam("{\"updated\":\"true\"}")
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientAdvisorDao.updateById(aiClientAdvisor);
        log.info("更新结果: {}", result);
    }

    @Test
    public void test_updateByAdvisorId() {
        AiClientAdvisor aiClientAdvisor = AiClientAdvisor.builder()
                .advisorId("advisor_001")
                .advisorName("根据顾问ID更新的测试顾问")
                .advisorType("SimpleLoggerAdvisor")
                .orderNum(3)
                .extParam("{\"method\":\"updateByAdvisorId\"}")
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientAdvisorDao.updateByAdvisorId(aiClientAdvisor);
        log.info("根据顾问ID更新结果: {}", result);
    }

    @Test
    public void test_deleteById() {
        int result = aiClientAdvisorDao.deleteById(1L);
        log.info("删除结果: {}", result);
    }

    @Test
    public void test_deleteByAdvisorId() {
        int result = aiClientAdvisorDao.deleteByAdvisorId("advisor_001");
        log.info("根据顾问ID删除结果: {}", result);
    }

    @Test
    public void test_queryById() {
        AiClientAdvisor aiClientAdvisor = aiClientAdvisorDao.queryById(1L);
        log.info("根据ID查询结果: {}", aiClientAdvisor);
    }

    @Test
    public void test_queryByAdvisorId() {
        AiClientAdvisor aiClientAdvisor = aiClientAdvisorDao.queryByAdvisorId("advisor_001");
        log.info("根据顾问ID查询结果: {}", aiClientAdvisor);
    }

    @Test
    public void test_queryAll() {
        List<AiClientAdvisor> aiClientAdvisorList = aiClientAdvisorDao.queryAll();
        log.info("查询所有结果: {}", aiClientAdvisorList);
    }

    @Test
    public void test_queryByStatus() {
        List<AiClientAdvisor> aiClientAdvisorList = aiClientAdvisorDao.queryByStatus(1);
        log.info("根据状态查询结果: {}", aiClientAdvisorList);
    }

    @Test
    public void test_queryByAdvisorType() {
        List<AiClientAdvisor> aiClientAdvisorList = aiClientAdvisorDao.queryByAdvisorType("PromptChatMemory");
        log.info("根据顾问类型查询结果: {}", aiClientAdvisorList);
    }

}
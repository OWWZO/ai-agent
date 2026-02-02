package org.wwz.ai.test.dao;

import org.wwz.ai.infrastructure.dao.IAiAgentDao;
import org.wwz.ai.infrastructure.dao.po.AiAgent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI智能体配置表 DAO 测试
 * @author bugstack虫洞栈
 * @description AI智能体配置表数据访问对象测试
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiAgentDaoTest {

    @Resource
    private IAiAgentDao aiAgentDao;

    @Test
    public void test_insert() {
        AiAgent aiAgent = AiAgent.builder()
                .agentId("test_001")
                .agentName("测试智能体")
                .description("这是一个测试智能体")
                .channel("agent")
                .status(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiAgentDao.insert(aiAgent);
        log.info("插入结果: {}, 生成ID: {}", result, aiAgent.getId());
    }

    @Test
    public void test_updateById() {
        AiAgent aiAgent = AiAgent.builder()
                .id(1L)
                .agentId("test_001")
                .agentName("更新后的测试智能体")
                .description("这是一个更新后的测试智能体")
                .channel("chat_stream")
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiAgentDao.updateById(aiAgent);
        log.info("更新结果: {}", result);
    }

    @Test
    public void test_updateByAgentId() {
        AiAgent aiAgent = AiAgent.builder()
                .agentId("test_001")
                .agentName("通过AgentId更新的智能体")
                .description("通过AgentId更新的描述")
                .channel("agent")
                .status(0)
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiAgentDao.updateByAgentId(aiAgent);
        log.info("通过AgentId更新结果: {}", result);
    }

    @Test
    public void test_queryById() {
        AiAgent aiAgent = aiAgentDao.queryById(1L);
        log.info("根据ID查询结果: {}", aiAgent);
    }

    @Test
    public void test_queryByAgentId() {
        AiAgent aiAgent = aiAgentDao.queryByAgentId("1");
        log.info("根据AgentId查询结果: {}", aiAgent);
    }

    @Test
    public void test_queryEnabledAgents() {
        List<AiAgent> aiAgents = aiAgentDao.queryEnabledAgents();
        log.info("查询启用的智能体数量: {}", aiAgents.size());
        aiAgents.forEach(agent -> log.info("启用的智能体: {}", agent));
    }

    @Test
    public void test_queryByChannel() {
        List<AiAgent> aiAgents = aiAgentDao.queryByChannel("agent");
        log.info("根据渠道查询结果数量: {}", aiAgents.size());
        aiAgents.forEach(agent -> log.info("渠道智能体: {}", agent));
    }

    @Test
    public void test_queryAll() {
        List<AiAgent> aiAgents = aiAgentDao.queryAll();
        log.info("查询所有智能体数量: {}", aiAgents.size());
        aiAgents.forEach(agent -> log.info("智能体: {}", agent));
    }

    @Test
    public void test_deleteById() {
        int result = aiAgentDao.deleteById(1L);
        log.info("根据ID删除结果: {}", result);
    }

    @Test
    public void test_deleteByAgentId() {
        int result = aiAgentDao.deleteByAgentId("test_001");
        log.info("根据AgentId删除结果: {}", result);
    }

}
package org.wwz.ai.test.dao;

import org.wwz.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import org.wwz.ai.infrastructure.dao.IAiAgentFlowConfigDao;
import org.wwz.ai.infrastructure.dao.po.AiAgentFlowConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体-客户端关联表 DAO 测试
 * @author bugstack虫洞栈
 * @description 智能体-客户端关联表数据访问对象测试
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiAgentFlowConfigDaoTest {

    @Resource
    private IAiAgentFlowConfigDao aiAgentFlowConfigDao;

    @Test
    public void test_insert() {
        AiAgentFlowConfig aiAgentFlowConfig = AiAgentFlowConfig.builder()
                .agentId("1")
                .clientId("3001")
                .clientName("测试客户端")
                .clientType(AiClientTypeEnumVO.TASK_ANALYZER_CLIENT.getCode())
                .sequence(1)
                .stepPrompt("测试步骤提示词")
                .createTime(LocalDateTime.now())
                .build();

        int result = aiAgentFlowConfigDao.insert(aiAgentFlowConfig);
        log.info("插入结果: {}, 生成ID: {}", result, aiAgentFlowConfig.getId());
    }

    @Test
    public void test_updateById() {
        AiAgentFlowConfig aiAgentFlowConfig = AiAgentFlowConfig.builder()
                .clientId("3002")
                .sequence(2)
                .build();

        int result = aiAgentFlowConfigDao.updateById(aiAgentFlowConfig);
        log.info("更新结果: {}", result);
    }

    @Test
    public void test_queryById() {
        AiAgentFlowConfig aiAgentFlowConfig = aiAgentFlowConfigDao.queryById("1");
        log.info("根据ID查询结果: {}", aiAgentFlowConfig);
    }

    @Test
    public void test_queryByAgentId() {
        List<AiAgentFlowConfig> aiAgentFlowConfigs = aiAgentFlowConfigDao.queryByAgentId("1");
        log.info("根据智能体ID查询结果数量: {}", aiAgentFlowConfigs.size());
        aiAgentFlowConfigs.forEach(config -> log.info("智能体关联配置: {}", config));
    }

    @Test
    public void test_queryByClientId() {
        List<AiAgentFlowConfig> aiAgentFlowConfigs = aiAgentFlowConfigDao.queryByClientId("3001");
        log.info("根据客户端ID查询结果数量: {}", aiAgentFlowConfigs.size());
        aiAgentFlowConfigs.forEach(config -> log.info("客户端关联配置: {}", config));
    }

    @Test
    public void test_queryByAgentIdAndClientId() {
        AiAgentFlowConfig aiAgentFlowConfig = aiAgentFlowConfigDao.queryByAgentIdAndClientId("1", "3001");
        log.info("根据智能体ID和客户端ID查询结果: {}", aiAgentFlowConfig);
    }

    @Test
    public void test_queryAll() {
        List<AiAgentFlowConfig> aiAgentFlowConfigs = aiAgentFlowConfigDao.queryAll();
        log.info("查询所有关联配置数量: {}", aiAgentFlowConfigs.size());
        aiAgentFlowConfigs.forEach(config -> log.info("关联配置: {}", config));
    }

    @Test
    public void test_deleteById() {
        int result = aiAgentFlowConfigDao.deleteById("1");
        log.info("根据ID删除结果: {}", result);
    }

    @Test
    public void test_deleteByAgentId() {
        int result = aiAgentFlowConfigDao.deleteByAgentId("1");
        log.info("根据智能体ID删除结果: {}", result);
    }

}

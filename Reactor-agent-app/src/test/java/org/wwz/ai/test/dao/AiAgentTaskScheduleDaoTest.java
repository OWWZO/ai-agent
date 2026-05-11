package org.wwz.ai.test.dao;

import org.wwz.ai.infrastructure.dao.IAiAgentTaskScheduleDao;
import org.wwz.ai.infrastructure.dao.po.AiAgentTaskSchedule;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体任务调度配置表 DAO 测试
 * @author bugstack虫洞栈
 * @description 智能体任务调度配置表数据访问对象测试
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiAgentTaskScheduleDaoTest {

    @Resource
    private IAiAgentTaskScheduleDao aiAgentTaskScheduleDao;

    @Test
    public void test_insert() {
        AiAgentTaskSchedule aiAgentTaskSchedule = AiAgentTaskSchedule.builder()
                .agentId("1")
                .taskName("测试任务")
                .description("这是一个测试任务")
                .cronExpression("0 0/30 * * * ?")
                .taskParam("{\"type\":\"test\"}")
                .status(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiAgentTaskScheduleDao.insert(aiAgentTaskSchedule);
        log.info("插入结果: {}, 生成ID: {}", result, aiAgentTaskSchedule.getId());
    }

    @Test
    public void test_updateById() {
        AiAgentTaskSchedule aiAgentTaskSchedule = AiAgentTaskSchedule.builder()
                .id(1L)
                .agentId("1")
                .taskName("更新后的测试任务")
                .description("这是一个更新后的测试任务")
                .cronExpression("0 0/15 * * * ?")
                .taskParam("{\"type\":\"updated_test\"}")
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiAgentTaskScheduleDao.updateById(aiAgentTaskSchedule);
        log.info("更新结果: {}", result);
    }

    @Test
    public void test_queryById() {
        AiAgentTaskSchedule aiAgentTaskSchedule = aiAgentTaskScheduleDao.queryById(1L);
        log.info("根据ID查询结果: {}", aiAgentTaskSchedule);
    }

    @Test
    public void test_queryByAgentId() {
        List<AiAgentTaskSchedule> aiAgentTaskSchedules = aiAgentTaskScheduleDao.queryByAgentId(1L);
        log.info("根据智能体ID查询结果数量: {}", aiAgentTaskSchedules.size());
        aiAgentTaskSchedules.forEach(task -> log.info("智能体任务: {}", task));
    }

    @Test
    public void test_queryEnabledTasks() {
        List<AiAgentTaskSchedule> aiAgentTaskSchedules = aiAgentTaskScheduleDao.queryEnabledTasks();
        log.info("查询有效任务数量: {}", aiAgentTaskSchedules.size());
        aiAgentTaskSchedules.forEach(task -> log.info("有效任务: {}", task));
    }

    @Test
    public void test_queryByTaskName() {
        AiAgentTaskSchedule aiAgentTaskSchedule = aiAgentTaskScheduleDao.queryByTaskName("自动发帖");
        log.info("根据任务名称查询结果: {}", aiAgentTaskSchedule);
    }

    @Test
    public void test_queryAll() {
        List<AiAgentTaskSchedule> aiAgentTaskSchedules = aiAgentTaskScheduleDao.queryAll();
        log.info("查询所有任务调度配置数量: {}", aiAgentTaskSchedules.size());
        aiAgentTaskSchedules.forEach(task -> log.info("任务调度配置: {}", task));
    }

    @Test
    public void test_deleteById() {
        int result = aiAgentTaskScheduleDao.deleteById(1L);
        log.info("根据ID删除结果: {}", result);
    }

    @Test
    public void test_deleteByAgentId() {
        int result = aiAgentTaskScheduleDao.deleteByAgentId(1L);
        log.info("根据智能体ID删除结果: {}", result);
    }

}
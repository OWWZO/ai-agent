package org.wwz.ai.test.dao;

import org.wwz.ai.infrastructure.dao.IAiClientRagOrderDao;
import org.wwz.ai.infrastructure.dao.po.AiClientRagOrder;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库配置表 DAO 测试
 * @author bugstack虫洞栈
 * @description 知识库配置表数据访问对象测试
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiClientRagOrderDaoTest {

    @Resource
    private IAiClientRagOrderDao aiClientRagOrderDao;

    @Test
    public void test_insert() {
        AiClientRagOrder aiClientRagOrder = AiClientRagOrder.builder()
                .ragId("test_rag_001")
                .ragName("测试知识库")
                .knowledgeTag("测试标签")
                .status(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientRagOrderDao.insert(aiClientRagOrder);
        log.info("插入结果: {}, 生成ID: {}", result, aiClientRagOrder.getId());
    }

    @Test
    public void test_updateById() {
        AiClientRagOrder aiClientRagOrder = AiClientRagOrder.builder()
                .id(1L)
                .ragId("test_rag_001")
                .ragName("更新后的测试知识库")
                .knowledgeTag("更新后的测试标签")
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientRagOrderDao.updateById(aiClientRagOrder);
        log.info("更新结果: {}", result);
    }

    @Test
    public void test_updateByRagId() {
        AiClientRagOrder aiClientRagOrder = AiClientRagOrder.builder()
                .ragId("test_rag_001")
                .ragName("根据知识库ID更新的测试知识库")
                .knowledgeTag("根据知识库ID更新的测试标签")
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientRagOrderDao.updateByRagId(aiClientRagOrder);
        log.info("根据知识库ID更新结果: {}", result);
    }

    @Test
    public void test_deleteById() {
        int result = aiClientRagOrderDao.deleteById(1L);
        log.info("删除结果: {}", result);
    }

    @Test
    public void test_deleteByRagId() {
        int result = aiClientRagOrderDao.deleteByRagId("test_rag_001");
        log.info("根据知识库ID删除结果: {}", result);
    }

    @Test
    public void test_queryById() {
        AiClientRagOrder aiClientRagOrder = aiClientRagOrderDao.queryById(3L);
        log.info("根据ID查询结果: {}", aiClientRagOrder);
    }

    @Test
    public void test_queryByRagId() {
        AiClientRagOrder aiClientRagOrder = aiClientRagOrderDao.queryByRagId("9001");
        log.info("根据知识库ID查询结果: {}", aiClientRagOrder);
    }

    @Test
    public void test_queryEnabledRagOrders() {
        List<AiClientRagOrder> ragOrders = aiClientRagOrderDao.queryEnabledRagOrders();
        log.info("查询启用的知识库配置结果: {}", ragOrders);
    }

    @Test
    public void test_queryByKnowledgeTag() {
        List<AiClientRagOrder> ragOrders = aiClientRagOrderDao.queryByKnowledgeTag("生成文章提示词");
        log.info("根据知识标签查询结果: {}", ragOrders);
    }

    @Test
    public void test_queryAll() {
        List<AiClientRagOrder> ragOrders = aiClientRagOrderDao.queryAll();
        log.info("查询所有知识库配置结果: {}", ragOrders);
    }

}
package org.wwz.ai.test.dao;

import org.wwz.ai.infrastructure.dao.IAiClientToolMcpDao;
import org.wwz.ai.infrastructure.dao.po.AiClientToolMcp;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP客户端配置表 DAO 测试
 * @author bugstack虫洞栈
 * @description MCP客户端配置表数据访问对象测试
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiClientToolMcpDaoTest {

    @Resource
    private IAiClientToolMcpDao aiClientToolMcpDao;

    @Test
    public void test_insert() {
        AiClientToolMcp aiClientToolMcp = AiClientToolMcp.builder()
                .mcpId("test_5006")
                .mcpName("测试MCP工具")
                .transportType("sse")
                .transportConfig("{\"baseUri\":\"http://localhost:8080\",\"sseEndpoint\":\"/sse\"}")
                .requestTimeout(180)
                .status(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientToolMcpDao.insert(aiClientToolMcp);
        log.info("插入结果: {}, 生成ID: {}", result, aiClientToolMcp.getId());
    }

    @Test
    public void test_updateById() {
        AiClientToolMcp aiClientToolMcp = AiClientToolMcp.builder()
                .id(1L)
                .mcpId("test_5006")
                .mcpName("更新后的测试MCP工具")
                .transportType("stdio")
                .transportConfig("{\"command\":\"npx\",\"args\":[\"-y\",\"test-mcp\"]}")
                .requestTimeout(300)
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientToolMcpDao.updateById(aiClientToolMcp);
        log.info("更新结果: {}", result);
    }

    @Test
    public void test_updateByMcpId() {
        AiClientToolMcp aiClientToolMcp = AiClientToolMcp.builder()
                .mcpId("5001")
                .mcpName("根据MCP ID更新的工具")
                .transportType("sse")
                .transportConfig("{\"baseUri\":\"http://updated.example.com\",\"sseEndpoint\":\"/sse\"}")
                .requestTimeout(240)
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = aiClientToolMcpDao.updateByMcpId(aiClientToolMcp);
        log.info("根据MCP ID更新结果: {}", result);
    }

    @Test
    public void test_deleteById() {
        int result = aiClientToolMcpDao.deleteById(1L);
        log.info("删除结果: {}", result);
    }

    @Test
    public void test_deleteByMcpId() {
        int result = aiClientToolMcpDao.deleteByMcpId("test_5006");
        log.info("根据MCP ID删除结果: {}", result);
    }

    @Test
    public void test_queryById() {
        AiClientToolMcp aiClientToolMcp = aiClientToolMcpDao.queryById(6L);
        log.info("根据ID查询结果: {}", aiClientToolMcp);
    }

    @Test
    public void test_queryByMcpId() {
        AiClientToolMcp aiClientToolMcp = aiClientToolMcpDao.queryByMcpId("5001");
        log.info("根据MCP ID查询结果: {}", aiClientToolMcp);
    }

    @Test
    public void test_queryAll() {
        List<AiClientToolMcp> aiClientToolMcpList = aiClientToolMcpDao.queryAll();
        log.info("查询所有MCP工具配置数量: {}", aiClientToolMcpList.size());
        aiClientToolMcpList.forEach(mcp -> log.info("MCP工具配置: {}", mcp));
    }

    @Test
    public void test_queryByStatus() {
        List<AiClientToolMcp> aiClientToolMcpList = aiClientToolMcpDao.queryByStatus(1);
        log.info("根据状态查询结果数量: {}", aiClientToolMcpList.size());
        aiClientToolMcpList.forEach(mcp -> log.info("启用的MCP工具配置: {}", mcp));
    }

    @Test
    public void test_queryByTransportType() {
        List<AiClientToolMcp> aiClientToolMcpList = aiClientToolMcpDao.queryByTransportType("sse");
        log.info("根据传输类型查询结果数量: {}", aiClientToolMcpList.size());
        aiClientToolMcpList.forEach(mcp -> log.info("SSE类型MCP工具配置: {}", mcp));
    }

    @Test
    public void test_queryEnabledMcps() {
        List<AiClientToolMcp> aiClientToolMcpList = aiClientToolMcpDao.queryEnabledMcps();
        log.info("查询启用的MCP工具配置数量: {}", aiClientToolMcpList.size());
        aiClientToolMcpList.forEach(mcp -> log.info("启用的MCP工具配置: {}", mcp));
    }

}
package org.wwz.ai.test.dao;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.wwz.ai.infrastructure.dao.catalog.IAiClientToolMcpConfigDao;
import org.wwz.ai.infrastructure.dao.po.catalog.AiClientToolMcpPO;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP 客户端配置表 DAO 测试（catalog 栈）。
 * <p>
 * 与旧 {@code AiClientToolMcpDaoTest} 的覆盖范围一致，但走运行时实际使用的 DAO。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiClientToolMcpConfigDaoTest {

    @Resource
    private IAiClientToolMcpConfigDao dao;

    /**
     * 生成唯一 MCP 标识，避免测试重复运行时命中唯一索引。
     */
    private String nextMcpId(String prefix) {
        return prefix + "_" + System.nanoTime();
    }

    private AiClientToolMcpPO newRow(String mcpId, String transportType, String transportConfig) {
        LocalDateTime now = LocalDateTime.now();
        return AiClientToolMcpPO.builder()
                .mcpId(mcpId)
                .mcpName("测试MCP工具")
                .transportType(transportType)
                .transportConfig(transportConfig)
                .requestTimeout(180)
                .status(1)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    @Test
    public void test_insert() {
        AiClientToolMcpPO row = newRow(nextMcpId("test_5006"), "sse",
                "{\"baseUri\":\"http://localhost:8080\",\"sseEndpoint\":\"/sse\"}");

        int result = dao.insert(row);
        log.info("插入结果: {}, 生成ID: {}", result, row.getId());
    }

    @Test
    public void test_insert_streamableHttp() {
        AiClientToolMcpPO row = newRow(nextMcpId("test_streamable_5007"), "streamable_http",
                "{\"baseUri\":\"http://127.0.0.1:8101\",\"endpoint\":\"/mcp\","
                        + "\"headers\":{\"Authorization\":\"Bearer test-token\"},"
                        + "\"resumableStreams\":false,\"openConnectionOnStartup\":true}");

        int result = dao.insert(row);
        log.info("插入Streamable HTTP结果: {}, 生成ID: {}", result, row.getId());
    }

    @Test
    public void test_updateById() {
        AiClientToolMcpPO row = AiClientToolMcpPO.builder()
                .id(1L)
                .mcpId("test_5006")
                .mcpName("更新后的测试MCP工具")
                .transportType("stdio")
                .transportConfig("{\"command\":\"npx\",\"args\":[\"-y\",\"test-mcp\"]}")
                .requestTimeout(300)
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = dao.updateById(row);
        log.info("更新结果: {}", result);
    }

    @Test
    public void test_updateByMcpId() {
        AiClientToolMcpPO row = AiClientToolMcpPO.builder()
                .mcpId("5001")
                .mcpName("根据MCP ID更新的工具")
                .transportType("sse")
                .transportConfig("{\"baseUri\":\"http://updated.example.com\",\"sseEndpoint\":\"/sse\"}")
                .requestTimeout(240)
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = dao.updateByMcpId(row);
        log.info("根据MCP ID更新结果: {}", result);
    }

    @Test
    public void test_updateByMcpId_streamableHttp() {
        AiClientToolMcpPO row = AiClientToolMcpPO.builder()
                .mcpId("test_streamable_5007")
                .mcpName("根据MCP ID更新的StreamableHttp工具")
                .transportType("streamable_http")
                .transportConfig("{\"baseUri\":\"http://updated.example.com\",\"endpoint\":\"/mcp\","
                        + "\"headers\":{\"Authorization\":\"Bearer updated-token\"},"
                        + "\"resumableStreams\":true,\"openConnectionOnStartup\":true}")
                .requestTimeout(240)
                .status(1)
                .updateTime(LocalDateTime.now())
                .build();

        int result = dao.updateByMcpId(row);
        log.info("根据MCP ID更新Streamable HTTP结果: {}", result);
    }

    @Test
    public void test_deleteById() {
        int result = dao.deleteById(1L);
        log.info("删除结果: {}", result);
    }

    @Test
    public void test_deleteByMcpId() {
        int result = dao.deleteByMcpId("test_5006");
        log.info("根据MCP ID删除结果: {}", result);
    }

    @Test
    public void test_queryById() {
        AiClientToolMcpPO row = dao.queryById(6L);
        log.info("根据ID查询结果: {}", row);
    }

    @Test
    public void test_queryByMcpId() {
        AiClientToolMcpPO row = dao.queryByMcpId("5001");
        log.info("根据MCP ID查询结果: {}", row);
    }

    @Test
    public void test_queryAll() {
        List<AiClientToolMcpPO> rows = dao.queryAll();
        log.info("查询所有MCP工具配置数量: {}", rows.size());
        rows.forEach(row -> log.info("MCP工具配置: {}", row));
    }

    @Test
    public void test_queryByStatus() {
        List<AiClientToolMcpPO> rows = dao.queryByStatus(1);
        log.info("根据状态查询结果数量: {}", rows.size());
        rows.forEach(row -> log.info("启用的MCP工具配置: {}", row));
    }

    @Test
    public void test_queryByTransportType() {
        List<AiClientToolMcpPO> rows = dao.queryByTransportType("sse");
        log.info("根据传输类型查询结果数量: {}", rows.size());
        rows.forEach(row -> log.info("SSE类型MCP工具配置: {}", row));
    }

    @Test
    public void test_queryByTransportType_streamableHttp() {
        List<AiClientToolMcpPO> rows = dao.queryByTransportType("streamable_http");
        log.info("根据Streamable HTTP传输类型查询结果数量: {}", rows.size());
        rows.forEach(row -> log.info("Streamable HTTP类型MCP工具配置: {}", row));
    }

    @Test
    public void test_queryEnabled() {
        List<AiClientToolMcpPO> rows = dao.queryEnabled();
        log.info("查询启用的MCP工具配置数量: {}", rows.size());
        rows.forEach(row -> log.info("启用的MCP工具配置: {}", row));
    }
}

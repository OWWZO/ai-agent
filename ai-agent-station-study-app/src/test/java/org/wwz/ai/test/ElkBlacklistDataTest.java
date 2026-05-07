package org.wwz.ai.test;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟向Elasticsearch写入拼团项目黑名单限流数据的测试类
 * 基于 elk-blacklist-data.sh 脚本的Java实现
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ElkBlacklistDataTest {

    // Elasticsearch配置
    private static final String ES_HOST = "localhost:9200";
    private static final String INDEX_NAME_PREFIX = "group-buy-market-log-";

    // 模拟数据
    private static final String[] USERS = {"user001", "user002", "user003", "user004", "user005",
                                          "user006", "user007", "user008", "user009", "user010"};

    private static final String[] IPS = {"192.168.1.100", "192.168.1.101", "192.168.1.102",
                                        "10.0.0.50", "10.0.0.51", "172.16.0.100", "172.16.0.101",
                                        "203.0.113.10", "203.0.113.11", "198.51.100.20"};

    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X)",
        "Mozilla/5.0 (Android 10; Mobile)",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"
    };

    private static final String[] GROUP_BUY_PRODUCTS = {"product_001", "product_002", "product_003",
                                                       "product_004", "product_005"};

    private static final String[] LIMIT_REASONS = {"访问频率过高", "恶意刷单", "异常IP访问",
                                                  "超过每日限制", "黑名单用户"};

    private static final String[] LIMIT_TYPES = {"rate_limit", "frequency_limit", "ip_blacklist",
                                                "daily_limit", "user_blacklist"};

    private static final String[] LOG_LEVELS = {"ERROR", "WARN", "INFO"};

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    public void testWriteBlacklistDataToElasticsearch() {
        String indexName = INDEX_NAME_PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        String esUrl = "http://" + ES_HOST;

        log.info("开始向Elasticsearch模拟写入拼团项目黑名单限流数据...");
        log.info("目标索引: {}", indexName);
        log.info("ES地址: {}", esUrl);

        // 检查Elasticsearch连接
        if (!checkElasticsearchConnection(esUrl)) {
            log.error("无法连接到Elasticsearch ({})", esUrl);
            log.error("请确保Elasticsearch服务正在运行");
            return;
        }
        log.info("Elasticsearch连接正常");

        // 批量写入数据
        log.info("开始生成并写入模拟数据...");
        int successCount = 0;
        int totalCount = 50;

        for (int i = 1; i <= totalCount; i++) {
            log.info("写入第 {} 条数据...", i);

            // 生成日志数据
            Map<String, Object> logData = generateLogData();

            // 写入到Elasticsearch
            boolean success = writeToElasticsearch(esUrl, indexName, logData);

            if (success) {
                successCount++;
                log.info("第 {} 条数据写入成功", i);
            } else {
                log.error("第 {} 条数据写入失败", i);
            }

            // 随机延迟，模拟真实场景
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("数据写入完成！成功: {}/{}", successCount, totalCount);

        // 查看索引信息
        getIndexCount(esUrl, indexName);

        log.info("可以使用以下命令查看写入的数据:");
        log.info("curl -X GET \"http://{}/{}/_search?pretty&size=5\"", ES_HOST, indexName);
        log.info("或者在Kibana中查看索引: {}", indexName);
    }

    /**
     * 检查Elasticsearch连接
     */
    private boolean checkElasticsearchConnection(String esUrl) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(esUrl + "/_cluster/health", String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("检查Elasticsearch连接失败", e);
            return false;
        }
    }

    /**
     * 生成模拟限流日志数据
     */
    private Map<String, Object> generateLogData() {
        Random random = ThreadLocalRandom.current();

        String userId = USERS[random.nextInt(USERS.length)];
        String ip = IPS[random.nextInt(IPS.length)];
        String userAgent = USER_AGENTS[random.nextInt(USER_AGENTS.length)];
        String product = GROUP_BUY_PRODUCTS[random.nextInt(GROUP_BUY_PRODUCTS.length)];
        String limitReason = LIMIT_REASONS[random.nextInt(LIMIT_REASONS.length)];
        String limitType = LIMIT_TYPES[random.nextInt(LIMIT_TYPES.length)];
        String logLevel = LOG_LEVELS[random.nextInt(LOG_LEVELS.length)];

        // 生成随机时间戳（最近24小时内）
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime randomTime = now.minusSeconds(random.nextInt(86400)); // 24小时内随机
        String timestamp = randomTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";

        int requestCount = random.nextInt(100) + 50; // 50-149次请求
        int limitThreshold = random.nextInt(50) + 20; // 20-69的限制阈值
        int threadNum = random.nextInt(10) + 1;
        int responseTime = random.nextInt(100) + 10;

        String message = String.format("用户访问拼团项目被限流 - 用户ID: %s, 产品: %s, 原因: %s, IP: %s, 请求次数: %d, 限制阈值: %d",
                userId, product, limitReason, ip, requestCount, limitThreshold);

        Map<String, Object> logData = new HashMap<>();
        logData.put("@timestamp", timestamp);
        logData.put("level", logLevel);
        logData.put("logger", "com.fuzhengwei.security.RateLimitFilter");
        logData.put("thread", "http-nio-8080-exec-" + threadNum);
        logData.put("message", message);
        logData.put("application", "group-buy-market");
        logData.put("environment", "production");
        logData.put("service", "group-buy-service");
        logData.put("user_id", userId);
        logData.put("ip_address", ip);
        logData.put("user_agent", userAgent);
        logData.put("product_id", product);
        logData.put("limit_type", limitType);
        logData.put("limit_reason", limitReason);
        logData.put("request_count", requestCount);
        logData.put("limit_threshold", limitThreshold);
        logData.put("action", "blocked");
        logData.put("endpoint", "/api/group-buy/join");
        logData.put("method", "POST");
        logData.put("status_code", 429);
        logData.put("response_time", responseTime);
        logData.put("session_id", "session_" + System.currentTimeMillis() + "_" + random.nextInt(10000));
        logData.put("trace_id", "trace_" + System.currentTimeMillis() + "_" + random.nextInt(10000));
        logData.put("tags", Arrays.asList("限流", "黑名单", "拼团", "安全"));

        return logData;
    }

    /**
     * 写入数据到Elasticsearch
     */
    private boolean writeToElasticsearch(String esUrl, String indexName, Map<String, Object> logData) {
        try {
            String url = esUrl + "/" + indexName + "/_doc";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String jsonData = JSON.toJSONString(logData);
            HttpEntity<String> request = new HttpEntity<>(jsonData, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                String responseBody = response.getBody();
                return responseBody != null && responseBody.contains("\"result\":\"created\"");
            }

            return false;
        } catch (Exception e) {
            log.error("写入Elasticsearch失败", e);
            return false;
        }
    }

    /**
     * 获取索引文档数量
     */
    private void getIndexCount(String esUrl, String indexName) {
        try {
            String url = esUrl + "/" + indexName + "/_count";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("索引信息: {}", response.getBody());
            }
        } catch (Exception e) {
            log.warn("获取索引信息失败", e);
        }
    }
}

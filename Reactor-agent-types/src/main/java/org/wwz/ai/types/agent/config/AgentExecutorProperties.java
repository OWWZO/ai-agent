package org.wwz.ai.types.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 主链路执行器与 visitor Cookie 配置。
 */
@Data
@ConfigurationProperties(prefix = "autobots.execution")
public class AgentExecutorProperties {

    private Pool dispatch = Pool.dispatchDefault();

    private Pool llm = Pool.llmDefault();

    private Pool task = Pool.taskDefault();

    private Pool tool = Pool.toolDefault();

    private Heartbeat heartbeat = Heartbeat.defaultValue();

    private VisitorCookie visitorCookie = VisitorCookie.defaultValue();

    /**
     * 工具批 allOf 超时（秒）。超时后未完成工具标记 failed，避免 UI 永久 running。
     */
    private Long toolBatchTimeoutSeconds = 600L;

    /**
     * 全局同时运行的同步子 Agent 上限，防止 task/tool 池被嵌套 Agent 打满。
     */
    private Integer maxConcurrentSubAgents = 4;

    /**
     * 获取子 Agent 并发许可的等待超时（秒）；超时则该次 Agent 派发失败。
     */
    private Long subAgentAcquireTimeoutSeconds = 30L;

    @Data
    public static class Pool {

        private Integer corePoolSize;
        private Integer maxPoolSize;
        private Integer queueCapacity;
        private Long keepAliveSeconds;
        private String rejectPolicy;
        private String threadNamePrefix;

        public static Pool dispatchDefault() {
            Pool pool = new Pool();
            pool.setCorePoolSize(16);
            pool.setMaxPoolSize(32);
            pool.setQueueCapacity(200);
            pool.setKeepAliveSeconds(60L);
            pool.setRejectPolicy("AbortPolicy");
            pool.setThreadNamePrefix("agent-dispatch-");
            return pool;
        }

        public static Pool llmDefault() {
            Pool pool = new Pool();
            pool.setCorePoolSize(16);
            pool.setMaxPoolSize(32);
            pool.setQueueCapacity(100);
            pool.setKeepAliveSeconds(60L);
            pool.setRejectPolicy("AbortPolicy");
            pool.setThreadNamePrefix("agent-llm-");
            return pool;
        }

        public static Pool toolDefault() {
            Pool pool = new Pool();
            pool.setCorePoolSize(8);
            pool.setMaxPoolSize(16);
            pool.setQueueCapacity(50);
            pool.setKeepAliveSeconds(60L);
            pool.setRejectPolicy("AbortPolicy");
            pool.setThreadNamePrefix("agent-tool-");
            return pool;
        }

        public static Pool taskDefault() {
            Pool pool = new Pool();
            pool.setCorePoolSize(8);
            pool.setMaxPoolSize(16);
            pool.setQueueCapacity(50);
            pool.setKeepAliveSeconds(60L);
            pool.setRejectPolicy("AbortPolicy");
            pool.setThreadNamePrefix("agent-task-");
            return pool;
        }
    }

    @Data
    public static class Heartbeat {

        private Integer poolSize;
        private String threadNamePrefix;
        private Long intervalMillis;

        public static Heartbeat defaultValue() {
            Heartbeat heartbeat = new Heartbeat();
            heartbeat.setPoolSize(2);
            heartbeat.setThreadNamePrefix("agent-heartbeat-");
            heartbeat.setIntervalMillis(10_000L);
            return heartbeat;
        }
    }

    @Data
    public static class VisitorCookie {

        private String name;
        private boolean httpOnly;
        private boolean secure;
        private String sameSite;
        private String path;
        private Long maxAgeDays;
        private List<String> allowedOrigins = new ArrayList<>();

        public static VisitorCookie defaultValue() {
            VisitorCookie cookie = new VisitorCookie();
            cookie.setName("ai_agent_visitor_token");
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setSameSite("Lax");
            cookie.setPath("/");
            cookie.setMaxAgeDays(365L);
            cookie.setAllowedOrigins(new ArrayList<>());
            return cookie;
        }
    }
}

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

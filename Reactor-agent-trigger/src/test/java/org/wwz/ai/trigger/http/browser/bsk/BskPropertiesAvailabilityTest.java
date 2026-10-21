package org.wwz.ai.trigger.http.browser.bsk;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.wwz.ai.trigger.config.BskPropertiesConfig;

import java.util.Map;

/**
 * 关掉 {@code reactor.browser-bsk.enabled} 时 {@code BskProperties} 必须仍然存在。
 *
 * <p>bsk 包内的两个 {@code @Configuration} 都带
 * {@code @ConditionalOnProperty("reactor.browser-bsk.enabled")}，它们身上的
 * {@code @EnableConfigurationProperties(BskProperties.class)} 会一并失效；
 * 而 {@code BrowserPairingController} 是无条件注册的、构造器里又要这个 bean——
 * 一旦缺失，<b>关掉 bsk 反而让整个应用启动失败</b>。</p>
 *
 * <p>这里刻意只注册 {@link BskPropertiesConfig}（不带任何 bsk 端点配置），
 * 用来代表「开关关掉」的那条路径。</p>
 */
public class BskPropertiesAvailabilityTest {

    @Test
    public void propertiesBeanExistsWithoutTheBskEndpointConfigurations() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(BskPropertiesConfig.class, Host.class);
            context.refresh();

            BskProperties properties = context.getBean(BskProperties.class);
            Assert.assertNotNull(properties);
            // 默认值必须可用：开关关掉时 Controller 只是不生成 bskPairingUrl。
            Assert.assertTrue(properties.isEnabled());
            Assert.assertEquals("/api/agent/browser/bsk-relay", properties.getPath());
            Assert.assertEquals(60, properties.getSessionTimeoutSeconds());
        }
    }

    @Test
    public void disabledFlagIsReadFromTheEnvironment() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("reactor.browser-bsk.enabled", "false")));
            context.register(BskPropertiesConfig.class, Host.class);
            context.refresh();
            Assert.assertFalse(context.getBean(BskProperties.class).isEnabled());
        }
    }

    /**
     * 模拟 {@code BrowserPairingController}：一个<b>无条件</b>注册的 bean，
     * 构造器里必须注入到 {@code BskProperties}。
     */
    @Configuration
    static class Host {
        @Bean
        public BskPropertiesConsumer consumer(BskProperties properties) {
            return new BskPropertiesConsumer(properties);
        }
    }

    /** 只为了把注入点坐实：注入不到的话 refresh() 阶段就抛异常。 */
    static class BskPropertiesConsumer {
        final BskProperties properties;

        BskPropertiesConsumer(BskProperties properties) {
            this.properties = properties;
        }
    }
}

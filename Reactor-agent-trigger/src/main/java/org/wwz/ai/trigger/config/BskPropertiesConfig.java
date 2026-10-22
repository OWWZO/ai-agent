package org.wwz.ai.trigger.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.trigger.http.browser.bsk.BskProperties;

/**
 * 无条件注册 {@link BskProperties}。
 *
 * <p>bsk 包内的两个 {@code @Configuration}（{@code BskRelayWebSocketConfig} /
 * {@code BskRelayBeansConfig}）都带
 * {@code @ConditionalOnProperty("cute.browser-bsk.enabled")}，而这两个类上的
 * {@code @EnableConfigurationProperties} 也随之失效——于是关掉开关后
 * {@code BskProperties} 这个 bean 根本不存在，而
 * {@code BrowserPairingController}（无条件注册）构造器里要它，
 * 结果就是<b>关掉 bsk 反而整个应用启动失败</b>。</p>
 *
 * <p>配置类必须与「用不用这个开关」解耦：开关决定要不要注册 WS 端点，
 * 不决定配置对象本身在不在。这里按仓库既有的
 * {@code BrowserRelayPropertiesConfig} 模式，无条件注册一份。</p>
 */
@Configuration
@EnableConfigurationProperties(BskProperties.class)
public class BskPropertiesConfig {
}

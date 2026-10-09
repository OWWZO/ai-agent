package org.wwz.ai.trigger.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.wwz.ai.application.auth.IAuthApplicationService;
import org.wwz.ai.trigger.http.auth.AuthenticationFilter;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;

/**
 * 触发层基础过滤器配置。
 *
 * <p>过滤器顺序是本类的关键契约：CORS 先处理跨域响应，认证过滤器注册顺序由认证整合接管。</p>
 */
@Configuration
@EnableConfigurationProperties(AgentExecutorProperties.class)
public class BaseFilterConfig {
	public BaseFilterConfig() {
	}

	@Bean
	public FilterRegistrationBean<CorsFilter> corsFilter(AgentExecutorProperties properties) {
		// 仅在配置提供允许来源时启用凭证；否则保留通配来源的无凭证跨域行为。
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

		CorsConfiguration browser = new CorsConfiguration();
		if (CollectionUtils.isNotEmpty(properties.getVisitorCookie().getAllowedOrigins())) {
			browser.setAllowCredentials(true);
			browser.setAllowedOrigins(properties.getVisitorCookie().getAllowedOrigins());
		}
		browser.addAllowedOriginPattern("chrome-extension://*");
		browser.addAllowedHeader("*");
		browser.addAllowedMethod("*");
		source.registerCorsConfiguration("/api/agent/browser/**", browser);

		CorsConfiguration config = new CorsConfiguration();
		if (CollectionUtils.isNotEmpty(properties.getVisitorCookie().getAllowedOrigins())) {
			config.setAllowCredentials(true);
			config.setAllowedOrigins(properties.getVisitorCookie().getAllowedOrigins());
		}
		config.addAllowedHeader("*");
		config.addAllowedMethod("*");
		source.registerCorsConfiguration("/**", config);
		CorsFilter corsFilter = new CorsFilter(source);
		return this.creatAllFilter(corsFilter, 1);
	}

	@Bean
    public FilterRegistrationBean<AuthenticationFilter> authenticationFilter(
            IAuthApplicationService authApplicationService,
            ObjectMapper objectMapper) {
        return this.creatAllFilter(
                new AuthenticationFilter(authApplicationService, objectMapper),
                2
        );
    }


	<T extends Filter> FilterRegistrationBean<T> creatAllFilter(T filter, int order) {
		// 统一注册到全部 URL，具体过滤器通过自身逻辑决定是否需要处理当前请求。
		return this.createFilter(filter, order, "/*");
	}

	<T extends Filter> FilterRegistrationBean<T> createFilter(T filter, int order, String... urlPatterns) {
		// 集中设置顺序、匹配路径和分发类型，避免各 Bean 的注册参数逐处漂移。
		FilterRegistrationBean<T> bean = new FilterRegistrationBean<>();
		bean.setFilter(filter);
		bean.setOrder(order);
		bean.addUrlPatterns(urlPatterns);
		bean.setDispatcherTypes(DispatcherType.REQUEST, new DispatcherType[0]);
		return bean;
	}
}

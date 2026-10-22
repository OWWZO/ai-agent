package org.wwz.ai.application.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Shared time source for authentication expiry decisions.
 */
@Configuration(proxyBeanMethods = false)
public class AuthConfiguration {

    @Bean
    public Clock authClock() {
        return Clock.systemUTC();
    }
}

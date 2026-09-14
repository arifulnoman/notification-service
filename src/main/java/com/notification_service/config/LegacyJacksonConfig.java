package com.notification_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring Boot 4's Jackson auto-configuration only registers a
 * tools.jackson.databind.ObjectMapper (Jackson 3) bean. Classic Jackson 2
 * (com.fasterxml.jackson.databind) is still on the classpath, but only
 * transitively via jjwt-jackson, which needs it internally — nothing
 * registers it as a bean. JwtTokenProvider and
 * DynamicNotificationListenerRegistrar are written against the classic
 * Jackson 2 API, so this bean fills that gap.
 */
@Configuration
public class LegacyJacksonConfig {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}

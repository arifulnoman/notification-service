package com.notification_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client used only to call a registered service's userLookupUrl at
 * notification-read time. Kept aggressively short-timeout and separate from
 * any other RestClient in the app: a slow/down integration's lookup
 * endpoint must never noticeably slow down — let alone hang — the
 * notification list API for its users.
 */
@Configuration
public class UserLookupClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 500;
    private static final int READ_TIMEOUT_MS = 800;

    @Bean
    RestClient userLookupRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return RestClient.builder().requestFactory(factory).build();
    }
}

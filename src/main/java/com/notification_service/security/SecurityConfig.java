package com.notification_service.security;

import java.util.Map;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AdminApiKeyFilter adminApiKeyFilter;
    private final ObjectMapper objectMapper;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(org.springframework.security.config.Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Allow WebSocket handshake endpoints to be open initially (upgrading HTTP to WS).
                // The interceptor handles the actual STOMP CONNECT frame security.
                .requestMatchers("/ws-notifications/**").permitAll()
                .requestMatchers("/").permitAll()
                // Service registry onboarding — guarded by AdminApiKeyFilter below, not end-user JWTs.
                .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(adminApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // Spring Security's defaults return an empty body on both of these — replace them
            // with a JSON message so a rejected request (wrong path, missing/wrong credential,
            // right credential but wrong kind — e.g. a JWT on /api/admin/**) is never silent.
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) ->
                        writeJsonError(response, HttpStatus.UNAUTHORIZED,
                                "Authentication required or invalid: " + authException.getMessage()))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        writeJsonError(response, HttpStatus.FORBIDDEN,
                                "Not authorized for [" + request.getRequestURI() + "]: "
                                        + accessDeniedException.getMessage())));

        return http.build();
    }

    /**
     * AdminApiKeyFilter and JwtAuthenticationFilter are @Component beans, so Spring Boot
     * would ALSO auto-register each as its own standalone global servlet filter (at
     * LOWEST_PRECEDENCE — after Spring Security's whole chain has already run and
     * committed a response) in addition to the position wired above via
     * addFilterBefore(...). That stray late copy is what produced the blank,
     * wrong-status responses seen from AdminApiKeyFilter's own sendError() calls —
     * disable the auto-registration so each filter only runs once, where intended.
     */
    @Bean
    FilterRegistrationBean<AdminApiKeyFilter> disableAdminApiKeyFilterAutoRegistration(AdminApiKeyFilter filter) {
        FilterRegistrationBean<AdminApiKeyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> disableJwtAuthenticationFilterAutoRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private void writeJsonError(jakarta.servlet.http.HttpServletResponse response, HttpStatus status, String message)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message));
    }
}

package com.notification_service.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Guards /api/admin/** with a static API key instead of the shared end-user
 * JWT — deliberately a separate, out-of-band credential. This way rotating
 * a registered service's JWT public key (done through this same admin API)
 * never depends on — or gets tangled with — the credential guarding this
 * API itself.
 */
@Slf4j
@Component
public class AdminApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Admin-Api-Key";
    private static final String ADMIN_PATH_PREFIX = "/api/admin/";

    @Value("${admin.api-key}")
    private String expectedApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(ADMIN_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(HEADER);
        if (!StringUtils.hasText(providedKey) || !isValid(providedKey)) {
            log.warn("Rejected admin request to [{}] — missing or invalid {}", request.getRequestURI(), HEADER);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing " + HEADER);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        filterChain.doFilter(request, response);
    }

    private boolean isValid(String providedKey) {
        byte[] provided = providedKey.getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedApiKey.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(provided, expected);
    }
}

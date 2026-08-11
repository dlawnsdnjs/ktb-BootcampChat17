package com.ktb.chatapp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
/**
 * 실패 요청 로깅 필터.
 * 정상 요청은 Micrometer에서 집계하고, 원인 파악에 필요한 404 URL만 남긴다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        filterChain.doFilter(request, response);

        // Keep this production-safe and limited to failures needed for load-test diagnosis.
        // Query strings may contain sensitive values, so only log the normalized request URI.
        if (response.getStatus() == HttpServletResponse.SC_NOT_FOUND) {
            log.warn("HTTP 404: method={}, uri={}", request.getMethod(), request.getRequestURI());
        }
    }

}

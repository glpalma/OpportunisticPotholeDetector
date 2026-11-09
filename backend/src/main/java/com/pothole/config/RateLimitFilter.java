package com.pothole.config;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final boolean enabled;

    private final Bandwidth requestLimit;

    private final Bandwidth authFailureLimit;

    private final ConcurrentMap<String, Bucket> requestBuckets = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Bucket> authFailureBuckets = new ConcurrentHashMap<>();

    RateLimitFilter(PotholeProperties properties) {
        PotholeProperties.RateLimit rateLimit = properties.getRateLimit();
        this.enabled = rateLimit.isEnabled();
        int burst = rateLimit.getBurst();
        int perSecond = Math.min(rateLimit.getRequestsPerSecond(), burst);
        this.requestLimit = Bandwidth.builder()
                .capacity(burst)
                .refillGreedy(perSecond, Duration.ofSeconds(1))
                .build();
        int authFailures = rateLimit.getAuthFailuresPerMinute();
        this.authFailureLimit = Bandwidth.builder()
                .capacity(authFailures)
                .refillGreedy(authFailures, Duration.ofMinutes(1))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || !isClientApi(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String ip = request.getRemoteAddr();
        Bucket authFailures = authFailureBuckets.computeIfAbsent(ip, key -> Bucket.builder().addLimit(authFailureLimit).build());
        if (authFailures.getAvailableTokens() < 1) {
            EstimationProbe estimation = authFailures.estimateAbilityToConsume(1);
            reject(response, estimation.getNanosToWaitForRefill());
            return;
        }
        Bucket requests = requestBuckets.computeIfAbsent(ip, key -> Bucket.builder().addLimit(requestLimit).build());
        ConsumptionProbe probe = requests.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            reject(response, probe.getNanosToWaitForRefill());
            return;
        }
        StatusCapture captured = new StatusCapture(response);
        filterChain.doFilter(request, captured);
        if (captured.status() == HttpServletResponse.SC_UNAUTHORIZED) {
            authFailures.tryConsume(1);
        }
    }

    private static boolean isClientApi(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return path.startsWith("/v1/");
    }

    private static void reject(HttpServletResponse response, long nanos) {
        long seconds = Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(seconds));
    }

    private static final class StatusCapture extends HttpServletResponseWrapper {

        private int status = 200;

        private StatusCapture(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int status) {
            this.status = status;
            super.setStatus(status);
        }

        @Override
        public void sendError(int status) throws IOException {
            this.status = status;
            super.sendError(status);
        }

        @Override
        public void sendError(int status, String message) throws IOException {
            this.status = status;
            super.sendError(status, message);
        }

        private int status() {
            return status;
        }
    }
}

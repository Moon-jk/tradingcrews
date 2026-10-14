package com.pj.stock_console.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 🛑 IP 기반 Rate Limiting 필터
 *
 * - 1분 윈도우 기반 요청 수 제한
 * - 로그인 시도: 10회/분
 * - 일반 API: 120회/분
 * - 제한 초과 시 429 Too Many Requests 반환
 */
@Component
@Order(1) // SecurityRequestFilter보다 먼저 실행
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // IP별 {windowKey -> count} 저장
    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> windowStartTimes = new ConcurrentHashMap<>();

    private static final int LOGIN_LIMIT = 10;       // 로그인 시도: 10회/분
    private static final int GENERAL_LIMIT = 120;    // 일반 요청: 120회/분
    private static final long WINDOW_MS = 60_000L;   // 1분 윈도우

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = getClientIp(request);
        String path = request.getRequestURI();
        String method = request.getMethod();

        // 로그인 시도인지 판별
        boolean isLoginAttempt = "POST".equalsIgnoreCase(method) && "/login".equals(path);
        String bucketKey = clientIp + (isLoginAttempt ? ":login" : ":general");
        int limit = isLoginAttempt ? LOGIN_LIMIT : GENERAL_LIMIT;

        // 윈도우 만료 체크 → 리셋
        long now = System.currentTimeMillis();
        Long windowStart = windowStartTimes.get(bucketKey);
        if (windowStart == null || (now - windowStart) > WINDOW_MS) {
            windowStartTimes.put(bucketKey, now);
            requestCounts.put(bucketKey, new AtomicInteger(0));
        }

        // 카운트 증가
        int count = requestCounts
                .computeIfAbsent(bucketKey, k -> new AtomicInteger(0))
                .incrementAndGet();

        // 제한 초과
        if (count > limit) {
            if (count == limit + 1) {
                // 처음 초과 시에만 로그 남기기 (로그 폭탄 방지)
                log.warn("🛑 RATE LIMIT exceeded: {} | Bucket: {} | Count: {}/{}", clientIp, bucketKey, count, limit);
            }

            response.setStatus(429); // Too Many Requests
            response.setContentType("text/plain;charset=UTF-8");
            response.setHeader("Retry-After", "60");
            response.getWriter().write(isLoginAttempt
                    ? "로그인 시도가 너무 많습니다. 1분 후 다시 시도해주세요."
                    : "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 정적 리소스는 Rate Limit 대상 제외
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/vendor/") || path.startsWith("/favicon")
                || path.startsWith("/card-assets/") || path.endsWith(".png") || path.endsWith(".jpg")
                || path.endsWith(".woff2") || path.endsWith(".css") || path.endsWith(".js");
    }
}

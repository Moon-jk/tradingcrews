package com.pj.stock_console.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 🛡️ SQL Injection / XSS 탐지 및 차단 필터
 *
 * - 모든 HTTP 요청의 파라미터, URL, 쿼리스트링을 검사
 * - SQL Injection 패턴 감지 시: 로그 기록 + 403 차단
 * - 반복 공격 IP 자동 블랙리스트 (10회 이상 → 1시간 차단)
 */
@Component
public class SecurityRequestFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityRequestFilter.class);

    // ── SQL Injection 패턴 (Time-based, Union, Fingerprinting 등) ──
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(" +
                    // Time-based blind injection
                    "sleep\\s*\\(|pg_sleep\\s*\\(|waitfor\\s+delay|benchmark\\s*\\(" +
                    "|dbms_pipe\\.receive_message" +
                    // Union / Select injection
                    "|union\\s+(all\\s+)?select|select\\s+.+\\s+from\\s+" +
                    // Comment / termination
                    "|;\\s*(drop|alter|create|insert|update|delete|truncate)\\s+" +
                    // Information schema probing
                    "|information_schema|sysobjects|syscolumns" +
                    // File operations
                    "|load_file\\s*\\(|into\\s+(out|dump)file" +
                    // Common bypass techniques
                    "|0x[0-9a-f]{8,}" +  // hex-encoded strings
                    "|char\\s*\\(\\s*\\d+|concat\\s*\\(.*select" +
                    // Oracle specific
                    "|from\\s+dual|utl_http|utl_inaddr" +
                    // Stacked queries
                    "|;\\s*select\\s+|;\\s*exec\\s+" +
                    // XOR / boolean blind
                    "|'\\s*xor\\s*\\(" +
                    ")"
    );

    // ── XSS 패턴 ──
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)(<script[^>]*>|javascript\\s*:|on(error|load|click|mouseover|focus)\\s*=)",
            Pattern.CASE_INSENSITIVE
    );

    // ── IP별 공격 시도 카운터 ──
    private final ConcurrentHashMap<String, AtomicInteger> attackCountMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> blockedIpMap = new ConcurrentHashMap<>();

    private static final int MAX_ATTACK_ATTEMPTS = 10;          // 10회 이상 시 차단
    private static final long BLOCK_DURATION_MS = 3600_000L;    // 1시간 차단

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = getClientIp(request);

        // ── 1단계: 블랙리스트 IP 체크 ──
        if (isBlocked(clientIp)) {
            log.warn("🚫 BLOCKED IP attempt: {} → {} {}", clientIp, request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Access Denied");
            return;
        }

        // ── 2단계: URL + 쿼리스트링 검사 ──
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();

        if (queryString != null && isMalicious(queryString)) {
            handleAttack(clientIp, request, "QUERY_STRING", queryString, response);
            return;
        }

        if (isMalicious(requestUri)) {
            handleAttack(clientIp, request, "URI", requestUri, response);
            return;
        }

        // ── 3단계: 모든 파라미터 검사 ──
        Map<String, String[]> params = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String paramName = entry.getKey();
            for (String value : entry.getValue()) {
                if (value != null && isMalicious(value)) {
                    handleAttack(clientIp, request, "PARAM[" + paramName + "]", value, response);
                    return;
                }
            }
        }

        // ── 4단계: 안전한 요청 → 통과 ──
        filterChain.doFilter(request, response);
    }

    /**
     * SQL Injection 또는 XSS 패턴이 포함되어 있는지 검사
     */
    private boolean isMalicious(String input) {
        if (input == null || input.isEmpty()) return false;

        // URL 디코딩된 문자열도 검사 (Double Encoding 우회 방지)
        String decoded = input;
        try {
            decoded = java.net.URLDecoder.decode(input, "UTF-8");
        } catch (Exception ignored) {
            // 디코딩 실패 시 원본으로 검사
        }

        return SQL_INJECTION_PATTERN.matcher(decoded).find()
                || XSS_PATTERN.matcher(decoded).find();
    }

    /**
     * 공격 감지 시 로깅 + 카운터 증가 + 차단 응답
     */
    private void handleAttack(String clientIp, HttpServletRequest request,
                              String source, String maliciousValue,
                              HttpServletResponse response) throws IOException {
        // 카운터 증가
        int count = attackCountMap
                .computeIfAbsent(clientIp, k -> new AtomicInteger(0))
                .incrementAndGet();

        // 로그 기록 (공격 추적용)
        log.warn("🚨 ATTACK DETECTED [#{} from {}] | Method: {} | URI: {} | Source: {} | Payload: {}",
                count, clientIp, request.getMethod(), request.getRequestURI(),
                source, truncate(maliciousValue, 200));

        // 반복 공격 시 IP 블랙리스트 등록
        if (count >= MAX_ATTACK_ATTEMPTS) {
            blockedIpMap.put(clientIp, System.currentTimeMillis());
            log.error("🔒 IP BLOCKED for 1 hour: {} (after {} attack attempts)", clientIp, count);
        }

        // 403 응답 (최소 정보만 반환)
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("Bad Request");
    }

    /**
     * IP가 블랙리스트에 있고 차단 시간이 아직 유효한지 확인
     */
    private boolean isBlocked(String ip) {
        Long blockedAt = blockedIpMap.get(ip);
        if (blockedAt == null) return false;

        if (System.currentTimeMillis() - blockedAt > BLOCK_DURATION_MS) {
            // 차단 시간 만료 → 해제
            blockedIpMap.remove(ip);
            attackCountMap.remove(ip);
            return false;
        }
        return true;
    }

    /**
     * 실제 클라이언트 IP 추출 (프록시/로드밸런서 환경 대응)
     */
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

    /**
     * 로그 출력용 문자열 자르기
     */
    private String truncate(String value, int maxLen) {
        if (value == null) return "null";
        return value.length() > maxLen ? value.substring(0, maxLen) + "..." : value;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 정적 리소스는 검사 제외 (성능 최적화)
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/vendor/") || path.startsWith("/favicon")
                || path.startsWith("/card-assets/") || path.endsWith(".png") || path.endsWith(".jpg")
                || path.endsWith(".woff2") || path.endsWith(".css") || path.endsWith(".js");
    }
}

package com.pj.stock_console.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * 🛡️ 글로벌 에러 핸들러
 *
 * - 내부 에러 메시지(스택트레이스, DB 정보)가 사용자에게 노출되는 것을 방지
 * - 모든 예외를 중앙에서 로깅하고, 사용자에게는 일반적인 메시지만 반환
 * - API(JSON) 요청과 페이지(HTML) 요청을 구분하여 처리
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 필수 파라미터 누락 (400 Bad Request)
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingParam(MissingServletRequestParameterException ex,
                                     HttpServletRequest request) {
        log.warn("⚠️ Missing parameter: {} in {} {}", ex.getParameterName(), request.getMethod(), request.getRequestURI());

        if (isApiRequest(request)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "필수 파라미터가 누락되었습니다: " + ex.getParameterName()
            ));
        }
        return errorPage("잘못된 요청입니다.", HttpStatus.BAD_REQUEST);
    }

    /**
     * 존재하지 않는 데이터 (404 Not Found)
     */
    @ExceptionHandler({NoResourceFoundException.class})
    public Object handleNotFound(Exception ex, HttpServletRequest request) {
        // 정적 리소스 404는 로깅 생략 (과도한 로그 방지)
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "요청한 리소스를 찾을 수 없습니다."
            ));
        }
        return errorPage("페이지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
    }

    /**
     * 인가 실패 (403 Forbidden)
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public Object handleAccessDenied(Exception ex, HttpServletRequest request) {
        log.warn("🚫 Access denied: {} {} from {}", request.getMethod(), request.getRequestURI(), getClientIp(request));

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "접근 권한이 없습니다."
            ));
        }
        return errorPage("접근 권한이 없습니다.", HttpStatus.FORBIDDEN);
    }

    /**
     * IllegalArgumentException (잘못된 입력)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArg(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("⚠️ IllegalArgument: {} | URI: {}", ex.getMessage(), request.getRequestURI());

        if (isApiRequest(request)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "잘못된 요청입니다."
            ));
        }
        return errorPage("잘못된 요청입니다.", HttpStatus.BAD_REQUEST);
    }

    /**
     * 그 외 모든 예외 (500 Internal Server Error)
     * ⚠️ 핵심: 내부 에러 메시지를 절대 사용자에게 노출하지 않음
     */
    @ExceptionHandler(Exception.class)
    public Object handleGlobalException(Exception ex, HttpServletRequest request) {
        // 내부 로그에는 상세 정보 기록
        log.error("🔥 Unhandled exception at {} {} from {}: {}",
                request.getMethod(), request.getRequestURI(), getClientIp(request), ex.getMessage(), ex);

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
            ));
        }
        return errorPage("서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── 유틸리티 ──

    /**
     * API 요청 여부 판별 (Accept 헤더 또는 URL 패턴 기반)
     */
    private boolean isApiRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        return (accept != null && accept.contains("application/json"))
                || uri.startsWith("/api/")
                || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    /**
     * HTML 에러 페이지 반환 (Thymeleaf error 템플릿 사용)
     */
    private ModelAndView errorPage(String message, HttpStatus status) {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("errorMessage", message);
        mav.addObject("statusCode", status.value());
        mav.setStatus(status);
        return mav;
    }

    /**
     * 클라이언트 IP 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

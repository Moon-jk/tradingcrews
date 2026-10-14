package com.pj.stock_console.security;

import java.util.regex.Pattern;

/**
 * 🔍 입력값 검증 유틸리티
 *
 * - 컨트롤러에서 사용자 입력을 검증할 때 사용
 * - 길이 제한, 패턴 매칭, 금지 문자 필터링
 */
public final class InputValidator {

    private InputValidator() {} // 인스턴스 생성 방지

    // ── 허용 패턴 정의 ──

    /** 로그인 ID: 영문, 숫자, -, _, @, . 만 허용 (이메일 대응) */
    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._@-]{3,50}$");

    /** 닉네임: 한글, 영문, 숫자, 공백, -, _ 만 허용 */
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9 _-]{2,20}$");

    /** 종목 심볼: 영문 대소문자, 숫자, -, . 만 허용 */
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Za-z0-9.-]{1,20}$");

    /** 일반 검색어: 한글, 영문, 숫자, 공백, 기본 문장부호만 허용 */
    private static final Pattern SEARCH_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9 .,!?~@#%&*()_+-]{1,100}$");

    // ── 검증 메서드 ──

    public static boolean isValidLoginId(String loginId) {
        return loginId != null && LOGIN_ID_PATTERN.matcher(loginId).matches();
    }

    public static boolean isValidNickname(String nickname) {
        return nickname != null && NICKNAME_PATTERN.matcher(nickname).matches();
    }

    public static boolean isValidSymbol(String symbol) {
        return symbol != null && SYMBOL_PATTERN.matcher(symbol).matches();
    }

    public static boolean isValidSearchQuery(String query) {
        return query != null && SEARCH_PATTERN.matcher(query).matches();
    }

    /**
     * 범용 텍스트 검증 (제목, 내용 등)
     * @param text 검증할 텍스트
     * @param maxLength 최대 허용 길이
     * @return 유효하면 true
     */
    public static boolean isValidText(String text, int maxLength) {
        return text != null && !text.isBlank() && text.length() <= maxLength;
    }

    /**
     * 입력값 길이 제한 (초과 시 자르기)
     */
    public static String sanitize(String input, int maxLength) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.length() > maxLength) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed;
    }

    /**
     * 크루/게시판 이름 검증: 한글, 영문, 숫자, 공백, 기본 기호만 허용
     */
    public static boolean isValidName(String name, int maxLength) {
        if (name == null || name.isBlank() || name.length() > maxLength) return false;
        return Pattern.matches("^[가-힣a-zA-Z0-9 _.,!?&()-]{1," + maxLength + "}$", name);
    }
}

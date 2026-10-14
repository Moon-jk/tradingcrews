package com.pj.stock_console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Frankfurter API (ECB 기반) 환율 조회
 *
 * - 완전 무료, API 키 없음, 무제한
 * - 영업일 기준 하루 1회 갱신 (실환율 비교용으로 충분)
 * - 1시간 캐싱 (실제 갱신 주기보다 짧아 항상 최신 보장)
 *
 * 엔드포인트: https://api.frankfurter.app/latest?from=USD&to=KRW
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String URL =
            "https://api.frankfurter.app/latest?from={from}&to={to}";

    /**
     * USD → KRW 환율 조회 (1시간 캐싱)
     * 예: 1 USD = 1,326.50 KRW
     */
    @Cacheable(value = "exchangeRate", key = "'USD_KRW'")
    public BigDecimal getUsdToKrw() {
        try {
            String url  = URL.replace("{from}", "USD").replace("{to}", "KRW");
            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(json);
            double rate   = root.path("rates").path("KRW").asDouble();
            if (rate > 0) {
                log.info("환율 갱신: 1 USD = {} KRW", rate);
                return BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.warn("환율 조회 실패: {}", e.getMessage());
        }
        // 조회 실패 시 fallback (최근 평균값)
        log.warn("환율 조회 실패 - fallback 1330.00 사용");
        return new BigDecimal("1330.00");
    }

    /**
     * KRW → USD 환율
     */
    public BigDecimal getKrwToUsd() {
        BigDecimal usdToKrw = getUsdToKrw();
        return BigDecimal.ONE.divide(usdToKrw, 8, RoundingMode.HALF_UP);
    }

    /**
     * USD 금액 → KRW 변환
     */
    public BigDecimal usdToKrw(BigDecimal usdAmount) {
        return usdAmount.multiply(getUsdToKrw()).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * KRW 금액 → USD 변환
     */
    public BigDecimal krwToUsd(BigDecimal krwAmount) {
        return krwAmount.multiply(getKrwToUsd()).setScale(4, RoundingMode.HALF_UP);
    }

    /** 1시간마다 캐시 초기화 → 다음 요청 시 자동 갱신 */
    @Scheduled(fixedDelay = 3_600_000)
    @CacheEvict(value = "exchangeRate", allEntries = true)
    public void evictExchangeRateCache() {
        log.debug("환율 캐시 초기화");
    }
}

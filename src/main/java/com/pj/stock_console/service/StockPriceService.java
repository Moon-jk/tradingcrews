package com.pj.stock_console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pj.stock_console.entity.TradingAccount.MarketType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 통합 주가 조회 서비스
 *
 * 미국(US): Finnhub API
 *   - 무료 분당 60건
 *   - https://finnhub.io → API 키 발급
 *   - application.properties: finnhub.api.key=YOUR_KEY
 *
 * 한국(KR): 한국투자증권 KIS Developers
 *   - 완전 무료, 실시간
 *   - https://apiportal.koreainvestment.com → 계좌 개설 후 신청
 *   - application.properties:
 *       kis.app-key=YOUR_APP_KEY
 *       kis.app-secret=YOUR_APP_SECRET
 *       kis.account-no=YOUR_ACCOUNT_NO
 *       kis.mock=true   (모의투자 URL 사용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /* ── Finnhub ── */
    @Value("${finnhub.api.key}")
    private String finnhubKey;

    private static final String FINNHUB_QUOTE =
            "https://finnhub.io/api/v1/quote?symbol={symbol}&token={token}";
    private static final String FINNHUB_SEARCH =
            "https://finnhub.io/api/v1/search?q={symbol}&token={token}";

    /* ── KIS ── */
    @Value("${kis.app-key:}") // 설정에 없어도 에러 발생 안 함
    private String kisAppKey;

    @Value("${kis.app-secret:}")
    private String kisAppSecret;

    @Value("${kis.mock:true}") // 설정에 없으면 기본값 true 사용
    private boolean kisMock;

    private String kisAccessToken = null;
    private long   kisTokenExpiry  = 0;

    private String kisBaseUrl() {
        return kisMock
                ? "https://openapivts.koreainvestment.com:29443"
                : "https://openapi.koreainvestment.com:9443";
    }

    /* ════════════════════════════════════════
       통합 진입점: marketType 으로 분기
       ════════════════════════════════════════ */

    @Cacheable(value = "stockPrice", key = "#marketType.name() + '_' + #symbol")
    public BigDecimal getCurrentPrice(String symbol, MarketType marketType) {
        return marketType == MarketType.US
                ? getFinnhubPrice(symbol)
                : getKisPrice(symbol);
    }

    public StockInfo getStockInfo(String symbol, MarketType marketType) {
        return marketType == MarketType.US
                ? getFinnhubInfo(symbol)
                : getKisInfo(symbol);
    }

    public Map<String, BigDecimal> getCurrentPrices(
            Iterable<String> symbols, MarketType marketType) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (String symbol : symbols) {
            result.put(symbol, getCurrentPrice(symbol, marketType));
            // rate limit 방어
            try { Thread.sleep(marketType == MarketType.US ? 100 : 50); }
            catch (InterruptedException ignored) {}
        }
        return result;
    }

    /* ════════════════════════════════════════
       미국 - Finnhub
       ════════════════════════════════════════ */

    private BigDecimal getFinnhubPrice(String symbol) {
        try {
            String url = FINNHUB_QUOTE
                    .replace("{symbol}", symbol)
                    .replace("{token}", finnhubKey);
            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(json);
            double price = root.path("c").asDouble(); // c = current price
            if (price > 0) return BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("[Finnhub] 가격 조회 실패 [{}]: {}", symbol, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    private StockInfo getFinnhubInfo(String symbol) {
        try {
            String url = FINNHUB_QUOTE
                    .replace("{symbol}", symbol)
                    .replace("{token}", finnhubKey);
            String   json  = restTemplate.getForObject(url, String.class);
            JsonNode root  = objectMapper.readTree(json);

            double current  = root.path("c").asDouble();
            double prevClose = root.path("pc").asDouble();
            double change   = current - prevClose;
            double changePct = prevClose > 0 ? (change / prevClose * 100) : 0;

            if (current > 0) {
                // 종목명은 SYMBOL_SEARCH 에서 별도 조회
                String name = searchCompanyName(symbol);
                return new StockInfo(symbol, name,
                        BigDecimal.valueOf(current).setScale(4, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(changePct).setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(change).setScale(4, RoundingMode.HALF_UP),
                        "USD", MarketType.US);
            }
        } catch (Exception e) {
            log.warn("[Finnhub] 정보 조회 실패 [{}]: {}", symbol, e.getMessage());
        }
        return StockInfo.unknown(symbol, MarketType.US);
    }

    @Cacheable(value = "stockPrice", key = "'name_US_' + #symbol")
    public String searchCompanyName(String symbol) {
        try {
            String url = FINNHUB_SEARCH
                    .replace("{symbol}", symbol)
                    .replace("{token}", finnhubKey);
            String   json    = restTemplate.getForObject(url, String.class);
            JsonNode root    = objectMapper.readTree(json);
            JsonNode results = root.path("result");
            if (results.isArray() && results.size() > 0) {
                return results.get(0).path("description").asText(symbol);
            }
        } catch (Exception e) {
            log.warn("[Finnhub] 종목명 조회 실패 [{}]: {}", symbol, e.getMessage());
        }
        return symbol;
    }

    /* ════════════════════════════════════════
       한국 - KIS Developers
       ════════════════════════════════════════ */

    /** KIS Access Token 발급 (12시간 유효) */
    private synchronized String getKisToken() {
        if (kisAccessToken != null && System.currentTimeMillis() < kisTokenExpiry) {
            return kisAccessToken;
        }
        try {
            String url  = kisBaseUrl() + "/oauth2/tokenP";
            String body = String.format(
                    "{\"grant_type\":\"client_credentials\",\"appkey\":\"%s\",\"appsecret\":\"%s\"}",
                    kisAppKey, kisAppSecret);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> req = new HttpEntity<>(body, headers);

            ResponseEntity<String> res = restTemplate.postForEntity(url, req, String.class);
            JsonNode root = objectMapper.readTree(res.getBody());
            kisAccessToken = root.path("access_token").asText();
            // 만료 11시간 후로 설정 (실제 12시간이지만 여유 확보)
            kisTokenExpiry = System.currentTimeMillis() + (11 * 3600 * 1000L);
            log.info("[KIS] Access Token 갱신 완료");
            return kisAccessToken;
        } catch (Exception e) {
            log.error("[KIS] Token 발급 실패: {}", e.getMessage());
            // 🚀 발급 실패 시(API 제한 등) 1분 동안 다시 시도하지 않도록 강제 휴식 (로그 폭주 방지)
            kisTokenExpiry = System.currentTimeMillis() + 60_000L;
            return null;
        }
    }

private BigDecimal getKisPrice(String symbol) {
        try {
            String token = getKisToken();
            if (token == null) return BigDecimal.ZERO;

            String url = kisBaseUrl()
                    + "/uapi/domestic-stock/v1/quotations/inquire-price"
                    + "?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=" + symbol;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("appkey",    kisAppKey);
            headers.set("appsecret", kisAppSecret);
            // 🛠️ 수정됨: 시세 조회는 모의/실전 상관없이 FHKST01010100 사용
            headers.set("tr_id",     "FHKST01010100");

            ResponseEntity<String> res = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode root = objectMapper.readTree(res.getBody());
            
            // KIS API 에러 메시지 확인용 로그 (필요시 활성화)
            // if (!root.path("rt_cd").asText().equals("0")) {
            //     log.error("[KIS] 시세 조회 API 에러: {}", root.path("msg1").asText());
            // }

            String price  = root.path("output").path("stck_prpr").asText("0");
            return new BigDecimal(price).setScale(0, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("[KIS] 가격 조회 실패 [{}]: {}", symbol, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    private StockInfo getKisInfo(String symbol) {
        try {
            String token = getKisToken();
            if (token == null) return StockInfo.unknown(symbol, MarketType.KR);

            String url = kisBaseUrl()
                    + "/uapi/domestic-stock/v1/quotations/inquire-price"
                    + "?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=" + symbol;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("appkey",    kisAppKey);
            headers.set("appsecret", kisAppSecret);
            // 🛠️ 수정됨: 시세 조회는 모의/실전 상관없이 FHKST01010100 사용
            headers.set("tr_id",     "FHKST01010100");

            ResponseEntity<String> res = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode out = objectMapper.readTree(res.getBody()).path("output");

            BigDecimal price     = new BigDecimal(out.path("stck_prpr").asText("0"));
            BigDecimal prevClose = new BigDecimal(out.path("stck_sdpr").asText("0"));
            BigDecimal change    = price.subtract(prevClose);
            BigDecimal changePct = prevClose.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : change.multiply(new BigDecimal("100"))
                             .divide(prevClose, 2, RoundingMode.HALF_UP);
            
            // 💡 참고: inquire-price API는 종목명을 내려주지 않으므로, 임시로 symbol을 사용합니다.
            String name = out.path("hts_kor_isnm").asText(symbol);

            return new StockInfo(symbol, name, price, changePct, change, "KRW", MarketType.KR);
        } catch (Exception e) {
            log.warn("[KIS] 정보 조회 실패 [{}]: {}", symbol, e.getMessage());
        }
        return StockInfo.unknown(symbol, MarketType.KR);
    }

    /* ════════════════════════════════════════
       캐시 만료 (1분마다)
       ════════════════════════════════════════ */
    @Scheduled(fixedDelay = 60_000)
    @CacheEvict(value = "stockPrice", allEntries = true)
    public void evictPriceCache() {
        log.debug("주가 캐시 초기화");
    }

    /* ════════════════════════════════════════
       종목 정보 VO
       ════════════════════════════════════════ */
    public record StockInfo(
            String     symbol,
            String     name,
            BigDecimal price,
            BigDecimal changePercent,
            BigDecimal change,
            String     currency,
            MarketType marketType
    ) {
        public static StockInfo unknown(String symbol, MarketType type) {
            return new StockInfo(symbol, symbol, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    type == MarketType.US ? "USD" : "KRW", type);
        }

        public String currencySymbol() {
            return "KRW".equals(currency) ? "₩" : "$";
        }
    }
}

package com.pj.stock_console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pj.stock_console.dto.TwelveDataResponse;
import com.pj.stock_console.entity.MarketIndicator;
import com.pj.stock_console.repository.MarketIndicatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final MarketIndicatorRepository marketIndicatorRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    // 임의의 키가 지정되어 있다면 환경변수나 프로퍼티에서 주입받아주세요. (요청 시 API 키 누락 방지!)
    // 예시: @Value("${twelvedata.api.key}") 문자열 변수 처리
    private static final String API_KEY = "4ad402555901482b8a7f93d87cb4ab23";

    /**
     * 서버 구동이 완료되고 포트가 개방된 직후, 배경(Background)에서 1회 실행하여 초기 데이터를 확보합니다.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void initMarketDataOnStartup() {
        LocalDate today = LocalDate.now();
        // 대표 종목인 USD/KRW의 오늘 데이터가 이미 있는지 먼저 확인합니다.
        if (marketIndicatorRepository.existsBySymbolAndTargetDate("USD/KRW", today)) {
            log.info("✅ [Async] Recent market data already exists in DB. Skipping Twelve Data API call on startup.");
            return;
        }

        log.info("🚀 [Async] No recent market data found. Fetching from Twelve Data (Background Task)...");
        fetchMarketDataWithOutputSize(30);
    }

    /**
     * 매일 오전 8시(한국 시간 기준)에 실행
     * (cron = "초 분 시 일 월 요일")
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    @Transactional
    public void fetchAndStoreMarketData() {
        log.info("Starting Market Data Fetch Schedule (USD/KRW, DXY, VIX, TNX, WTI)");
        fetchMarketDataWithOutputSize(2); // 주말 등 고려하여 최근 2일치 확인
    }

    private void fetchMarketDataWithOutputSize(int outputSize) {
        // apikey 파라미터를 추가하여 API 한 번 호출로 세 가지 지수를 모두 가져옵니다.
        String urlString = UriComponentsBuilder.fromUriString("https://api.twelvedata.com/time_series")
                .queryParam("symbol", "USD/KRW,UUP,VIXY,IEF,USO")
                .queryParam("interval", "1day")
                .queryParam("outputsize", String.valueOf(outputSize))
                .queryParam("apikey", API_KEY)
                .build()
                .toUriString();

        try {
            // 응답을 먼저 String으로 받아 에러 여부를 체크함 (429 에러 등 대응)
            ResponseEntity<String> response = restTemplate.getForEntity(urlString, String.class);
            String body = response.getBody();

            if (body == null) {
                log.warn("Twelve Data API response is null");
                return;
            }

            JsonNode root = objectMapper.readTree(body);

            // 1. 전체 응답 레벨에서 에러가 있는지 확인 (429 Too Many Requests 등)
            if (root.has("status") && "error".equals(root.get("status").asText())) {
                log.warn("Twelve Data API Error: {} - {}", 
                    root.has("code") ? root.get("code").asText() : "N/A",
                    root.has("message") ? root.get("message").asText() : "Unknown");
                return;
            }

            // 2. 정상 응답일 경우 Map으로 변환하여 처리
            Map<String, TwelveDataResponse.SymbolData> responseMap = objectMapper.readValue(
                body, 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, TwelveDataResponse.SymbolData>>() {}
            );

            if (responseMap != null) {
                // 각 심볼별 처리
                saveIndicatorData("USD/KRW", responseMap.get("USD/KRW"));
                saveIndicatorData("DXY", responseMap.get("UUP"));
                saveIndicatorData("VIX", responseMap.get("VIXY"));
                saveIndicatorData("TNX", responseMap.get("IEF"));
                saveIndicatorData("WTI", responseMap.get("USO"));
            }

        } catch (Exception e) {
            log.error("Failed to fetch market data from Twelve Data", e);
        }
    }

    private void saveIndicatorData(String symbol, TwelveDataResponse.SymbolData symbolData) {
        if (symbolData == null) {
            log.warn("No data returned for symbol: {}", symbol);
            return;
        }

        if ("error".equals(symbolData.getStatus())) {
            log.warn("API Error for symbol {}: {} - {}", symbol, symbolData.getCode(), symbolData.getMessage());
            return;
        }

        List<TwelveDataResponse.Value> values = symbolData.getValues();
        if (values == null || values.isEmpty()) {
            log.warn("No time series values for symbol: {}", symbol);
            return;
        }

        int savedCount = 0;
        for (TwelveDataResponse.Value data : values) {
            LocalDate targetDate = LocalDate.parse(data.getDatetime(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Double closePrice = Double.parseDouble(data.getClose());

            // DB 존재 여부 확인 후 적재 (중복 방지)
            if (!marketIndicatorRepository.existsBySymbolAndTargetDate(symbol, targetDate)) {
                MarketIndicator indicator = new MarketIndicator();
                indicator.setSymbol(symbol);
                indicator.setTargetDate(targetDate);
                indicator.setClosePrice(closePrice);
                
                // OHLC 값 추가 저장
                if (data.getOpen() != null) indicator.setOpenPrice(Double.parseDouble(data.getOpen()));
                if (data.getHigh() != null) indicator.setHighPrice(Double.parseDouble(data.getHigh()));
                if (data.getLow() != null) indicator.setLowPrice(Double.parseDouble(data.getLow()));

                marketIndicatorRepository.save(indicator);
                savedCount++;
            }
        }
        log.info("Successfully fetched and saved {} historical records for symbol: {}", savedCount, symbol);
    }
}

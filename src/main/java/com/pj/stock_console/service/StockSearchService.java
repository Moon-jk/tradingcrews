package com.pj.stock_console.service;

import com.pj.stock_console.dto.StockSearchDto;
import com.pj.stock_console.entity.News;
import com.pj.stock_console.entity.Stock;
import com.pj.stock_console.repository.NewsRepository;
import com.pj.stock_console.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockSearchService {

    private final StockRepository stockRepository;
    private final NewsRepository newsRepository;

    @Value("${finnhub.api.key}")
    private String apiKey;

    // 한국 주식 임시 마스터 데이터 (추후 DB화 하시면 DB 조회로 변경)
    private static final Map<String, String> KR_STOCKS = Map.ofEntries(
            Map.entry("005930", "삼성전자"),
            Map.entry("000660", "SK하이닉스"),
            Map.entry("373220", "LG에너지솔루션"),
            Map.entry("207940", "삼성바이오로직스"),
            Map.entry("005380", "현대차"),
            Map.entry("000270", "기아"),
            Map.entry("068270", "셀트리온"),
            Map.entry("035420", "NAVER"),
            Map.entry("035720", "카카오"),
            Map.entry("005490", "POSCO홀딩스")
    );

    // [신규] 프론트엔드 자동완성을 위한 리스트 반환 메서드
    public List<StockSearchDto> searchForAutocomplete(String keyword, String market) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String kw = keyword.trim().toUpperCase();

        if ("US".equalsIgnoreCase(market)) {
            // 내장 DB에 저장된 주식 데이터를 검색
            List<Stock> results = stockRepository.searchByKeyword(kw);
            return results.stream()
                    .limit(7)
                    .map(stock -> new StockSearchDto(stock.getSymbol(), stock.getNameEn()))
                    .collect(Collectors.toList());
        } else {
            // 한국 주식 메모리 검색
            return KR_STOCKS.entrySet().stream()
                    .filter(e -> e.getValue().contains(kw) || e.getKey().startsWith(kw))
                    .limit(7)
                    .map(e -> new StockSearchDto(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
        }
    }

    // --- 이하 기존 코드 유지 ---
    @Transactional
    public void updateStockDatabase() {
        log.info("==== [데이터 업데이트] 미국 전체 주식/ETF 데이터 수집 시작 ====");
        WebClient webClient = WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                        .build())
                .baseUrl("https://finnhub.io/api/v1")
                .build();

        try {
            List<Map<String, Object>> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/stock/symbol")
                            .queryParam("exchange", "US")
                            .queryParam("token", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();

            if (response != null) {
                List<Stock> stockEntities = response.stream()
                        .filter(data -> {
                            String type = (String) data.get("type");
                            return "Common Stock".equals(type) || "ETP".equals(type) || "REIT".equals(type) || "ADR".equals(type);
                        })
                        .map(data -> {
                            String symbol = (String) data.get("symbol");
                            String description = (String) data.get("description");
                            return new Stock(symbol, description, description, "US");
                        })
                        .collect(Collectors.toList());

                stockRepository.saveAll(stockEntities);
                log.info("==== [업데이트 완료] 총 {}개의 종목(주식+ETF) 저장 완료 ====", stockEntities.size());
            }
        } catch (Exception e) {
            log.error(">>>> [업데이트 오류] 발생: {}", e.getMessage());
        }
    }

    public String findSymbolByKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return null;
        String kw = keyword.trim().toUpperCase();

        Optional<Stock> directStock = stockRepository.findById(kw);
        if (directStock.isPresent()) return directStock.get().getSymbol();

        List<Stock> results = stockRepository.searchByKeyword(kw);
        return results.isEmpty() ? null : results.get(0).getSymbol();
    }

    @Transactional
    public void fetchCompanyNews(String symbol) {
        String fromDate = LocalDate.now().minusDays(3).toString();
        String toDate = LocalDate.now().toString();

        WebClient webClient = WebClient.builder().baseUrl("https://finnhub.io/api/v1").build();

        try {
            List<Map<String, Object>> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/company-news")
                            .queryParam("symbol", symbol)
                            .queryParam("from", fromDate)
                            .queryParam("to", toDate)
                            .queryParam("token", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();

            if (response != null) {
                response.stream().limit(15).forEach(data -> {
                    String url = (String) data.get("url");
                    if (!newsRepository.existsByUrl(url)) {
                        String headline = (String) data.get("headline");
                        String summary = (String) data.get("summary");
                        String source = (String) data.get("source");
                        Long datetime = ((Number) data.get("datetime")).longValue();

                        News news = new News(symbol, headline, summary, url, datetime, source);
                        newsRepository.save(news);
                    }
                });
                log.info("==== [뉴스 업데이트] {} 관련 뉴스 저장 완료 ====", symbol);
            }
        } catch (Exception e) {
            log.error(">>>> [뉴스 수집 실패] {}: {}", symbol, e.getMessage());
        }
    }
}
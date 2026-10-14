package com.pj.stock_console.service;

import com.pj.stock_console.dto.NewsResponse;
import com.pj.stock_console.entity.News;
import com.pj.stock_console.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NewsService {

    @Value("${finnhub.api.key}")
    private String apiKey;

    private final NewsRepository newsRepository;
    private final GroqService groqService;
    private final RestTemplate restTemplate = new RestTemplate();

    public NewsService(NewsRepository newsRepository, GroqService groqService) {
        this.newsRepository = newsRepository;
        this.groqService = groqService;
    }

    private boolean autoNewsEnabled = false; 

    public boolean isAutoNewsEnabled() {
        return autoNewsEnabled;
    }

    public void toggleAutoNews() {
        this.autoNewsEnabled = !this.autoNewsEnabled;
        System.out.println(">>> [뉴스 수집 자동화] 상태가 변경되었습니다: " + (autoNewsEnabled ? "ON" : "OFF"));
    }

    @Scheduled(fixedDelay = 600000)
    public void fetchAndSaveNews() {
        if (!autoNewsEnabled) {
            System.out.println(">>> [자동 수집] 일반 시황 뉴스 업데이트 OFF 상태입니다.");
            return;
        }
        System.out.println(">>> [자동 수집] 일반 시황 뉴스 업데이트 시작");
        String url = "https://finnhub.io/api/v1/news?category=general&token=" + apiKey;
        fetchAndSaveNews(url, null);
    }

    public void fetchCompanyNews(String symbol) {
        System.out.println(">>> [종목 수집] " + symbol + " 뉴스 업데이트 시작");
        String url = "https://finnhub.io/api/v1/company-news?symbol=" + symbol +
                "&from=2026-02-01&to=2026-02-12&token=" + apiKey;
        fetchAndSaveNews(url, symbol);
    }

    public void fetchAndSaveNews(String url, String symbol) {
        try {
            NewsResponse[] responses = restTemplate.getForObject(url, NewsResponse[].class);

            if (responses != null) {
                int newCount = 0;
                for (NewsResponse res : responses) {
                    if (newCount >= 15)
                        break;

                    if (!newsRepository.existsByUrl(res.getUrl())
                            && !newsRepository.existsByHeadline(res.getHeadline())) {

                        String aiResponse = groqService.translateAndSummarize(res.getHeadline(), res.getSummary());

                        String finalHeadline = "";
                        String finalSummary = "";
                        String finalSentiment = "NEUTRAL";
                        String aiTickers = "";

                        String lowerAi = aiResponse != null ? aiResponse.toLowerCase() : "";
                        if (aiResponse != null && lowerAi.contains("title:") && lowerAi.contains("summary:")) {
                            finalSentiment = extractValue(aiResponse, "SENTIMENT:");
                            finalHeadline = extractValue(aiResponse, "TITLE:");
                            finalSummary = extractValue(aiResponse, "SUMMARY:");
                            aiTickers = extractValue(aiResponse, "TICKERS:");
                        } else {
                            System.err.println(">>> [AI 응답 형식 오류] 원본 내용: \n" + aiResponse);
                            finalHeadline = res.getHeadline();
                            finalSummary = aiResponse != null ? aiResponse : "요약 실패";
                        }

                        String mergedTickers = mergeTickers(res.getRelated(), aiTickers);

                        News news = new News();
                        news.setSymbol(symbol); 
                        news.setSource(res.getSource());
                        news.setHeadline(finalHeadline);
                        news.setSummary(finalSummary);
                        news.setSentiment(finalSentiment.isEmpty() ? "NEUTRAL" : finalSentiment.toUpperCase());
                        news.setTickers(mergedTickers);
                        news.setUrl(res.getUrl());
                        news.setDatetime(res.getDatetime());

                        newsRepository.save(news);
                        newCount++;

                        System.out.println(">>> [" + (symbol == null ? "General" : symbol) + " 저장] " + finalHeadline + " (" + finalSentiment + ")");
                        Thread.sleep(2500);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(">>> 수집 중 오류: " + e.getMessage());
        }
    }

    private String extractValue(String input, String key) {
        if (input == null) return "";
        String upperInput = input.toUpperCase();
        String cleanKey = key.replace(":", "").replace("*", "").toUpperCase();
        
        // 다양한 형태의 키 지원 (KEY:, **KEY:**, Key:, etc.)
        String[] possibleKeys = {
            cleanKey + ":",
            "**" + cleanKey + "**:",
            "**" + cleanKey + ":**"
        };
        
        for (String pk : possibleKeys) {
            if (upperInput.contains(pk.toUpperCase())) {
                try {
                    int startIdx = upperInput.indexOf(pk.toUpperCase()) + pk.length();
                    String remaining = input.substring(startIdx).trim();
                    return remaining.split("\n")[0].trim();
                } catch (Exception ignored) {}
            }
        }
        return "";
    }

    private String mergeTickers(String apiRelated, String aiTickers) {
        Set<String> set = new TreeSet<>();
        if (apiRelated != null && !apiRelated.isEmpty()) {
            Arrays.stream(apiRelated.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(s -> set.add(s.toUpperCase()));
        }
        if (aiTickers != null && !aiTickers.isEmpty() && !aiTickers.equalsIgnoreCase("N/A")) {
            Arrays.stream(aiTickers.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(s -> set.add(s.toUpperCase()));
        }
        return String.join(",", set);
    }

    /**
     * 최근 24시간 뉴스를 분석하여 마켓 센티먼트 지수 산출
     */
    public Map<String, Object> getMarketSentiment() {
        long now = System.currentTimeMillis() / 1000;
        long last24h = now - (24 * 3600);

        long positive = newsRepository.countBySentimentAndDatetimeGreaterThan("POSITIVE", last24h);
        long negative = newsRepository.countBySentimentAndDatetimeGreaterThan("NEGATIVE", last24h);
        long neutral = newsRepository.countBySentimentAndDatetimeGreaterThan("NEUTRAL", last24h);

        long total = positive + negative;
        int score = 50; 
        String status = "NEUTRAL";
        String description = "시장 분위기가 관망세입니다.";

        if (total > 0) {
            score = (int) ((double) positive / total * 100);
            if (score >= 80) {
                status = "EXTREME_GREED";
                description = "시장이 매우 낙관적입니다. (극도의 탐욕)";
            } else if (score >= 60) {
                status = "GREED";
                description = "시장이 상승세를 타고 있습니다. (탐욕)";
            } else if (score >= 40) {
                status = "NEUTRAL";
                description = "시장 분위기가 중립적입니다.";
            } else if (score >= 20) {
                status = "FEAR";
                description = "시장에 불안감이 감돌고 있습니다. (공포)";
            } else {
                status = "EXTREME_FEAR";
                description = "시장이 매우 비관적입니다. (극도의 공포)";
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("score", score);
        result.put("status", status);
        result.put("description", description);
        result.put("positive", positive);
        result.put("negative", negative);
        result.put("neutral", neutral);
        
        long totalCount = total + neutral;
        result.put("totalCount", totalCount);

        // 추가된 퍼센트 계산 로직
        int posP = 33, neuP = 34, negP = 33;
        if (totalCount > 0) {
            posP = (int) ((double) positive / totalCount * 100);
            negP = (int) ((double) negative / totalCount * 100);
            neuP = 100 - posP - negP;
        }
        result.put("posPercent", posP);
        result.put("neuPercent", neuP);
        result.put("negPercent", negP);

        return result;
    }
}
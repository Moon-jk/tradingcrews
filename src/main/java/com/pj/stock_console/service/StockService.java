package com.pj.stock_console.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;

@Service
public class StockService {
    private final WebClient webClient;

    @Value("${finnhub.api.key}")
    private String apiKey;

    public StockService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://finnhub.io/api/v1").build();
    }

    public Map<String, Object> getPrice(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/quote")
                        .queryParam("symbol", symbol.toUpperCase())
                        .queryParam("token", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }
}
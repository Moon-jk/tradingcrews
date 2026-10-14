package com.pj.stock_console.controller;

import com.pj.stock_console.entity.Member;
import com.pj.stock_console.service.StockService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockApiController {

    private final StockService stockService;

    private static final String LOGIN_MEMBER = "loginMember";

    @PostMapping("/prices")
    public ResponseEntity<?> getWatchlistPrices(@RequestBody List<String> symbols, HttpSession session) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        Map<String, Map<String, Object>> prices = new HashMap<>();
        for (String symbol : symbols) {
            try {
                Map<String, Object> quote = stockService.getPrice(symbol);
                if (quote != null) {
                    prices.put(symbol, quote);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch price for async request: {} - {}", symbol, e.getMessage());
            }
        }

        return ResponseEntity.ok(prices);
    }
}
package com.pj.stock_console.controller;

import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.TradingAccount.MarketType;
import com.pj.stock_console.service.ExchangeRateService;
import com.pj.stock_console.service.StockPriceService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class TradingApiController {

    private final StockPriceService   stockPriceService;
    private final ExchangeRateService exchangeRateService;

    /**
     * 실시간 주가 조회
     * GET /api/trading/price?symbol=AAPL&market=US
     * GET /api/trading/price?symbol=005930&market=KR
     */
    @GetMapping("/price")
    public ResponseEntity<?> getPrice(@RequestParam String symbol,
                                      @RequestParam(defaultValue = "US") String market,
                                      HttpSession session) {
        if (!isLoggedIn(session)) return ResponseEntity.status(401).build();

        MarketType type = parseMarket(market);
        StockPriceService.StockInfo info = stockPriceService.getStockInfo(symbol, type);

        if (info.price().compareTo(BigDecimal.ZERO) == 0)
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "종목을 찾을 수 없습니다: " + symbol));

        return ResponseEntity.ok(Map.of(
                "symbol",        info.symbol(),
                "name",          info.name(),
                "price",         info.price(),
                "change",        info.change(),
                "changePercent", info.changePercent(),
                "currency",      info.currency(),
                "currencySymbol",info.currencySymbol()
        ));
    }

    /**
     * 현재 환율 조회
     * GET /api/trading/exchange-rate
     */
    @GetMapping("/exchange-rate")
    public ResponseEntity<?> getExchangeRate(HttpSession session) {
        if (!isLoggedIn(session)) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of(
                "usdToKrw", exchangeRateService.getUsdToKrw(),
                "krwToUsd", exchangeRateService.getKrwToUsd()
        ));
    }

    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("loginMember") != null;
    }

    private MarketType parseMarket(String market) {
        try { return MarketType.valueOf(market.toUpperCase()); }
        catch (Exception e) { return MarketType.US; }
    }
}

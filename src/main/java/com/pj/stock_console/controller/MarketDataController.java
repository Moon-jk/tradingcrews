package com.pj.stock_console.controller;

import com.pj.stock_console.entity.MarketIndicator;
import com.pj.stock_console.repository.MarketIndicatorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketIndicatorRepository marketIndicatorRepository;

    /**
     * 원/달러 환율 데이터 조회
     */
    @GetMapping("/exchange-rate")
    public ResponseEntity<List<MarketIndicator>> getExchangeRate() {
        List<MarketIndicator> data = marketIndicatorRepository.findBySymbolOrderByTargetDateAsc("USD/KRW");
        return ResponseEntity.ok(data);
    }

    /**
     * 달러 인덱스 데이터 조회
     */
    @GetMapping("/dxy")
    public ResponseEntity<List<MarketIndicator>> getDollarIndex() {
        List<MarketIndicator> data = marketIndicatorRepository.findBySymbolOrderByTargetDateAsc("DXY");
        return ResponseEntity.ok(data);
    }

    /**
     * VIX (변동성 지수) 데이터 조회
     */
    @GetMapping("/vix")
    public ResponseEntity<List<MarketIndicator>> getVixIndex() {
        List<MarketIndicator> data = marketIndicatorRepository.findBySymbolOrderByTargetDateAsc("VIX");
        return ResponseEntity.ok(data);
    }

    /**
     * 미 국채 10년물 (TNX proxy) 데이터 조회
     */
    @GetMapping("/tnx")
    public ResponseEntity<List<MarketIndicator>> getTnxIndex() {
        List<MarketIndicator> data = marketIndicatorRepository.findBySymbolOrderByTargetDateAsc("TNX");
        return ResponseEntity.ok(data);
    }

    /**
     * WTI 국제 유가 (USO proxy) 데이터 조회
     */
    @GetMapping("/wti")
    public ResponseEntity<List<MarketIndicator>> getWtiIndex() {
        List<MarketIndicator> data = marketIndicatorRepository.findBySymbolOrderByTargetDateAsc("WTI");
        return ResponseEntity.ok(data);
    }
}

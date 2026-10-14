package com.pj.stock_console.controller;

import com.pj.stock_console.entity.Crew;
import com.pj.stock_console.entity.CrewMember;
import com.pj.stock_console.entity.Member;
import com.pj.stock_console.service.CrewService;
import com.pj.stock_console.service.TradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/crew")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrewApiController {

    private final CrewService crewService;
    private final TradingService tradingService;

    /**
     * 특정 크루의 통계(모든 멤버의 수익률, 자산 포함)를 계산하여 반환합니다.
     * /crew/{id} 상세 페이지 비동기 로딩용.
     */
    @GetMapping("/{id}/stats")
    public ResponseEntity<?> getCrewStats(@PathVariable("id") Long id) {
        try {
            Crew crew = crewService.findById(id);
            List<Long> memberIds = new ArrayList<>();
            for (CrewMember cm : crew.getMembers()) {
                memberIds.add(cm.getMember().getId());
            }

            // TradingService에서 여러 멤버 식별자를 Bulk로 계산 (N+1 최적화)
            Map<Long, TradingService.MemberStat> bulkStats = tradingService.getMemberStatsBulk(memberIds);

            BigDecimal crewTotalProfit = BigDecimal.ZERO;
            BigDecimal crewTotalInitial = BigDecimal.ZERO;
            Map<Long, String> memberAssetMap = new HashMap<>();
            Map<Long, String> memberRateMap = new HashMap<>();

            for (Long memId : memberIds) {
                TradingService.MemberStat stat = bulkStats.getOrDefault(memId, new TradingService.MemberStat(BigDecimal.ZERO, BigDecimal.ZERO));
                crewTotalProfit = crewTotalProfit.add(stat.profit());
                crewTotalInitial = crewTotalInitial.add(stat.initial());

                BigDecimal mTotalAsset = stat.initial().add(stat.profit());
                String fAsset = "₩ " + NumberFormat.getInstance().format(mTotalAsset);
                
                String fRate = "0.00%";
                if (stat.initial().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal mRate = stat.profit().divide(stat.initial(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
                    fRate = (mRate.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + mRate.setScale(2, RoundingMode.HALF_UP) + "%";
                }

                memberAssetMap.put(memId, fAsset);
                memberRateMap.put(memId, fRate);
            }

            String formattedProfit = (crewTotalProfit.compareTo(BigDecimal.ZERO) >= 0 ? "+ ₩ " : "- ₩ ") 
                                     + NumberFormat.getInstance().format(crewTotalProfit.abs());
            String formattedInitial = "₩ " + NumberFormat.getInstance().format(crewTotalInitial);
            
            String formattedRate = "0.00%";
            if (crewTotalInitial.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal rate = crewTotalProfit.divide(crewTotalInitial, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
                formattedRate = (rate.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + rate.setScale(2, RoundingMode.HALF_UP) + "%";
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "crewTotalProfit", formattedProfit,
                "crewProfitRate", formattedRate,
                "crewTotalInitial", formattedInitial,
                "rawProfitRate", crewTotalInitial.compareTo(BigDecimal.ZERO) > 0 ? crewTotalProfit.divide(crewTotalInitial, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) : 0,
                "memberAssetMap", memberAssetMap,
                "memberRateMap", memberRateMap
            ));

        } catch (Exception e) {
            log.error("Error fetching crew stats async: ", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 여러 크루의 통합 통계를 반환합니다.
     * /crew (대시보드) 및 /crew/explore 페이지 비동기 로딩용.
     */
    @GetMapping("/stats/bulk")
    public ResponseEntity<?> getMultipleCrewStats(@RequestParam("crewIds") List<Long> crewIds) {
        try {
            Map<Long, Map<String, String>> result = new HashMap<>();

            for (Long crewId : crewIds) {
                try {
                    Crew crew = crewService.findById(crewId);
                    if (crew == null) continue;
                    
                    List<Long> memberIds = new ArrayList<>();
                    for (CrewMember cm : crew.getMembers()) {
                        memberIds.add(cm.getMember().getId());
                    }

                    Map<Long, TradingService.MemberStat> bulkStats = tradingService.getMemberStatsBulk(memberIds);
                    BigDecimal cProfit = BigDecimal.ZERO;
                    BigDecimal cInit = BigDecimal.ZERO;

                    for (Long memId : memberIds) {
                        TradingService.MemberStat stat = bulkStats.getOrDefault(memId, new TradingService.MemberStat(BigDecimal.ZERO, BigDecimal.ZERO));
                        cProfit = cProfit.add(stat.profit());
                        cInit = cInit.add(stat.initial());
                    }

                    String fProfit = (cProfit.compareTo(BigDecimal.ZERO) >= 0 ? "+ ₩ " : "- ₩ ") 
                                    + NumberFormat.getInstance().format(cProfit.abs());
                    String fInit = "₩ " + NumberFormat.getInstance().format(cInit);
                    
                    String fRate = "0.00%";
                    if (cInit.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal rate = cProfit.divide(cInit, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
                        fRate = (rate.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + rate.setScale(2, RoundingMode.HALF_UP) + "%";
                    }

                    result.put(crewId, Map.of(
                        "profit", fProfit,
                        "initial", fInit,
                        "rate", fRate,
                        "rawProfitRate", cInit.compareTo(BigDecimal.ZERO) > 0 ? cProfit.divide(cInit, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).toString() : "0"
                    ));
                } catch (Exception inner) {
                    log.warn("Skipping crew {} due to calculation error: {}", crewId, inner.getMessage());
                }
            }

            return ResponseEntity.ok(Map.of("success", true, "data", result));
            
        } catch (Exception e) {
            log.error("Error fetching multiple crew stats async: ", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}

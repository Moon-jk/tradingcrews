package com.pj.stock_console.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PredictionScheduler {

    private final PredictionAutomationService automationService;

    /**
     * 매일 오전 09:00: 오늘의 주가 예측 생성 (평일)
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI")
    public void scheduleCreation() {
        automationService.createDailyStockPrediction();
    }

    /**
     * 매일 오후 22:00: 베팅 마감 (평일)
     * 주식 시장 개장 전후로 마감 시점을 잡을 수 있습니다.
     */
    @Scheduled(cron = "0 0 22 * * MON-FRI")
    public void scheduleClosing() {
        automationService.closeDailyPredictions();
    }

    /**
     * 매일 오전 08:00: 결과 정산 (평일)
     * 미국 시장 종가가 확정된 후 정산을 진행합니다.
     */
    @Scheduled(cron = "0 0 8 * * MON-FRI")
    public void scheduleSettlement() {
        automationService.settleDailyPredictions();
    }
}

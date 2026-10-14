package com.pj.stock_console.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pj.stock_console.entity.PredictionStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class PredictionDto {

    private PredictionDto() {
        throw new IllegalStateException("Utility class");
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionRequest {
        private String title;
        private List<String> options;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionResponse {
        private Long id;
        private String title;
        private PredictionStatus status;
        private Long totalPool;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime closedAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime settledAt;
        private String resultMessage; // 정산 결과 추가
        private List<OptionResponse> options;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OptionResponse {
        private Long id;
        private String content;
        private Long optionPool;
        private Boolean isWinner;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BetRequest {
        private Long optionId;
        private Long amount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SettleRequest {
        private Long winningOptionId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RollbackRequest {
        private String reason;
    }
}

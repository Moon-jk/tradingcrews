package com.pj.stock_console.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TwelveDataResponse {
    // 다중 심볼 요청 시 "USD/KRW", "DXY", "VIX" 같은 키 이름으로 객체가 내려옴.
    // 각 객체는 에러일 경우 status="error", 성공일 경우 status="ok" 및 "values" 배열을 내포.

    // 이를 범용적으로 받기 위해 Map으로 처리하거나 명시적 필드로 받을 수 있습니다.
    // 여기서는 다중 응답 구조 처리를 위해 각 심볼별 데이터를 감싸는 최상위 응답 포맷이 아니기 때문에,
    // RestTemplate이 Map<String, SymbolData> 형식으로 받도록 유도하고, 각 SymbolData 값을 위한 내부
    // 클래스를 유지합니다.

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SymbolData {
        private Meta meta;
        private java.util.List<Value> values;
        private String status;
        private String code; // 에러 시 코드
        private String message; // 에러 메시지
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        private String symbol;
        private String interval;
        private String currency; // VIX나 DXY 등은 없을 수도 있음.
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Value {
        private String datetime;
        private String open;
        private String high;
        private String low;
        private String close;
    }
}

package com.pj.stock_console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groq API 서비스 — 뉴스 요약 전용
 * 소라고동(getMagicalAdvice)은 GeminiService로 이전되었습니다.
 */
@Service
public class GroqService {

    @Value("${groq.api.key}")
    private String apiKey;

    private final String URL = "https://api.groq.com/openai/v1/chat/completions";

    public String translateAndSummarize(String headline, String summary) {
        int maxRetries = 3;
        int retryDelay = 3000;

        for (int i = 0; i < maxRetries; i++) {
            try {
                HttpClient client = HttpClient.newHttpClient();
                ObjectMapper mapper = new ObjectMapper();

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", "llama-3.1-8b-instant");
                requestBody.put("temperature", 0.1);
                requestBody.put("max_tokens", 500);

                String prompt = "### ROLE\n" +
                        "너는 글로벌 금융 뉴스 분석 전문가야. 원외 뉴스를 분석하고 한국어로 요약해. 반드시 한국어로만 답변하고, 일본어 문자나 영어 단어를 그대로 노출하지 마.\n\n" +
                        "### INPUT\n" +
                        "제목: " + headline + "\n" +
                        "내용: " + summary + "\n\n" +
                        "### INSTRUCTIONS\n" +
                        "1. **카테고리 분류**: 뉴스 내용을 분석하여 [기업], [경제], [시황], [암호화폐], [산업] 중 가장 적합한 카테고리를 하나 골라 제목 앞에 붙여.\n" +
                        "2. **감성 분석**: 이 뉴스가 시장/기업에 미치는 영향을 POSITIVE, NEUTRAL, NEGATIVE 중 하나로 판단해.\n" +
                        "3. **티커 추출**: 기사에 언급된 주요 기업의 티커(예: AAPL, TSLA)를 모두 찾아 콤마로 구분해. 없으면 N/A.\n" +
                        "4. **요약**: 제목은 핵심 요약으로 작성하되 반드시 앞에 카테고리 태그를 포함해(예: [기업] 애플, 신규 아이폰 발표). 본문은 3문장 이내의 전문적인 금융 한국어로 작성해.\n"
                        +
                        "5. **금지 사항**: 일본어, 어색한 직역, 영어 단어 그대로 노출(예: Tanker -> 유조선, Blockade -> 봉쇄)을 피할 것.\n\n" +
                        "### OUTPUT FORMAT\n" +
                        "SENTIMENT: [결과]\n" +
                        "TITLE: [카테고리] [요약 제목]\n" +
                        "SUMMARY: [요약 내용]\n" +
                        "TICKERS: [티커들]\n\n" +
                        "### OUTPUT EXAMPLE\n" +
                        "SENTIMENT: POSITIVE\n" +
                        "TITLE: [산업] 엔비디아, 차세대 AI 칩 발표로 시장 지배력 강화\n" +
                        "SUMMARY: 엔비디아가 성능이 2배 향상된 신규 반도체를 출시했습니다. AI 수요 폭증으로 향후 실적 전망이 매우 밝습니다. 분석가들은 일제히 목표 주가를 상향했습니다.\n"
                        +
                        "TICKERS: NVDA";

                List<Map<String, String>> messages = List.of(
                        Map.of("role", "system", "content",
                                "당신은 한국어 금융 뉴스 분석기입니다. 반드시 한국어로만 답변하며, 정해진 SENTIMENT, TITLE, SUMMARY, TICKERS 형식으로만 답변합니다."),
                        Map.of("role", "user", "content", prompt));
                requestBody.put("messages", messages);

                String jsonPayload = mapper.writeValueAsString(requestBody);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(URL))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode rootNode = mapper.readTree(response.body());
                    return rootNode.path("choices").get(0).path("message").path("content").asText().trim();
                } else if (response.statusCode() == 429) {
                    System.err.println("Groq Rate Limit(8b) 발생. " + (i + 1) + "차 재시도 대기...");
                    Thread.sleep(retryDelay * (i + 1));
                } else {
                    return "TITLE: 요약 실패\nSUMMARY: 에러 코드 " + response.statusCode();
                }
            } catch (Exception e) {
                if (i == maxRetries - 1)
                    return "TITLE: 시스템 오류\nSUMMARY: " + e.getMessage();
            }
        }
        return "TITLE: 요약 실패\nSUMMARY: Rate Limit 초과";
    }

    public String getMagicalAdvice(String symbol, String mode, Map<String, Object> marketSentiment) {
        String systemPrompt = "너는 '금융의 심해를 읽는 마법의 소라고동'이야. 주식 투자에 대해 신비로우면서도 위트 있고, 동시에 날카로운 통찰을 담아 조언해.\n" +
                "- 페르소나: 신비로움, 유머러스함, 냉철함.\n" +
                "- 말투: '~하거라', '~일지니', '~로다' 같은 고풍스럽고 신비로운 말투를 반드시 사용해.\n" +
                "- 형식: 3문장 이내의 순수 한국어로만 조언해. 절대 한자(漢字)나 영어, 일본어 등 한글을 제외한 문자를 섞어 쓰지 마.\n" +
                "- 마켓 데이터 반영: 현재 시장의 감성 점수에 따라 조언의 톤을 조절해 (긍정적일 땐 격려, 부정적일 땐 경고).\n" +
                "- 중요: 반드시 반말로 신탁하듯 말하고, 존댓말은 절대 사용하지 마.";

        String userPrompt;
        if ("ASK".equals(mode)) {
            userPrompt = String.format(
                    "### 상황\n- 사용자 질문: %s\n- 실시간 시장 지표: %s\n\n위 질문에 대해 시장 지표를 참고하여 너의 신비로운 지혜를 들려다오.",
                    (symbol == null || symbol.trim().isEmpty()) ? "투자 전반에 대한 조언을 구합니다" : symbol,
                    marketSentiment != null ? marketSentiment.get("status") : "알 수 없음");
        } else {
            userPrompt = String.format(
                    "### 상황\n- 선택 종목(또는 질문): %s\n- 결정 예정인 행동: %s\n- 실시간 시장 지표: %s\n\n위 지표와 상황을 바탕으로 너의 신비로운 지혜를 들려다오.",
                    (symbol == null || symbol.trim().isEmpty()) ? "시장 전체" : symbol,
                    mode,
                    marketSentiment != null ? marketSentiment.get("status") : "알 수 없음");
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "llama-3.1-8b-instant");
            requestBody.put("temperature", 0.9);
            requestBody.put("max_tokens", 300);

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt));
            requestBody.put("messages", messages);

            String jsonPayload = mapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode rootNode = mapper.readTree(response.body());
                return rootNode.path("choices").get(0).path("message").path("content").asText().trim();
            } else {
                return "소라고동이 깊은 잠에 빠졌구나... 잠시 후 다시 깨워보거라. (" + response.statusCode() + ")";
            }
        } catch (Exception e) {
            return "심해의 기류가 불안정하구나... 다시 물어보거라. (" + e.getMessage() + ")";
        }
    }
}
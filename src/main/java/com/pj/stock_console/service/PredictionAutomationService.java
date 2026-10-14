package com.pj.stock_console.service;

import com.pj.stock_console.entity.*;
import com.pj.stock_console.entity.TradingAccount.MarketType;
import com.pj.stock_console.repository.PredictionOptionRepository;
import com.pj.stock_console.repository.PredictionQuestionRepository;
import com.pj.stock_console.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionAutomationService {

    private final PredictionService predictionService;
    private final PredictionQuestionRepository questionRepository;
    private final PredictionOptionRepository optionRepository;
    private final StockRepository stockRepository;
    private final StockPriceService stockPriceService;

    // 미국 인기 종목 (나스닥 100 ETF 및 주요 테크주)
    private static final List<String> US_POPULAR_SYMBOLS = List.of(
            "AAPL", "MSFT", "NVDA", "AMZN", "META", "GOOGL", "GOOG", "TSLA", "AVGO", "COST",
            "PEP", "NFLX", "ASML", "AZN", "ADBE", "LIN", "AMD", "CSCO", "TMUS", "INTC",
            "TXN", "AMGN", "QCOM", "INTU", "ISRG", "AMAT", "BKNG", "HON", "MU", "VRTX",
            "ADP", "REGN", "MDLZ", "PANW", "GILD", "LRCX", "ADI", "MELI", "SNPS", "KLAC",
            "CDNS", "PYPL", "CSX", "MAR", "ORLY", "ASML", "NXPI", "MNST", "CTAS", "ROP",
            "LULU", "ADSK", "PDD", "TEAM", "CPRT", "PCAR", "DXCM", "KDP", "MCHP", "VRSK",
            "IDXX", "CHTR", "MRVL", "AEP", "SBUX", "PAYX", "BKR", "KLA", "EXC", "DASH",
            "EA", "CTSH", "ODFL", "FAST", "XEL", "WDAY", "GEHC", "ROST", "ANSS", "CSGP",
            "DDOG", "BIIB", "ILMN", "WBD", "MDB", "DLTR", "ZSCALER", "FANG", "CDW", "ARM",
            "TTD", "ON", "ABNB", "MSTR", "ZS", "APP", "CEG", "GFS", "MCO", "TEAM");

    // 한국 인기 종목 (코스피 100 상위 및 관심 종목)
    private static final List<String> KR_POPULAR_SYMBOLS = List.of(
            "005930", "000660", "373220", "207940", "005380", "000270", "068270", "005490", "035420", "051910",
            "105560", "005935", "035720", "000660", "006400", "012330", "032830", "055550", "003670", "011200",
            "028260", "010950", "009150", "047050", "015760", "034730", "000810", "034020", "018260", "033780",
            "010130", "001500", "036570", "086790", "003550", "011170", "010620", "009830", "051900", "011070",
            "024110", "004020", "000720", "071050", "035250", "002790", "042660", "005830", "029780", "000080",
            "006800", "039490", "004990", "001450", "012450", "000100", "008930", "001040", "267250", "005070",
            "096770", "138040", "004800", "003410", "017670", "078930", "001430", "011780", "010120", "006360",
            "000990", "016360", "139480", "005250", "030200", "004170", "003490", "011790", "086280", "000120",
            "007070", "012750", "047810", "001740", "002380", "005940", "000150", "021240", "008770", "010140",
            "001230", "161390", "004370", "003000", "001800", "005440", "180640", "010060", "000210", "000240");

    private final Random random = new Random();

    /**
     * 매일 오전 09:00경 실행: 인기 종목 선정 및 예측 생성 (미국/한국 랜덤)
     */
    @Transactional
    public void createDailyStockPrediction() {
        log.info("[자동화] 오늘의 인기 종목 예측 생성 시작");

        // 1. 시장 선정 및 종목 코드 추출
        MarketType marketType = random.nextBoolean() ? MarketType.US : MarketType.KR;
        List<String> symbols = (marketType == MarketType.US) ? US_POPULAR_SYMBOLS : KR_POPULAR_SYMBOLS;
        String symbol = symbols.get(random.nextInt(symbols.size()));

        // 2. 실시간 정보 조회 (이름, 현재가 등)
        StockPriceService.StockInfo info = stockPriceService.getStockInfo(symbol, marketType);
        if (info.price().compareTo(BigDecimal.ZERO) == 0) {
            log.warn("[자동화] 정보를 불러올 수 없어 예측 생성을 중단합니다. Symbol: {}, Market: {}", symbol, marketType);
            return;
        }

        String name = info.name();
        BigDecimal currentPrice = info.price();
        String currencySymbol = info.currencySymbol();

        // 3. 질문 생성
        String title = String.format("오늘의 종가 예측: [%s] - 장마감 후 [%s%,.0f]보다 높을까요?",
                name, currencySymbol, currentPrice);

        // 미국 주식 등 소수점 단위가 필요한 경우 대응
        if (marketType == MarketType.US) {
            title = String.format("오늘의 종가 예측: [%s] - 장마감 후 [%s%,.2f]보다 높을까요?",
                    name, currencySymbol, currentPrice);
        }

        PredictionQuestion question = PredictionQuestion.builder()
                .title(title)
                .status(PredictionStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .targetStockCode(symbol)
                .marketType(marketType.name())
                .basePrice(currentPrice)
                .build();

        // 4. 옵션 생성
        PredictionOption up = PredictionOption.builder()
                .question(question)
                .content("상승 (높음)")
                .build();
        PredictionOption down = PredictionOption.builder()
                .question(question)
                .content("하락 (낮음)")
                .build();

        question.setOptions(List.of(up, down));
        questionRepository.save(question);

        log.info("[자동화] 예측 생성 완료: {} (Market: {})", title, marketType);
    }

    /**
     * 베팅 마감
     */
    @Transactional
    public void closeDailyPredictions() {
        log.info("[자동화] 자동 생성된 예측 베팅 마감 시작");
        List<PredictionQuestion> openQuestions = questionRepository.findAll().stream()
                .filter(q -> q.getStatus() == PredictionStatus.OPEN && q.getTargetStockCode() != null)
                .toList();

        for (PredictionQuestion q : openQuestions) {
            predictionService.closePrediction(q.getId());
            log.info("[자동화] 마감 완료: id={}, title={}", q.getId(), q.getTitle());
        }
    }

    /**
     * 결과 정산 (멀티 시장 대응)
     */
    @Transactional
    public void settleDailyPredictions() {
        log.info("[자동화] 자동 생성된 예측 결과 정산 시작");
        List<PredictionQuestion> closedQuestions = questionRepository.findAll().stream()
                .filter(q -> q.getStatus() == PredictionStatus.CLOSED && q.getTargetStockCode() != null)
                .toList();

        for (PredictionQuestion q : closedQuestions) {
            try {
                // 시장 타입 판별
                MarketType mType = "KR".equals(q.getMarketType()) ? MarketType.KR : MarketType.US;
                String currency = (mType == MarketType.KR) ? "₩" : "$";

                // 현재가(결과가) 조회
                BigDecimal finalPrice = stockPriceService.getCurrentPrice(q.getTargetStockCode(), mType);
                if (finalPrice.compareTo(BigDecimal.ZERO) == 0)
                    continue;

                // 승리 옵션 판단
                PredictionOption winner = null;
                if (finalPrice.compareTo(q.getBasePrice()) > 0) {
                    winner = q.getOptions().stream().filter(o -> o.getContent().contains("상승")).findFirst()
                            .orElse(null);
                } else if (finalPrice.compareTo(q.getBasePrice()) < 0) {
                    winner = q.getOptions().stream().filter(o -> o.getContent().contains("하락")).findFirst()
                            .orElse(null);
                }

                if (winner != null) {
                    predictionService.settlePrediction(q.getId(), winner.getId());
                    String msg = String.format("정산 결과: 종가 %s%,d (기준가 대비 %s)",
                            currency, finalPrice.longValue(),
                            (finalPrice.compareTo(q.getBasePrice()) > 0 ? "상승" : "하락"));

                    if (mType == MarketType.US) {
                        msg = String.format("정산 결과: 종가 %s%,.2f (기준가 대비 %s)",
                                currency, finalPrice, (finalPrice.compareTo(q.getBasePrice()) > 0 ? "상승" : "하락"));
                    }

                    q.setResultMessage(msg);
                    questionRepository.save(q);
                    log.info("[자동화] 정산 완료: id={}, Winner={}", q.getId(), winner.getContent());
                } else {
                    // 보합 (가격 변동 없음) -> 환불 처리
                    predictionService.refundPrediction(q.getId());
                    String msg = String.format("정산 결과: 종가 %s%,d (변동 없음 - 전액 환불)", currency, finalPrice.longValue());
                    if (mType == MarketType.US) {
                        msg = String.format("정산 결과: 종가 %s%,.2f (변동 없음 - 전액 환불)", currency, finalPrice);
                    }
                    q.setResultMessage(msg);
                    questionRepository.save(q);
                    log.info("[자동화] 보합으로 인한 전액 환불 처리 완료: id={}", q.getId());
                }
            } catch (Exception e) {
                log.error("[자동화] 정산 도중 오류 발생 [id={}]: {}", q.getId(), e.getMessage());
            }
        }
    }
}

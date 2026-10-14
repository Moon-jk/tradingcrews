package com.pj.stock_console.service;

import com.pj.stock_console.dto.TradeRequest;
import com.pj.stock_console.entity.*;
import com.pj.stock_console.entity.TradingAccount.MarketType;
import com.pj.stock_console.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingService {

        private final TradingAccountRepository accountRepository;
        private final HoldingRepository holdingRepository;
        private final TradeHistoryRepository tradeHistoryRepository;
        private final PendingOrderRepository pendingOrderRepository;
        private final StockPriceService stockPriceService;
        private final ExchangeRateService exchangeRateService;

        // 🌟 [추가됨] 랭킹 캐시 저장을 위한 로컬 메모리 변수
        private final Map<RankingType, List<RankingEntry>> rankingCache = new ConcurrentHashMap<>();

        /*
         * ════════════════════════════════════════
         * 모의투자 전체 초기화 (관리자용)
         * ════════════════════════════════════════
         */
        @Transactional
        public void resetAllMockInvestmentData() {
                pendingOrderRepository.deleteAll();
                tradeHistoryRepository.deleteAll();
                holdingRepository.deleteAll();
                accountRepository.deleteAll();
        }

        /*
         * ════════════════════════════════════════
         * 계좌 조회 / 자동 생성
         * ════════════════════════════════════════
         */

        @Transactional
        public TradingAccount getOrCreate(Member member, MarketType type) {
                return accountRepository
                                .findByMemberIdAndMarketType(member.getId(), type)
                                .orElseGet(() -> accountRepository.save(
                                                TradingAccount.create(member, type)));
        }

        /**
         * 🌟 [추가됨] 회원의 닉네임이 변경될 때 모의투자 계좌의 닉네임 정보도 함께 업데이트
         */
        @Transactional
        public void updateNickname(Long memberId, String newNickname) {
                List<TradingAccount> accounts = accountRepository.findAllByMemberId(memberId);
                for (TradingAccount account : accounts) {
                        account.setMemberNickname(newNickname);
                }
                // 닉네임이 변경되었으므로 랭킹 캐시 초기화 (다음 조회 시 최신 정보로 갱신됨)
                rankingCache.clear();
                log.info("Member {} nickname updated to {}. Trading accounts updated and ranking cache cleared.", memberId, newNickname);
        }

        public TradingAccount getAccount(Long memberId, MarketType type) {
                return accountRepository.findByMemberIdAndMarketType(memberId, type)
                                .orElseThrow(() -> new IllegalStateException(type.name() + " 계좌가 없습니다."));
        }

        /*
         * ════════════════════════════════════════
         * 포트폴리오 조회
         * ════════════════════════════════════════
         */

        public PortfolioData getPortfolio(Long memberId, MarketType type) {
                TradingAccount account = getAccount(memberId, type);
                List<Holding> holdings = holdingRepository.findAllByAccountId(account.getId())
                                .stream().filter(h -> h.getQuantity() > 0).toList();

                List<String> symbols = holdings.stream().map(Holding::getSymbol).toList();
                Map<String, BigDecimal> prices = stockPriceService.getCurrentPrices(symbols, type);

                BigDecimal holdingVal = holdings.stream()
                                .map(h -> h.getValuation(prices.getOrDefault(h.getSymbol(), BigDecimal.ZERO)))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                return new PortfolioData(account, holdings, prices, account.getTotalAsset(holdingVal));
        }

        /*
         * ════════════════════════════════════════
         * 매수
         * ════════════════════════════════════════
         */

        @Transactional
        public String buy(Member member, TradeRequest req) {
                TradingAccount account = getAccount(member.getId(), req.getMarketType());

                // 1. 시장가 또는 지정가에 따른 [실제 주문 단가] 미리 계산
                BigDecimal price = "LIMIT".equals(req.getOrderType())
                                ? req.getPrice()
                                : stockPriceService.getCurrentPrice(req.getSymbol(), req.getMarketType());

                if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
                        throw new IllegalArgumentException("주가 조회에 실패했습니다. 종목코드를 확인하세요.");
                }

                // 2. 정확한 실제 가격(price)을 기준으로 등급별 제한 검사
                validateBuyLimits(member, account, req, price);

                if ("LIMIT".equals(req.getOrderType())) {
                        return placeLimitOrder(account, req, TradeHistory.TradeType.BUY);
                }

                // 3. 시장가 즉시 체결
                account.withdraw(price.multiply(BigDecimal.valueOf(req.getQuantity())));

                Holding holding = holdingRepository
                                .findByAccountIdAndSymbol(account.getId(), req.getSymbol())
                                .orElseGet(() -> holdingRepository.save(
                                                Holding.create(account, req.getSymbol(), req.getCompanyName(), 0,
                                                                price)));
                holding.buy(req.getQuantity(), price);

                tradeHistoryRepository.save(TradeHistory.ofBuy(
                                account, req.getSymbol(), req.getCompanyName(), req.getQuantity(), price));

                return String.format("%s %d주 매수 체결 (%s%,.2f)",
                                req.getSymbol(), req.getQuantity(), account.getCurrencySymbol(), price);
        }

        /*
         * ════════════════════════════════════════
         * 매도
         * ════════════════════════════════════════
         */

        @Transactional
        public String sell(Member member, TradeRequest req) {
                TradingAccount account = getAccount(member.getId(), req.getMarketType());

                if ("LIMIT".equals(req.getOrderType())) {
                        return placeLimitOrder(account, req, TradeHistory.TradeType.SELL);
                }

                BigDecimal price = stockPriceService.getCurrentPrice(req.getSymbol(), req.getMarketType());
                if (price.compareTo(BigDecimal.ZERO) == 0)
                        throw new IllegalArgumentException("주가 조회에 실패했습니다.");

                Holding holding = holdingRepository
                                .findByAccountIdAndSymbol(account.getId(), req.getSymbol())
                                .orElseThrow(() -> new IllegalStateException("보유하지 않은 종목입니다."));

                BigDecimal avgBuyPrice = holding.getAvgBuyPrice();
                holding.sell(req.getQuantity());
                account.deposit(price.multiply(BigDecimal.valueOf(req.getQuantity())));

                tradeHistoryRepository.save(TradeHistory.ofSell(
                                account, req.getSymbol(), req.getCompanyName(),
                                req.getQuantity(), price, avgBuyPrice));

                if (holding.getQuantity() == 0)
                        holdingRepository.delete(holding);

                return String.format("%s %d주 매도 체결 (%s%,.2f)",
                                req.getSymbol(), req.getQuantity(), account.getCurrencySymbol(), price);
        }

        /*
         * ════════════════════════════════════════
         * 등급별 제한 검사 (수정됨)
         * ════════════════════════════════════════
         */

        private void validateBuyLimits(Member member, TradingAccount account, TradeRequest req,
                        BigDecimal actualPrice) {
                // 1. 최대 보유 종목 수 초과 확인
                long currentHoldingCount = holdingRepository
                                .findAllByAccountId(account.getId())
                                .stream().filter(h -> h.getQuantity() > 0).count();

                boolean isNewSymbol = holdingRepository
                                .findByAccountIdAndSymbol(account.getId(), req.getSymbol())
                                .map(h -> h.getQuantity() == 0)
                                .orElse(true);

                int maxHoldings = member.getMaxHoldings();
                if (isNewSymbol && currentHoldingCount >= maxHoldings) {
                        throw new IllegalStateException(String.format(
                                        "보유 종목이 최대 %d개를 초과할 수 없습니다. (%s 등급)",
                                        maxHoldings, member.getRole().getDescription()));
                }

                // 2. 1회 최대 주문 금액 제한 (버그 수정: 실제 가격을 기준으로 곱셈)
                BigDecimal orderTotal = actualPrice.multiply(BigDecimal.valueOf(req.getQuantity()));
                BigDecimal maxOrderAmt = account.getInitialSeed().multiply(member.getMaxOrderRatio());

                if (orderTotal.compareTo(maxOrderAmt) > 0) {
                        throw new IllegalStateException(String.format(
                                        "1회 최대 주문 금액(%s%,.0f)을 초과했습니다. (%s 등급: 시드의 %s%%)",
                                        account.getCurrencySymbol(),
                                        maxOrderAmt,
                                        member.getRole().getDescription(),
                                        member.getMaxOrderRatio().multiply(new BigDecimal("100")).stripTrailingZeros()
                                                        .toPlainString()));
                }
        }

        /*
         * ════════════════════════════════════════
         * 지정가 주문 등록
         * ════════════════════════════════════════
         */

        private String placeLimitOrder(TradingAccount account, TradeRequest req,
                        TradeHistory.TradeType type) {
                BigDecimal reserved = BigDecimal.ZERO;

                if (type == TradeHistory.TradeType.BUY) {
                        reserved = req.getPrice().multiply(BigDecimal.valueOf(req.getQuantity()));
                        account.withdraw(reserved);
                } else {
                        Holding h = holdingRepository
                                        .findByAccountIdAndSymbol(account.getId(), req.getSymbol())
                                        .orElseThrow(() -> new IllegalStateException("보유하지 않은 종목입니다."));
                        if (h.getQuantity() < req.getQuantity())
                                throw new IllegalStateException("보유 수량이 부족합니다.");
                }

                pendingOrderRepository.save(PendingOrder.create(
                                account, type, req.getSymbol(), req.getCompanyName(),
                                req.getQuantity(), req.getPrice(), reserved));

                return String.format("[지정가] %s %d주 %s 주문 등록 (목표가: %s%,.2f)",
                                req.getSymbol(), req.getQuantity(),
                                type == TradeHistory.TradeType.BUY ? "매수" : "매도",
                                account.getCurrencySymbol(), req.getPrice());
        }

        /*
         * ════════════════════════════════════════
         * 지정가 취소
         * ════════════════════════════════════════
         */

        @Transactional
        public void cancelOrder(Long orderId, Long memberId) {
                PendingOrder order = pendingOrderRepository.findById(orderId)
                                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
                if (!order.getAccount().getMemberId().equals(memberId))
                        throw new SecurityException("권한이 없습니다.");
                if (order.getStatus() != PendingOrder.OrderStatus.PENDING)
                        throw new IllegalStateException("이미 처리된 주문입니다.");

                if (order.getTradeType() == TradeHistory.TradeType.BUY
                                && order.getReservedCash().compareTo(BigDecimal.ZERO) > 0) {
                        order.getAccount().deposit(order.getReservedCash());
                }
                order.cancel();
        }

        /*
         * ════════════════════════════════════════
         * 지정가 체결 스케줄러 (1분마다)
         * ════════════════════════════════════════
         */

        @Scheduled(fixedDelay = 60_000)
        @Transactional
        public void processPendingOrders() {
                List<PendingOrder> orders = pendingOrderRepository
                                .findAllByStatus(PendingOrder.OrderStatus.PENDING);
                if (orders.isEmpty())
                        return;

                Map<String, BigDecimal> usPrice = new HashMap<>();
                Map<String, BigDecimal> krPrice = new HashMap<>();

                orders.forEach(o -> {
                        if (o.getAccount().getMarketType() == MarketType.US)
                                usPrice.put(o.getSymbol(), null);
                        else
                                krPrice.put(o.getSymbol(), null);
                });

                usPrice.keySet().forEach(s -> usPrice.put(s,
                                stockPriceService.getCurrentPrice(s, MarketType.US)));
                krPrice.keySet().forEach(s -> krPrice.put(s,
                                stockPriceService.getCurrentPrice(s, MarketType.KR)));

                for (PendingOrder order : orders) {
                        BigDecimal current = (order.getAccount().getMarketType() == MarketType.US
                                        ? usPrice
                                        : krPrice).getOrDefault(order.getSymbol(), BigDecimal.ZERO);
                        if (current.compareTo(BigDecimal.ZERO) == 0)
                                continue;

                        boolean fill = order.getTradeType() == TradeHistory.TradeType.BUY
                                        ? current.compareTo(order.getTargetPrice()) <= 0
                                        : current.compareTo(order.getTargetPrice()) >= 0;

                        if (fill) {
                                try {
                                        fillOrder(order, current);
                                } catch (Exception e) {
                                        log.error("지정가 체결 실패 [orderId={}]: {}", order.getId(), e.getMessage());
                                }
                        }
                }
        }

        private void fillOrder(PendingOrder order, BigDecimal price) {
                TradingAccount account = order.getAccount();

                if (order.getTradeType() == TradeHistory.TradeType.BUY) {
                        BigDecimal actual = price.multiply(BigDecimal.valueOf(order.getQuantity()));
                        BigDecimal diff = order.getReservedCash().subtract(actual);
                        if (diff.compareTo(BigDecimal.ZERO) > 0)
                                account.deposit(diff);

                        Holding h = holdingRepository
                                        .findByAccountIdAndSymbol(account.getId(), order.getSymbol())
                                        .orElseGet(() -> holdingRepository.save(Holding.create(
                                                        account, order.getSymbol(), order.getCompanyName(), 0, price)));
                        h.buy(order.getQuantity(), price);
                        tradeHistoryRepository.save(TradeHistory.ofBuy(
                                        account, order.getSymbol(), order.getCompanyName(), order.getQuantity(),
                                        price));

                } else {
                        Holding h = holdingRepository
                                        .findByAccountIdAndSymbol(account.getId(), order.getSymbol())
                                        .orElseThrow(() -> new IllegalStateException("보유 종목 없음"));
                        BigDecimal avg = h.getAvgBuyPrice();
                        h.sell(order.getQuantity());
                        account.deposit(price.multiply(BigDecimal.valueOf(order.getQuantity())));
                        tradeHistoryRepository.save(TradeHistory.ofSell(
                                        account, order.getSymbol(), order.getCompanyName(),
                                        order.getQuantity(), price, avg));
                        if (h.getQuantity() == 0)
                                holdingRepository.delete(h);
                }
                order.fill();
                log.info("지정가 체결 완료: {} {} {}주 @ {}", order.getTradeType(),
                                order.getSymbol(), order.getQuantity(), price);
        }

        /*
         * ════════════════════════════════════════
         * 거래 내역
         * ════════════════════════════════════════
         */

        public Page<TradeHistory> getTradeHistory(Long memberId, MarketType type, int page) {
                TradingAccount account = getAccount(memberId, type);
                return tradeHistoryRepository.findAllByAccountIdOrderByTradedAtDesc(
                                account.getId(), PageRequest.of(Math.max(page - 1, 0), 20));
        }

        /*
         * ════════════════════════════════════════
         * 미체결 주문
         * ════════════════════════════════════════
         */

        public List<PendingOrder> getPendingOrders(Long memberId, MarketType type) {
                TradingAccount account = getAccount(memberId, type);
                return pendingOrderRepository.findAllByAccountIdAndStatusOrderByCreatedAtDesc(
                                account.getId(), PendingOrder.OrderStatus.PENDING);
        }

        /*
         * ════════════════════════════════════════
         * 랭킹
         * ════════════════════════════════════════
         */

        public List<RankingEntry> getRanking(RankingType rankingType) {
                // 🌟 [최적화] 이미 캐시된 데이터가 있다면 즉시 반환 (없으면 백그라운드에서 자동 생성됨)
                List<RankingEntry> cached = rankingCache.get(rankingType);
                if (cached != null) return cached;
                
                // 캐시가 비어있는 초기 상태만 동기적으로 계산 (그 외엔 스케줄러가 처리)
                return refreshRankingCache(rankingType);
        }

        /**
         * 🌟 [최적화] 랭킹 연산을 백그라운드에서 매 5분마다 수행하여 사용자 대기 시간을 제로화합니다.
         */
        @Scheduled(fixedRate = 300_000) // 5분마다 갱신
        public void updateRankingCache() {
                log.info("🔄 [Scheduled] 대시보드 랭킹 캐시 갱신 시작...");
                refreshRankingCache(RankingType.COMBINED);
                refreshRankingCache(RankingType.US);
                refreshRankingCache(RankingType.KR);
                log.info("✅ [Scheduled] 랭킹 캐시 갱신 완료");
        }

        @Transactional(readOnly = true)
        public List<RankingEntry> refreshRankingCache(RankingType rankingType) {
                // 1. 모든 계좌와 그에 딸린 보유 종목을 최대한 한꺼번에 조회 (N+1 방어)
                List<TradingAccount> accounts = accountRepository.findAllWithHoldings();
                
                // 2. 종목별 그룹화 (이미 holdings가 fetch 된 상태)
                BigDecimal usdToKrw = exchangeRateService.getUsdToKrw();

                // 3. 필요한 모든 주식 가격을 한꺼번에 조회 (외부 API 호출 최소화)
                Set<String> usSymbols = new HashSet<>();
                Set<String> krSymbols = new HashSet<>();
                
                for (TradingAccount acc : accounts) {
                        for (Holding h : acc.getHoldings()) {
                                if (h.getQuantity() <= 0) continue;
                                if (acc.getMarketType() == MarketType.US) usSymbols.add(h.getSymbol());
                                else krSymbols.add(h.getSymbol());
                        }
                }

                Map<String, BigDecimal> usPrices = usSymbols.isEmpty() ? Collections.emptyMap() : stockPriceService.getCurrentPrices(usSymbols, MarketType.US);
                Map<String, BigDecimal> krPrices = krSymbols.isEmpty() ? Collections.emptyMap() : stockPriceService.getCurrentPrices(krSymbols, MarketType.KR);

                // 4. 단일 시장별 계산 로직
                if (rankingType != RankingType.COMBINED) {
                        List<RankingEntry> entries = new ArrayList<>();
                        for (TradingAccount acc : accounts) {
                                if (rankingType == RankingType.US && acc.getMarketType() != MarketType.US) continue;
                                if (rankingType == RankingType.KR && acc.getMarketType() != MarketType.KR) continue;

                                Map<String, BigDecimal> prices = acc.getMarketType() == MarketType.US ? usPrices : krPrices;

                                BigDecimal holdingVal = acc.getHoldings().stream()
                                                .filter(h -> h.getQuantity() > 0)
                                                .map(h -> h.getValuation(prices.getOrDefault(h.getSymbol(), BigDecimal.ZERO)))
                                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                                BigDecimal total = acc.getTotalAsset(holdingVal);
                                BigDecimal krwTotal = acc.getMarketType() == MarketType.US
                                                ? total.multiply(usdToKrw).setScale(0, RoundingMode.HALF_UP)
                                                : total;

                                entries.add(new RankingEntry(
                                                acc.getMemberId(), 0, acc.getMemberNickname(), acc.getMarketType(), acc.getMemberRole(),
                                                total, krwTotal, acc.getProfitRate(total), acc.getCurrencySymbol()));
                        }
                        entries.sort(Comparator.comparing(RankingEntry::profitRate).reversed());
                        
                        List<RankingEntry> result = new ArrayList<>();
                        for (int i = 0; i < entries.size(); i++) {
                                RankingEntry e = entries.get(i);
                                result.add(new RankingEntry(e.memberId(), i + 1, e.nickname(), e.marketType(), e.memberRole(),
                                                e.totalAsset(), e.krwTotalAsset(), e.profitRate(), e.currencySymbol()));
                        }
                        rankingCache.put(rankingType, result);
                        return result;
                }

                // 5. 통합 랭킹 계산 로직
                Map<Long, CombinedAccountInfo> combinedMap = new HashMap<>();
                for (TradingAccount acc : accounts) {
                        Map<String, BigDecimal> prices = acc.getMarketType() == MarketType.US ? usPrices : krPrices;

                        BigDecimal holdingVal = acc.getHoldings().stream()
                                        .filter(h -> h.getQuantity() > 0)
                                        .map(h -> h.getValuation(prices.getOrDefault(h.getSymbol(), BigDecimal.ZERO)))
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                        BigDecimal totalAsset = acc.getTotalAsset(holdingVal);
                        BigDecimal krwAsset = acc.getMarketType() == MarketType.US
                                        ? totalAsset.multiply(usdToKrw).setScale(0, RoundingMode.HALF_UP)
                                        : totalAsset;
                        BigDecimal krwInitial = acc.getMarketType() == MarketType.US
                                        ? acc.getInitialSeed().multiply(usdToKrw).setScale(0, RoundingMode.HALF_UP)
                                        : acc.getInitialSeed();

                        CombinedAccountInfo info = combinedMap.computeIfAbsent(acc.getMemberId(),
                                        id -> new CombinedAccountInfo(id, acc.getMemberNickname(), acc.getMemberRole()));

                        info.totalKrwAsset = info.totalKrwAsset.add(krwAsset);
                        info.totalKrwInitial = info.totalKrwInitial.add(krwInitial);
                }

                List<RankingEntry> entries = new ArrayList<>();
                for (CombinedAccountInfo info : combinedMap.values()) {
                        if (info.totalKrwInitial.compareTo(BigDecimal.ZERO) == 0) continue;
                        BigDecimal profitAmount = info.totalKrwAsset.subtract(info.totalKrwInitial);
                        BigDecimal profitRate = profitAmount.divide(info.totalKrwInitial, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));

                        entries.add(new RankingEntry(info.memberId, 0, info.nickname, null, info.role,
                                        info.totalKrwAsset, info.totalKrwAsset, profitRate, "₩"));
                }
                entries.sort(Comparator.comparing(RankingEntry::profitRate).reversed());

                List<RankingEntry> result = new ArrayList<>();
                for (int i = 0; i < entries.size(); i++) {
                        RankingEntry e = entries.get(i);
                        result.add(new RankingEntry(e.memberId(), i + 1, e.nickname(), e.marketType(), e.memberRole(),
                                        e.totalAsset(), e.krwTotalAsset(), e.profitRate(), e.currencySymbol()));
                }
                rankingCache.put(rankingType, result);
                return result;
        }

        /*
         * ════════════════════════════════════════
         * 멤버 총 수익금 한화 환산 조회
         * ════════════════════════════════════════
         */
        public BigDecimal calculateMemberTotalProfitKrw(Long memberId) {
                List<TradingAccount> accounts = accountRepository.findAll()
                                .stream().filter(a -> a.getMemberId().equals(memberId)).toList();

                BigDecimal totalProfitKrw = BigDecimal.ZERO;
                BigDecimal usdToKrw = exchangeRateService.getUsdToKrw();

                for (TradingAccount acc : accounts) {
                        List<Holding> holdings = holdingRepository.findAllByAccountId(acc.getId())
                                        .stream().filter(h -> h.getQuantity() > 0).toList();
                        Map<String, BigDecimal> prices = stockPriceService.getCurrentPrices(
                                        holdings.stream().map(Holding::getSymbol).toList(), acc.getMarketType());

                        BigDecimal holdingVal = holdings.stream()
                                        .map(h -> h.getValuation(prices.getOrDefault(h.getSymbol(), BigDecimal.ZERO)))
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                        BigDecimal totalAsset = acc.getTotalAsset(holdingVal);
                        BigDecimal profit = totalAsset.subtract(acc.getInitialSeed());

                        if (acc.getMarketType() == MarketType.US) {
                                // 달러 수익을 원화로 환산
                                BigDecimal profitKrw = profit.multiply(usdToKrw).setScale(0, RoundingMode.HALF_UP);
                                totalProfitKrw = totalProfitKrw.add(profitKrw);
                        } else {
                                totalProfitKrw = totalProfitKrw.add(profit);
                        }
                }
                return totalProfitKrw;
        }

        /*
         * ════════════════════════════════════════
         * 멤버 총 원금 한화 환산 조회
         * ════════════════════════════════════════
         */
        public BigDecimal calculateMemberTotalInitialKrw(Long memberId) {
                List<TradingAccount> accounts = accountRepository.findAll()
                                .stream().filter(a -> a.getMemberId().equals(memberId)).toList();

                BigDecimal totalInitialKrw = BigDecimal.ZERO;
                BigDecimal usdToKrw = exchangeRateService.getUsdToKrw();

                for (TradingAccount acc : accounts) {
                        BigDecimal initial = acc.getInitialSeed();
                        if (acc.getMarketType() == MarketType.US) {
                                BigDecimal initialKrw = initial.multiply(usdToKrw).setScale(0, RoundingMode.HALF_UP);
                                totalInitialKrw = totalInitialKrw.add(initialKrw);
                        } else {
                                totalInitialKrw = totalInitialKrw.add(initial);
                        }
                }
                return totalInitialKrw;
        }

        private static class CombinedAccountInfo {
                Long memberId;
                String nickname;
                Role role;
                BigDecimal totalKrwAsset = BigDecimal.ZERO;
                BigDecimal totalKrwInitial = BigDecimal.ZERO;

                CombinedAccountInfo(Long memberId, String nickname, Role role) {
                        this.memberId = memberId;
                        this.nickname = nickname;
                        this.role = role;
                }
        }

        public record PortfolioData(
                        TradingAccount account,
                        List<Holding> holdings,
                        Map<String, BigDecimal> prices,
                        BigDecimal totalAsset) {
        }

        public record MemberStat(
                        BigDecimal profit,
                        BigDecimal initial) {
        }

        /*
         * ════════════════════════════════════════
         * 다수 멤버 통합 조회 (Bulk 최적화)
         * ════════════════════════════════════════
         */
        public Map<Long, MemberStat> getMemberStatsBulk(List<Long> memberIds) {
                if (memberIds == null || memberIds.isEmpty()) return Collections.emptyMap();
                Set<Long> memberIdSet = new HashSet<>(memberIds);
                List<TradingAccount> accounts = accountRepository.findAllByMemberIdIn(memberIdSet);

                List<Long> accountIds = accounts.stream().map(TradingAccount::getId).toList();
                List<Holding> allHoldings = holdingRepository.findAllByAccountIdIn(accountIds).stream()
                                .filter(h -> h.getQuantity() > 0).toList();

                Map<Long, List<Holding>> holdingsByAccount = allHoldings.stream()
                                .collect(Collectors.groupingBy(h -> h.getAccount().getId()));

                List<String> usSymbols = allHoldings.stream()
                                .filter(h -> accounts.stream().anyMatch(a -> a.getId().equals(h.getAccount().getId()) && a.getMarketType() == MarketType.US))
                                .map(Holding::getSymbol).distinct().toList();
                List<String> krSymbols = allHoldings.stream()
                                .filter(h -> accounts.stream().anyMatch(a -> a.getId().equals(h.getAccount().getId()) && a.getMarketType() == MarketType.KR))
                                .map(Holding::getSymbol).distinct().toList();

                Map<String, BigDecimal> usPrices = usSymbols.isEmpty() ? Collections.emptyMap() : stockPriceService.getCurrentPrices(usSymbols, MarketType.US);
                Map<String, BigDecimal> krPrices = krSymbols.isEmpty() ? Collections.emptyMap() : stockPriceService.getCurrentPrices(krSymbols, MarketType.KR);
                BigDecimal usdToKrw = exchangeRateService.getUsdToKrw();

                Map<Long, MemberStat> resultMap = new HashMap<>();
                for (Long mId : memberIdSet) {
                        BigDecimal totalProfit = BigDecimal.ZERO;
                        BigDecimal totalInitial = BigDecimal.ZERO;

                        for (TradingAccount acc : accounts) {
                                if (!acc.getMemberId().equals(mId)) continue;

                                List<Holding> accHoldings = holdingsByAccount.getOrDefault(acc.getId(), Collections.emptyList());
                                Map<String, BigDecimal> prices = acc.getMarketType() == MarketType.US ? usPrices : krPrices;

                                BigDecimal holdingVal = accHoldings.stream()
                                                .map(h -> h.getValuation(prices.getOrDefault(h.getSymbol(), BigDecimal.ZERO)))
                                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                                BigDecimal totalAsset = acc.getTotalAsset(holdingVal);
                                BigDecimal profit = totalAsset.subtract(acc.getInitialSeed());

                                if (acc.getMarketType() == MarketType.US) {
                                        totalProfit = totalProfit.add(profit.multiply(usdToKrw).setScale(0, RoundingMode.HALF_UP));
                                        totalInitial = totalInitial.add(acc.getInitialSeed().multiply(usdToKrw).setScale(0, RoundingMode.HALF_UP));
                                } else {
                                        totalProfit = totalProfit.add(profit);
                                        totalInitial = totalInitial.add(acc.getInitialSeed());
                                }
                        }
                        resultMap.put(mId, new MemberStat(totalProfit, totalInitial));
                }
                return resultMap;
        }

        public record RankingEntry(
                        Long memberId,
                        int rank,
                        String nickname,
                        MarketType marketType,
                        Role memberRole,
                        BigDecimal totalAsset,
                        BigDecimal krwTotalAsset,
                        BigDecimal profitRate,
                        String currencySymbol) {
        }

        public enum RankingType {
                US, KR, COMBINED
        }
}
package com.pj.stock_console.controller;

import com.pj.stock_console.dto.StockSearchDto;
import com.pj.stock_console.dto.TradeRequest;
import com.pj.stock_console.entity.*;
import com.pj.stock_console.entity.TradingAccount.MarketType;
import com.pj.stock_console.service.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/trading")
@RequiredArgsConstructor
public class TradingController {

    private final TradingService      tradingService;
    private final StockPriceService   stockPriceService;
    private final ExchangeRateService exchangeRateService;
    // [추가] 자동완성 서비스를 주입받습니다.
    private final StockSearchService  stockSearchService;

    @GetMapping
    public String dashboard(@RequestParam(defaultValue = "US") String market,
                            HttpSession session, Model model) {
        Member     me   = requireLogin(session);
        MarketType type = parseMarket(market);

        tradingService.getOrCreate(me, MarketType.US);
        tradingService.getOrCreate(me, MarketType.KR);

        TradingService.PortfolioData pf      = tradingService.getPortfolio(me.getId(), type);
        List<PendingOrder>           pending = tradingService.getPendingOrders(me.getId(), type);

        model.addAttribute("account",       pf.account());
        model.addAttribute("holdings",      pf.holdings());
        model.addAttribute("prices",        pf.prices());
        model.addAttribute("totalAsset",    pf.totalAsset());
        model.addAttribute("pendingOrders", pending);
        model.addAttribute("market",        market.toUpperCase());
        model.addAttribute("usdToKrw",      exchangeRateService.getUsdToKrw());
        model.addAttribute("me",            me); 

        return "trading";
    }

    @GetMapping("/order")
    public String orderForm(@RequestParam String symbol,
                            @RequestParam(defaultValue = "US") String market,
                            HttpSession session, Model model) {
        Member     me   = requireLogin(session);
        MarketType type = parseMarket(market);

        tradingService.getOrCreate(me, type);
        TradingAccount               account = tradingService.getAccount(me.getId(), type);
        StockPriceService.StockInfo info    = stockPriceService.getStockInfo(symbol, type);

        model.addAttribute("stockInfo",   info);
        model.addAttribute("account",     account);
        model.addAttribute("market",      market.toUpperCase());
        model.addAttribute("me",          me);
        model.addAttribute("maxHoldings", me.getMaxHoldings());

        return "trading-order";
    }

    @PostMapping("/trade")
    public String trade(@ModelAttribute TradeRequest req,
                        HttpSession session, RedirectAttributes ra) {
        Member me = requireLogin(session);
        try {
            String msg = "BUY".equals(req.getTradeType())
                    ? tradingService.buy(me, req)
                    : tradingService.sell(me, req);
            ra.addFlashAttribute("successMsg", msg);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/trading?market=" + req.getMarketType().name();
    }

    @PostMapping("/order/{id}/cancel")
    public String cancelOrder(@PathVariable Long id,
                              @RequestParam(defaultValue = "US") String market,
                              HttpSession session, RedirectAttributes ra) {
        Member me = requireLogin(session);
        try {
            tradingService.cancelOrder(id, me.getId());
            ra.addFlashAttribute("successMsg", "주문이 취소되었습니다.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/trading?market=" + market;
    }

    @GetMapping("/history")
    public String history(@RequestParam(defaultValue = "US") String market,
                          @RequestParam(defaultValue = "1")  int    page,
                          HttpSession session, Model model) {
        Member     me   = requireLogin(session);
        MarketType type = parseMarket(market);

        tradingService.getOrCreate(me, type);
        Page<TradeHistory> histPage = tradingService.getTradeHistory(me.getId(), type, page);

        model.addAttribute("histories",   histPage.getContent());
        model.addAttribute("totalPages",  histPage.getTotalPages());
        model.addAttribute("currentPage", page);
        model.addAttribute("market",      market.toUpperCase());
        model.addAttribute("account",     tradingService.getAccount(me.getId(), type));

        return "trading-history";
    }

    @GetMapping("/ranking")
    public String ranking(@RequestParam(defaultValue = "COMBINED") String type,
                          HttpSession session, Model model) {
        requireLogin(session);
        TradingService.RankingType rankingType =
                TradingService.RankingType.valueOf(type.toUpperCase());

        model.addAttribute("ranking",     tradingService.getRanking(rankingType));
        model.addAttribute("rankingType", type.toUpperCase());
        model.addAttribute("usdToKrw",    exchangeRateService.getUsdToKrw());

        return "trading-ranking";
    }

    /* ════════════════════════════════════════
       [신규] 프론트엔드 연동용 API (JSON 반환)
       ════════════════════════════════════════ */
    // 1. 자동완성 검색
    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<List<StockSearchDto>> searchStock(
            @RequestParam("q") String q,
            @RequestParam(value = "market", defaultValue = "US") String market) {
        
        // 🔒 입력 검증: 빈 값 또는 100자 초과 차단
        if (q == null || q.isBlank() || q.length() > 100) {
            return ResponseEntity.ok(List.of());
        }
        
        List<StockSearchDto> results = stockSearchService.searchForAutocomplete(q.trim(), market);
        return ResponseEntity.ok(results);
    }

    // 2. 현재가 조회
    @GetMapping("/price")
    @ResponseBody
    public ResponseEntity<?> getPrice(
            // @RequestParam에 value를 명시해주고, null 값이 들어오는 것을 방지합니다.
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "market", defaultValue = "US") String market) {
        
        // 🚨 방어 로직 추가: symbol이 비어있으면 400 에러 대신 친절한 JSON 응답을 줍니다.
        if (symbol == null || symbol.trim().isEmpty()) {
             return ResponseEntity.badRequest().body(java.util.Map.of("error", "종목 코드를 입력해 주세요."));
        }

        try {
            MarketType type = parseMarket(market);
            StockPriceService.StockInfo info = stockPriceService.getStockInfo(symbol, type);
            
            if (info.price().compareTo(java.math.BigDecimal.ZERO) == 0) {
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "종목을 찾을 수 없거나 시세 정보가 없습니다."));
            }

            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("symbol", info.symbol());
            result.put("name", info.name());
            result.put("price", info.price());
            result.put("changePercent", info.changePercent());
            result.put("currencySymbol", info.currencySymbol());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(java.util.Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /* ── 세션 헬퍼 ── */
    private Member requireLogin(HttpSession session) {
        Member me = (Member) session.getAttribute("loginMember");
        if (me == null) throw new LoginRequiredException(); 
        return me;
    }

    private MarketType parseMarket(String market) {
        try { return MarketType.valueOf(market.toUpperCase()); }
        catch (Exception e) { return MarketType.US; }
    }

    @ExceptionHandler(LoginRequiredException.class)
    public String handleLoginRequiredException() {
        return "redirect:/login"; 
    }

    public static class LoginRequiredException extends RuntimeException {
        public LoginRequiredException() {
            super("로그인이 필요합니다.");
        }
    }
}
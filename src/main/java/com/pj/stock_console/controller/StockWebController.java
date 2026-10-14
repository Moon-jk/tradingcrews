package com.pj.stock_console.controller;

import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.Notice;
import com.pj.stock_console.entity.Watchlist;
import com.pj.stock_console.repository.NewsRepository;
import com.pj.stock_console.repository.NoticeRepository;
import com.pj.stock_console.repository.WatchlistRepository;
import com.pj.stock_console.service.StockService;
import com.pj.stock_console.service.TradingService;
import com.pj.stock_console.repository.CardNewsRepository;
import com.pj.stock_console.entity.CardNews;
import com.pj.stock_console.entity.News; // RESTORED
import com.pj.stock_console.service.NewsService;
import jakarta.servlet.http.HttpSession;

import org.springframework.data.domain.Page; // RESTORED
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable; // RESTORED
import org.springframework.data.domain.Sort; // RESTORED
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.hibernate.Hibernate;

import java.util.*;

@Controller
public class StockWebController {

    private static final String SYMBOL = "symbol";
    private static final String LOGIN_MEMBER = "loginMember";

    private final StockService stockService;
    private final NewsRepository newsRepository;
    private final WatchlistRepository watchlistRepository; // [변경점] 주입 추가
    private final NoticeRepository noticeRepository;
    private final TradingService tradingService;
    private final CardNewsRepository cardNewsRepository; // 추가
    private final com.pj.stock_console.service.CrewService crewService; // 추가
    private final NewsService newsService;
    private final com.pj.stock_console.service.GroqService groqService; // 소라고동 원복

    // [변경점] 생성자에 파라미터 추가
    public StockWebController(StockService stockService, NewsRepository newsRepository,
            WatchlistRepository watchlistRepository, NoticeRepository noticeRepository,
            TradingService tradingService, CardNewsRepository cardNewsRepository,
            com.pj.stock_console.service.CrewService crewService,
            NewsService newsService,
            com.pj.stock_console.service.GroqService groqService) { // 소라고동 원복
        this.stockService = stockService;
        this.newsRepository = newsRepository;
        this.watchlistRepository = watchlistRepository;
        this.noticeRepository = noticeRepository;
        this.tradingService = tradingService;
        this.cardNewsRepository = cardNewsRepository; // 추가
        this.crewService = crewService;
        this.newsService = newsService;
        this.groqService = groqService; // 소라고동 원복
    }

    // 1. 루트 경로 처리 (의도에 따른 조건부 렌더링)
    @GetMapping("/")
    @Transactional(readOnly = true)
    public String mainRoot(HttpSession session, Model model) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);

        // [비회원/크롤러] 랜딩 페이지로 안내 (SEO 최적화)
        if (loginMember == null) {
            populateLandingModel(model);
            return "index";
        }

        // [회원] 바로 대시보드로 진입
        populateDashboardModel(session, model);
        return "mainpage";
    }

    // 2. 대시보드 페이지 (명시적 접근)
    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public String mainDashboard(HttpSession session, Model model) {
        populateDashboardModel(session, model);
        return "mainpage";
    }

    // 3. 전문적인 랜딩 페이지 (명시적 접근)
    @GetMapping({ "/welcome", "/index" })
    @Transactional(readOnly = true)
    public String landingPage(Model model) {
        populateLandingModel(model);
        return "index";
    }

    /**
     * 대시보드에 필요한 데이터 채우기 (로그인 여부 무관하게 데이터 노출 가능)
     */
    private void populateDashboardModel(HttpSession session, Model model) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);

        List<Watchlist> watchlist = new ArrayList<>();
        int totalWatchlistCount = 0;
        TradingService.RankingEntry myTradingInfo = null;

        if (loginMember != null) {
            watchlist = watchlistRepository.findByMemberAndPinnedTrue(loginMember);
            watchlist.sort((a, b) -> a.getSymbol().compareToIgnoreCase(b.getSymbol()));
            totalWatchlistCount = (int) watchlistRepository.countByMember(loginMember);

            // 🌟 [최적화] 랭킹 정보를 한 번만 가져옵니다.
            List<TradingService.RankingEntry> allRanking = tradingService.getRanking(TradingService.RankingType.COMBINED);
            Long myId = loginMember.getId();
            myTradingInfo = allRanking.stream()
                    .filter(e -> e.memberId() != null && e.memberId().equals(myId))
                    .findFirst()
                    .orElse(null);
            
            model.addAttribute("topRanking", allRanking.stream().limit(3).toList());
        } else {
            // 로그인하지 않은 경우에도 상위 랭킹은 표시
            model.addAttribute("topRanking", tradingService.getRanking(TradingService.RankingType.COMBINED).stream().limit(3).toList());
        }

        List<Notice> recentNotices = noticeRepository.findAllByOrderByPinnedDescCreatedAtDesc(PageRequest.of(0, 3)).getContent();
        List<com.pj.stock_console.service.CrewService.CrewRankingEntry> topCrewRanking = crewService.getCrewRanking(3); // 크루 TOP 3 추가

        // 🌟 [최적화] 실시간 주가는 이제 프론트엔드에서 비동기(AJAX)로 가져옵니다.
        // 기존의 무거운 동기식 API 호출(CompletableFuture/Finnhub)을 제거하여 로딩 속도를 개선합니다.
        Map<String, Map<String, Object>> watchlistPrices = new HashMap<>(); // 빈 맵만 전달 (JS가 채움)

        List<CardNews> topRecommendedNews = cardNewsRepository.findAllByOrderByCreatedDateDesc(PageRequest.of(0, 3))
                .getContent();
        topRecommendedNews.forEach(news -> Hibernate.initialize(news.getLikedMembers()));

        List<News> latestAiNews = newsRepository.findAll(PageRequest.of(0, 3, Sort.by("datetime").descending())).getContent();

        model.addAttribute("watchlist", watchlist);
        model.addAttribute("totalWatchlistCount", totalWatchlistCount);
        model.addAttribute("recentNotices", recentNotices);
        model.addAttribute("myTradingInfo", myTradingInfo);
        model.addAttribute("watchlistPrices", watchlistPrices);
        model.addAttribute("topCrewRanking", topCrewRanking); // 크루 랭킹 추가
        model.addAttribute("topRecommendedNews", topRecommendedNews);
        model.addAttribute("marketSentiment", newsService.getMarketSentiment()); // 마켓 센티먼트 추가
        model.addAttribute("latestAiNews", latestAiNews); // 최근 AI 요약 뉴스 추가
    }

    /**
     * 랜딩 페이지에 필요한 데이터 채우기
     */
    private void populateLandingModel(Model model) {
        List<Notice> recentNotices = noticeRepository.findAllByOrderByPinnedDescCreatedAtDesc(PageRequest.of(0, 3))
                .getContent();
        List<CardNews> topRecommendedNews = cardNewsRepository.findAllByOrderByCreatedDateDesc(PageRequest.of(0, 3))
                .getContent();
        topRecommendedNews.forEach(news -> Hibernate.initialize(news.getLikedMembers()));

        model.addAttribute("recentNotices", recentNotices);
        model.addAttribute("topRecommendedNews", topRecommendedNews);
        model.addAttribute("vixSymbol", "TVC:VIX");
    }

    @GetMapping("/secondary")
    public String sceondaryPage(@RequestParam(value = SYMBOL, defaultValue = "NDAQ") String symbol, Model model) {
        model.addAttribute("selectedSymbol", symbol.toUpperCase());
        return "secondary";
    }

    // 3. 실시간 뉴스 전용 페이지
    @GetMapping("/infocenter/news")
    public String listNews(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        Pageable pageable = PageRequest.of(page, 6, Sort.by("datetime").descending());
        Page<News> newsPage;

        if (keyword != null && !keyword.trim().isEmpty()) {
            String cleanKeyword = keyword.trim();
            newsPage = newsRepository.findByHeadlineContainingIgnoreCaseOrSummaryContainingIgnoreCase(
                    cleanKeyword, cleanKeyword, pageable);
            model.addAttribute("keyword", cleanKeyword);
        } else {
            newsPage = newsRepository.findAll(pageable);
        }

        model.addAttribute("newsPage", newsPage);
        model.addAttribute("newsList", newsPage.getContent());
        model.addAttribute("marketSentiment", newsService.getMarketSentiment()); // 마켓 센티먼트 데이터 추가

        return "news_list";
    }

    // 4. 개별 주식 가격 상세 페이지 (기존 유지)
    @GetMapping("/schedule")
    public String stockPage(@RequestParam(value = SYMBOL, defaultValue = "AAPL") String symbol, Model model) {
        Map<String, Object> data = stockService.getPrice(symbol);
        model.addAttribute(SYMBOL, symbol.toUpperCase());
        model.addAttribute("currentPrice", data.get("c"));
        model.addAttribute("highPrice", data.get("h"));
        model.addAttribute("lowPrice", data.get("l"));
        return "schedule";
    }

    // 5. 실시간 종목 모니터링 (기존 /dashboard에서 변경)
    @GetMapping("/monitor")
    public String monitor(Model model) {
        List<String> symbols = List.of("AAPL", "TSLA", "NVDA", "MSFT", "LLYX", "SMCI");
        model.addAttribute("symbols", symbols);
        return "stock-dashboard";
    }

    // 6. 매크로 대시보드 1 (기존 유지)
    @GetMapping("/macro")
    public String macroDashboard(Model model) {
        List<Map<String, String>> assets = getMacroAssets1();
        model.addAttribute("assets", assets);
        return "macro-dashboard";
    }

    // 7. 매크로 대시보드 2 (기존 유지)
    @GetMapping("/macro2")
    public String macroDashboard2(Model model) {
        List<Map<String, String>> assets = getMacroAssets2();
        model.addAttribute("assets", assets);
        return "macro-dashboard";
    }

    // --- 내부 데이터 정리용 프라이빗 메서드 ---
    private List<Map<String, String>> getMacroAssets1() {
        return List.of(
                Map.of("name", "금 (Gold)", SYMBOL, "OANDA:XAUUSD"),
                Map.of("name", "은 (Silver)", SYMBOL, "OANDA:XAGUSD"),
                Map.of("name", "달러/원 (USD/KRW)", SYMBOL, "FX_IDC:USDKRW"),
                Map.of("name", "나스닥 100", SYMBOL, "NDX"),
                Map.of("name", "S&P 500", SYMBOL, "SPY"),
                Map.of("name", "KOSPI 200 (EWY)", SYMBOL, "AMEX:EWY"),
                Map.of("name", "코스닥 (KOSDAQ)", SYMBOL, "TVC:KOSDAQ"));
    }

    private List<Map<String, String>> getMacroAssets2() {
        return List.of(
                Map.of("name", "금 (Gold 선물)", SYMBOL, "COMEX:GC1!"),
                Map.of("name", "은 (Silver 선물)", SYMBOL, "COMEX:SI1!"),
                Map.of("name", "달러/원 (USD/KRW)", SYMBOL, "FX_IDC:USDKRW"),
                Map.of("name", "나스닥 100 선물", SYMBOL, "CME_MINI:NQ1!"),
                Map.of("name", "S&P 500 선물", SYMBOL, "CME_MINI:ES1!"),
                Map.of("name", "KOSPI 200 (EWY)", SYMBOL, "AMEX:EWY"),
                Map.of("name", "코스닥 (KOSDAQ)", SYMBOL, "KRX:KOSDAQ"));
    }

    @GetMapping("/magical")
    public String magical(Model model) {
        model.addAttribute("marketSentiment", newsService.getMarketSentiment());
        return "magical";
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/magical/consult")
    @org.springframework.web.bind.annotation.ResponseBody
    public Map<String, Object> consultMagical(
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam("mode") String mode) {
        try {
            Map<String, Object> sentiment = newsService.getMarketSentiment();
            String advice = groqService.getMagicalAdvice(symbol, mode, sentiment);
            return Map.of("advice", advice, "sentiment", sentiment);
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("advice", "심해의 기류가 어지럽구나... 다시 물어보아라. (Error: " + e.getMessage() + ")", "status", "error");
        }
    }

    @RestController
    public class HealthCheckController {
        @GetMapping("/health")
        public String health() {
            return "I'm alive!";
        }
    }
}
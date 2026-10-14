package com.pj.stock_console.controller;

import com.pj.stock_console.entity.CardNews;
import com.pj.stock_console.entity.Discussion;
import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.Watchlist;
import com.pj.stock_console.entity.WatchlistMemo;
import com.pj.stock_console.repository.CardNewsRepository;
import com.pj.stock_console.repository.DiscussionRepository;
import com.pj.stock_console.repository.WatchlistMemoRepository;
import com.pj.stock_console.repository.WatchlistRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j // 로그 확인을 위해 추가
@Controller
@RequiredArgsConstructor
public class MyDashboardController {

    private final WatchlistRepository watchlistRepository;
    private final DiscussionRepository discussionRepository;
    private final WatchlistMemoRepository watchlistMemoRepository;
    private final CardNewsRepository cardNewsRepository;

    // 1. 대시보드 화면 조회
    @GetMapping("/my-dashboard")
    public String myDashboard(HttpSession session, Model model) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null)
            return "redirect:/login";

        List<Watchlist> watchlist = watchlistRepository.findByMember(loginMember);
        List<String> symbols = watchlist.stream()
                .map(Watchlist::getSymbol)
                .toList();

        List<CardNews> savedNewsList = cardNewsRepository.findSavedByMemberId(loginMember.getId());

        model.addAttribute("member", loginMember);
        model.addAttribute("watchlist", watchlist);
        model.addAttribute("symbols", symbols);
        model.addAttribute("savedNewsList", savedNewsList);
        return "my_dashboard";
    }

    // 2. 관심 종목 추가 API
    @PostMapping("/api/watchlist/add")
    @ResponseBody
    @Transactional
    public String addSymbol(@RequestParam("symbol") String symbol, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null)
            return "auth_fail";

        String upperSymbol = symbol.trim().toUpperCase();
        if (!watchlistRepository.existsByMemberAndSymbol(loginMember, upperSymbol)) {
            Watchlist watch = new Watchlist();
            watch.setMember(loginMember);
            watch.setSymbol(upperSymbol);
            watchlistRepository.save(watch);
            return "success";
        }
        return "exists";
    }

    // 3. 관심 종목 삭제 API
    @PostMapping("/api/watchlist/delete")
    @ResponseBody
    @Transactional
    public String deleteSymbol(@RequestParam("symbol") String symbol, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null)
            return "auth_fail";

        watchlistRepository.deleteByMemberAndSymbol(loginMember, symbol.toUpperCase());
        return "success";
    }

    // [New] 관심 종목 핀 토글 API
    @PostMapping("/api/watchlist/pin")
    @ResponseBody
    @Transactional
    public String togglePin(@RequestParam("symbol") String symbol, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) return "auth_fail";

        Watchlist watch = watchlistRepository.findByMemberAndSymbol(loginMember, symbol.toUpperCase()).orElse(null);
        if (watch == null) return "not_found";

        if (!watch.isPinned() && watchlistRepository.countByMemberAndPinnedTrue(loginMember) >= 3) {
            return "max_limit"; // 3개 초과
        }

        watch.setPinned(!watch.isPinned());
        watchlistRepository.save(watch);
        return watch.isPinned() ? "pinned" : "unpinned";
    }

    // 4. 메모 저장 API (Auth Error 해결 포인트)
    @PostMapping("/api/watchlist/memo")
    @ResponseBody
    @Transactional
    public String saveMemo(@RequestParam("symbol") String symbol,
            @RequestParam("memo") String memo,
            HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");

        // 세션이 없으면 로그를 남겨서 확인 가능하게 함
        if (loginMember == null) {
            log.error("메모 저장 실패: 세션에 로그인 정보가 없습니다.");
            return "auth_fail";
        }

        watchlistRepository.findByMemberAndSymbol(loginMember, symbol.toUpperCase())
                .ifPresentOrElse(
                        w -> w.setMemo(memo),
                        () -> log.warn("해당 종목이 관심 종목에 없습니다: {}", symbol));

        return "success";
    }

    // 새 메모 로그 저장 API
    @PostMapping("/api/watchlist/memo/add")
    @ResponseBody
    public String addMemoLog(@RequestParam("symbol") String symbol, @RequestParam("content") String content, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null)
            return "auth_fail";

        WatchlistMemo memo = new WatchlistMemo();
        memo.setSymbol(symbol.toUpperCase());
        memo.setContent(content);
        memo.setMember(loginMember);
        watchlistMemoRepository.save(memo);

        return "success";
    }

    // 토론글 저장 API
    @PostMapping("/api/discussion/add")
    @ResponseBody
    public String addDiscussion(@RequestParam("symbol") String symbol, @RequestParam("content") String content, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null)
            return "auth_fail";

        Discussion post = new Discussion();
        post.setSymbol(symbol.toUpperCase());
        post.setContent(content);
        post.setMember(loginMember);
        discussionRepository.save(post);

        return "success";
    }

}
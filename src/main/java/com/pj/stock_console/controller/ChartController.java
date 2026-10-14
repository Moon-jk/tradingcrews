package com.pj.stock_console.controller;

import com.pj.stock_console.entity.Discussion;
import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.News;
import com.pj.stock_console.entity.WatchlistMemo;
import com.pj.stock_console.repository.DiscussionRepository;
import com.pj.stock_console.repository.NewsRepository;
import com.pj.stock_console.repository.WatchlistMemoRepository;
import com.pj.stock_console.service.StockSearchService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChartController {

    private final StockSearchService stockSearchService;
    private final WatchlistMemoRepository watchlistMemoRepository;
    private final DiscussionRepository discussionRepository;
    private final NewsRepository newsRepository;

// 1. 차트 검색 페이지 이동 (HTML 반환)
    @GetMapping("/chart-search")
    public String chartSearch(@RequestParam("symbol") String symbol, HttpSession session, Model model) {

        // 1-1. [변환] 입력값(이름/티커)을 실제 티커로 변환
        String targetTicker = stockSearchService.findSymbolByKeyword(symbol);

        // 1-2. [검색 실패] 결과가 없으면 메인으로
        if (targetTicker == null) {
            return "redirect:/?error=notfound";
        }

        // 1-3. [주소 정리] 입력값과 실제 티커가 다르면 리다이렉트 (예: APPLE -> AAPL)
        if (!symbol.equalsIgnoreCase(targetTicker)) {
            return "redirect:/chart-search?symbol=" + targetTicker;
        }

        // 1-4. [데이터 로드] 차트 및 관련 데이터 준비
        String upperSymbol = targetTicker.toUpperCase();
        Member loginMember = (Member) session.getAttribute("loginMember");

        model.addAttribute("selectedSymbol", upperSymbol); // 화면에 전달될 최종 티커
        model.addAttribute("member", loginMember);

        // [핵심 변경] 텍스트 내 포함된 티커 기반으로 관련 뉴스 최신 5건 로드
        // 아까 Repository에 추가한 findTop5ByRelatedText 메서드를 사용합니다.
        List<News> newsList = newsRepository.findTop5ByRelatedText(upperSymbol);
        model.addAttribute("newsList", newsList);

        // 로그인한 경우만 메모장 로드
        if (loginMember != null) {
            List<WatchlistMemo> memoLogs = watchlistMemoRepository
                    .findByMemberAndSymbolOrderByCreatedAtDesc(loginMember, upperSymbol);
            model.addAttribute("memoLogs", memoLogs);
        }

        // 토론 목록 로드 (초기 렌더링용)
        List<Discussion> discussions = discussionRepository.findBySymbolOrderByCreatedAtAsc(upperSymbol);
        model.addAttribute("discussions", discussions);

        return "chart_search";
    }

    // 2. 투자 로그 삭제 API (JSON/Text 반환)
    @PostMapping("/api/watchlist/memo/delete")
    @ResponseBody
    public String deleteMemo(@RequestParam("id") Long id, HttpSession session) {
        Member member = (Member) session.getAttribute("loginMember");
        if (member == null) {
            return "fail";
        }

        return watchlistMemoRepository.findById(id)
                .map(memo -> {
                    if (memo.getMember().getId().equals(member.getId())) {
                        watchlistMemoRepository.delete(memo);
                        return "success";
                    } else {
                        return "fail";
                    }
                })
                .orElse("fail");
    }

    // 3. [신규] 실시간 토론 목록 조회 API (JSON 반환)
    @GetMapping("/api/discussion/list")
    @ResponseBody
    public List<Map<String, Object>> getDiscussionList(@RequestParam("symbol") String symbol, HttpSession session) {
        
        List<Discussion> discussions = discussionRepository.findBySymbolOrderByCreatedAtAsc(symbol);
        Member loginMember = (Member) session.getAttribute("loginMember");
        Long currentUserId = (loginMember != null) ? loginMember.getId() : -1L;

        List<Map<String, Object>> result = new ArrayList<>();

        for (Discussion d : discussions) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", d.getId());
            map.put("content", d.getContent());
            map.put("nickname", d.getMember().getNickname());
            map.put("createdAt", d.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")));
            map.put("isMine", d.getMember().getId().equals(currentUserId));
            
            result.add(map);
        }
        return result;
    }
}
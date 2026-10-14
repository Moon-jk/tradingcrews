package com.pj.stock_console.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pj.stock_console.entity.CardNews;
import com.pj.stock_console.entity.CardPage;
import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.CardNewsComment;
import com.pj.stock_console.repository.CardNewsCommentRepository;
import com.pj.stock_console.repository.CardNewsRepository;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/infocenter")
@RequiredArgsConstructor
public class InfoCenterController {

    private static final String LOGIN_MEMBER = "loginMember";
    private static final String REDIRECT_INFOCENTER = "redirect:/infocenter/";
    private static final String ERROR_MESSAGE = "errorMessage";
    private static final String REDIRECT_LOGIN = "redirect:/login";
    private static final String MSG_LOGIN_REQUIRED = "로그인이 필요합니다.";
    private static final String STATUS = "status";
    private static final String MESSAGE = "message";

    private final CardNewsRepository cardNewsRepository;
    private final CardNewsCommentRepository cardNewsCommentRepository;
    private final ObjectMapper objectMapper;

    // ══════════════════════════════════════════════════════
    // [1] 목록 페이지
    // ══════════════════════════════════════════════════════
    @GetMapping
    @Transactional(readOnly = true)
    public String list(@RequestParam(name = "page", defaultValue = "0") int page, Model model) {
        Page<CardNews> newsPage = cardNewsRepository.findAllByOrderByCreatedDateDesc(PageRequest.of(page, 6));
        // 뷰 랜더링 시 LazyInitializationException을 방지하기 위해 강제 초기화
        newsPage.forEach(news -> {
            Hibernate.initialize(news.getLikedMembers());
            Hibernate.initialize(news.getPages());
        });
        model.addAttribute("newsPage", newsPage);
        return "infocenter/list";
    }

    // ══════════════════════════════════════════════════════
    // [1-2] 매크로 차트 대시보드 페이지
    // ══════════════════════════════════════════════════════
    @GetMapping("/macro")
    public String macroDashboard() {
        return "infocenter/macro-db";
    }

    // ══════════════════════════════════════════════════════
    // [2] 상세 페이지
    // ══════════════════════════════════════════════════════
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public String detail(@PathVariable("id") Long id, Model model, HttpSession session) {
        CardNews cardNews = cardNewsRepository.findByIdWithPages(id)
                .orElse(null);
        if (cardNews == null)
            return "redirect:/infocenter";

        List<CardNews> popularNewsList = cardNewsRepository.findPopularNews(PageRequest.of(0, 4)).stream()
                .filter(n -> !n.getId().equals(id))
                .limit(3)
                .toList();

        // 뷰 랜더링 시 LazyInitializationException을 방지하기 위해 강제 초기화
        popularNewsList.forEach(popular -> {
            Hibernate.initialize(popular.getLikedMembers());
            Hibernate.initialize(popular.getPages());
        });
        Hibernate.initialize(cardNews.getLikedMembers());
        Hibernate.initialize(cardNews.getSavedMembers());

        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        boolean hasLiked = false;
        boolean hasSaved = false;
        if (loginMember != null) {
            hasLiked = cardNews.getLikedMembers().stream()
                    .anyMatch(m -> m.getId().equals(loginMember.getId()));
            hasSaved = cardNews.getSavedMembers().stream()
                    .anyMatch(m -> m.getId().equals(loginMember.getId()));
        }

        List<CardNewsComment> allComments = cardNewsCommentRepository.findByCardNewsIdOrderByCreatedAtDesc(id);
        int totalComments = allComments.size();

        model.addAttribute("cardNews", cardNews);
        model.addAttribute("pages", cardNews.getPages());
        model.addAttribute("popularNewsList", popularNewsList);
        model.addAttribute("hasLiked", hasLiked);
        model.addAttribute("hasSaved", hasSaved);
        model.addAttribute("comments", allComments);
        model.addAttribute("totalComments", totalComments);

        // SEO 데이터 추가
        model.addAttribute("pageTitle", cardNews.getTopic());
        if (!cardNews.getPages().isEmpty()) {
            String firstPageContent = cardNews.getPages().get(0).getContent().replaceAll("<[^>]*>", " ");
            model.addAttribute("metaDescription", firstPageContent.substring(0, Math.min(firstPageContent.length(), 150)));
        }

        return "infocenter/detail";
    }

    // ══════════════════════════════════════════════════════
    // 저장 (스크랩) 토글
    // ══════════════════════════════════════════════════════
    @PostMapping("/{id}/save")
    @Transactional
    public String toggleSave(@PathVariable("id") Long id, HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) {
            rttr.addFlashAttribute(ERROR_MESSAGE, MSG_LOGIN_REQUIRED);
            return REDIRECT_LOGIN;
        }

        CardNews cardNews = cardNewsRepository.findById(id).orElse(null);
        if (cardNews != null) {
            boolean alreadySaved = cardNews.getSavedMembers().stream()
                    .anyMatch(m -> m.getId().equals(loginMember.getId()));
            if (alreadySaved) {
                cardNews.getSavedMembers().removeIf(m -> m.getId().equals(loginMember.getId()));
            } else {
                cardNews.getSavedMembers().add(loginMember);
            }
            cardNewsRepository.save(cardNews);
        }
        return REDIRECT_INFOCENTER + id;
    }

    // ══════════════════════════════════════════════════════
    // 좋아요 (추천) 토글
    // ══════════════════════════════════════════════════════
    @PostMapping("/{id}/like")
    @Transactional
    public String toggleLike(@PathVariable("id") Long id, HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) {
            rttr.addFlashAttribute(ERROR_MESSAGE, MSG_LOGIN_REQUIRED);
            return REDIRECT_LOGIN;
        }

        CardNews cardNews = cardNewsRepository.findById(id).orElse(null);
        if (cardNews != null) {
            boolean alreadyLiked = cardNews.getLikedMembers().stream()
                    .anyMatch(m -> m.getId().equals(loginMember.getId()));
            if (alreadyLiked) {
                cardNews.getLikedMembers().removeIf(m -> m.getId().equals(loginMember.getId()));
            } else {
                cardNews.getLikedMembers().add(loginMember);
            }
            cardNewsRepository.save(cardNews);
        }
        return REDIRECT_INFOCENTER + id;
    }

    // ══════════════════════════════════════════════════════
    // 삭제 (관리자 전용)
    // ══════════════════════════════════════════════════════
    @PostMapping("/{id}/delete")
    @Transactional
    public String deleteNews(@PathVariable("id") Long id, HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        // admin 또는 tier1(isPremium)만 삭제 가능
        if (loginMember == null || !loginMember.isPremium()) {
            rttr.addFlashAttribute(ERROR_MESSAGE, "삭제 권한이 없습니다. (관리자 또는 1등급 이상 필요)");
            return REDIRECT_INFOCENTER + id;
        }

        cardNewsRepository.deleteById(id);
        rttr.addFlashAttribute("successMessage", "리포트가 삭제되었습니다.");
        return "redirect:/infocenter";
    }

    // ══════════════════════════════════════════════════════
    // [수정] GET - 수정 폼 페이지
    // ══════════════════════════════════════════════════════
    @GetMapping("/{id}/edit")
    @Transactional(readOnly = true)
    public String editForm(@PathVariable("id") Long id, HttpSession session,
            Model model, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null || !loginMember.isPremium()) {
            rttr.addFlashAttribute(ERROR_MESSAGE, "수정 권한이 없습니다. (관리자 또는 1등급 이상 필요)");
            return REDIRECT_INFOCENTER + id;
        }

        CardNews cardNews = cardNewsRepository.findByIdWithPages(id).orElse(null);
        if (cardNews == null)
            return "redirect:/infocenter";

        model.addAttribute("cardNews", cardNews);
        return "infocenter/edit";
    }

    // ══════════════════════════════════════════════════════
    // [수정] POST - 수정 처리
    // ══════════════════════════════════════════════════════
    @PostMapping("/{id}/edit")
    @Transactional
    public String editNews(@PathVariable("id") Long id,
            @RequestParam("topic") String topic,
            HttpSession session,
            RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null || !loginMember.isPremium()) {
            rttr.addFlashAttribute(ERROR_MESSAGE, "수정 권한이 없습니다. (관리자 또는 1등급 이상 필요)");
            return REDIRECT_INFOCENTER + id;
        }

        CardNews cardNews = cardNewsRepository.findById(id).orElse(null);
        if (cardNews != null && topic != null && !topic.trim().isEmpty()) {
            cardNews.setTopic(topic.trim());
            cardNewsRepository.save(cardNews);
            rttr.addFlashAttribute("successMessage", "리포트 제목이 수정되었습니다.");
        }
        return REDIRECT_INFOCENTER + id;
    }

    // ══════════════════════════════════════════════════════
    // 댓글 작성
    // ══════════════════════════════════════════════════════
    @PostMapping("/{id}/comment")
    @Transactional
    public String addComment(@PathVariable("id") Long id, @RequestParam("content") String content, HttpSession session,
            RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) {
            rttr.addFlashAttribute(ERROR_MESSAGE, MSG_LOGIN_REQUIRED);
            return REDIRECT_LOGIN;
        }

        CardNews cardNews = cardNewsRepository.findById(id).orElse(null);
        if (cardNews != null && content != null && !content.trim().isEmpty()) {
            CardNewsComment comment = new CardNewsComment();
            comment.setCardNews(cardNews);
            comment.setMember(loginMember);
            comment.setContent(content.trim());
            cardNewsCommentRepository.save(comment);
        }
        return REDIRECT_INFOCENTER + id;
    }

    // ══════════════════════════════════════════════════════
    // [3] 파이썬 -> Spring 인제스트 API
    // POST /api/infocenter/ingest
    // 전달 본문 구성: topic, date (yyyy-MM-dd), folderId, cards (리스트 형태)
    // 각 카드는 page, title, content를 포함해야 함
    // ══════════════════════════════════════════════════════
    @ResponseBody
    @Transactional
    @PostMapping("/api/ingest")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody Map<String, Object> body) {
        try {
            String topic = (String) body.get("topic");
            String dateStr = (String) body.get("date");
            String folderId = (String) body.get("folderId");
            Object cardsRaw = body.get("cards");

            if (topic == null || dateStr == null || folderId == null || cardsRaw == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of(STATUS, "error", MESSAGE, "필수 필드 누락"));
            }

            // 중복 체크 — 같은 folderId는 스킵
            if (cardNewsRepository.existsByFolderId(folderId)) {
                return ResponseEntity.ok(
                        Map.of(STATUS, "skipped", MESSAGE, "이미 등록된 카드뉴스입니다."));
            }

            // 날짜 파싱
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);

            // CardNews 저장 (인스타그램 캡션 포함)
            String instagramCaption = (String) body.get("instagramCaption");
            CardNews cardNews = CardNews.of(topic, date, folderId, instagramCaption);
            CardNews savedNews = cardNewsRepository.save(cardNews);

            // cards 파싱 & CardPage 저장
            List<Map<String, Object>> cards = objectMapper.convertValue(cardsRaw, new TypeReference<>() {
            });

            for (Map<String, Object> card : cards) {
                int pageNum = ((Number) card.get("page")).intValue();
                String title = (String) card.get("title");
                String content = (String) card.get("content");
                cardNews.getPages().add(CardPage.of(cardNews, pageNum, title, content));
            }

            // cascade로 CardPage 자동 저장
            savedNews = cardNewsRepository.save(cardNews);

            return ResponseEntity.ok(Map.of(
                    STATUS, "success",
                    MESSAGE, "저장 완료",
                    "id", savedNews.getId()));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of(STATUS, "error", MESSAGE, e.getMessage()));
        }
    }
}

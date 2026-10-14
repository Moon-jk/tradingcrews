package com.pj.stock_console.controller;

import com.pj.stock_console.entity.Notice;
import com.pj.stock_console.repository.NoticeRepository;
import com.pj.stock_console.service.NoticeService;
// 본인의 Member 엔티티 경로에 맞게 Import 해주세요! (예: com.pj.stock_console.entity.Member)
import com.pj.stock_console.entity.Member;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeRepository noticeRepository;
    private final NoticeService noticeService;

    // [Read] 리스트 페이지 (누구나 볼 수 있음)
    @GetMapping
    public String noticeList(@RequestParam(name = "page", defaultValue = "0") int page, 
                             @RequestParam(name = "keyword", required = false) String keyword, 
                             Model model) {
        Page<Notice> noticePage;
        // 🔒 검색어 길이 제한 (200자 초과 시 무시)
        if (keyword != null && keyword.length() > 200) {
            keyword = null;
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            noticePage = noticeRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrderByPinnedDescCreatedAtDesc(keyword, keyword, PageRequest.of(page, 10));
        } else {
            noticePage = noticeRepository.findAllByOrderByPinnedDescCreatedAtDesc(PageRequest.of(page, 10));
        }
        model.addAttribute("notices", noticePage);
        model.addAttribute("keyword", keyword);
        return "notice/list";
    }

    // [Read] 상세 페이지 (누구나 볼 수 있음)
    @GetMapping("/detail/{id}")
    public String noticeDetail(@PathVariable(name = "id") Long id, Model model) {
        Notice notice = noticeService.getNoticeDetail(id);
        model.addAttribute("notice", notice);
        
        // SEO 데이터 추가
        model.addAttribute("pageTitle", notice.getTitle());
        String cleanContent = notice.getContent().replaceAll("<[^>]*>", " ");
        model.addAttribute("metaDescription", cleanContent.substring(0, Math.min(cleanContent.length(), 150)));
        
        return "notice/detail";
    }

    // ================= [여기서부터 로그인 권한 필요] =================

    // [Create] 글쓰기 폼 (로그인 + 권한 체크)
    @GetMapping("/write")
    public String writeForm(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login"; // 비로그인 시 로그인 페이지로 튕겨냄
        }
        if (!loginMember.getRole().name().equals("ADMIN")) {
            return "redirect:/notice"; // 권한 없으면 목록으로 반환
        }
        return "notice/write";
    }

    // [Create] 글쓰기 저장 (세션에서 닉네임 추출)
    @PostMapping("/write")
    public String writeSubmit(@RequestParam(name = "title") String title,
            @RequestParam(name = "content") String content,
            @RequestParam(name = "pinned", defaultValue = "false") boolean pinned,
            HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login";
        }
        if (!loginMember.getRole().name().equals("ADMIN")) {
            return "redirect:/notice";
        }

        // 세션에 저장된 닉네임을 작성자로 자동 지정!
        String author = loginMember.getNickname();
        noticeService.saveNotice(title, content, author, pinned);
        return "redirect:/notice";
    }

    // [Update] 수정 폼 (로그인 + 본인 확인)
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable(name = "id") Long id, Model model, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null)
            return "redirect:/login";

        Notice notice = noticeService.getNoticeForEdit(id);

        // 작성자 본인이거나 관리자가 아니면 접근 차단
        if (!notice.getAuthor().equals(loginMember.getNickname()) && !loginMember.getRole().name().equals("ADMIN")) {
            return "redirect:/notice/detail/" + id; // 권한 없음 -> 상세페이지로 돌려보냄
        }

        model.addAttribute("notice", notice);
        return "notice/edit";
    }

    // [Update] 수정 처리
    @PostMapping("/edit/{id}")
    public String editSubmit(@PathVariable(name = "id") Long id,
            @RequestParam(name = "title") String title,
            @RequestParam(name = "content") String content,
            @RequestParam(name = "pinned", defaultValue = "false") boolean pinned,
            HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null)
            return "redirect:/login";

        Notice notice = noticeService.getNoticeForEdit(id);
        if (!notice.getAuthor().equals(loginMember.getNickname()) && !loginMember.getRole().name().equals("ADMIN")) {
            return "redirect:/notice/detail/" + id;
        }

        // 서비스 단에서 업데이트 (컨트롤러에서 권한 체크를 한 번 더 할 수도 있습니다)
        noticeService.updateNotice(id, title, content, pinned);
        return "redirect:/notice/detail/" + id;
    }

    // [Delete] 삭제 처리 (본인만 삭제 가능)
    @PostMapping("/delete/{id}")
    public String deleteNotice(@PathVariable(name = "id") Long id, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null)
            return "redirect:/login";

        Notice notice = noticeService.getNoticeForEdit(id);
        if (notice.getAuthor().equals(loginMember.getNickname()) || loginMember.getRole().name().equals("ADMIN")) {
            noticeService.deleteNotice(id);
        }
        return "redirect:/notice";
    }

    // [좋아요/싫어요 처리]
    @PostMapping("/detail/{id}/like")
    public String likeNotice(@PathVariable Long id, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null)
            return "redirect:/login";

        noticeService.toggleLike(id, loginMember.getNickname());
        return "redirect:/notice/detail/" + id;
    }

    @PostMapping("/detail/{id}/dislike")
    public String dislikeNotice(@PathVariable Long id, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null)
            return "redirect:/login";

        noticeService.toggleDislike(id, loginMember.getNickname());
        return "redirect:/notice/detail/" + id;
    }

    // [댓글 작성]
    @PostMapping("/detail/{id}/comment")
    public String addComment(@PathVariable Long id, @RequestParam String content, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null)
            return "redirect:/login";

        noticeService.addComment(id, loginMember.getNickname(), content);
        return "redirect:/notice/detail/" + id;
    }

    // [댓글 삭제]
    @PostMapping("/detail/{noticeId}/comment/{commentId}/delete")
    public String deleteComment(@PathVariable Long noticeId, @PathVariable Long commentId, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember != null) {
            boolean isAdmin = loginMember.getRole().name().equals("ADMIN");
            noticeService.deleteComment(noticeId, commentId, loginMember.getNickname(), isAdmin);
        }
        return "redirect:/notice/detail/" + noticeId;
    }
}
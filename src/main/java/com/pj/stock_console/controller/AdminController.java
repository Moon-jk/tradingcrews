package com.pj.stock_console.controller;

import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.Role;
import com.pj.stock_console.entity.CardNews;
import com.pj.stock_console.repository.CardNewsRepository;
import com.pj.stock_console.repository.MemberRepository;
import com.pj.stock_console.service.StockSearchService;
import com.pj.stock_console.service.NewsService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller; // RestController 대신 Controller 사용!
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.pj.stock_console.entity.CommunityBoard;
import com.pj.stock_console.repository.CommunityBoardRepository;
import jakarta.persistence.EntityManager;
import com.pj.stock_console.service.TradingService;
import com.pj.stock_console.service.NotificationService;
import com.pj.stock_console.repository.CrewRepository;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin") // 공통 경로 설정
@RequiredArgsConstructor
public class AdminController {

    private final StockSearchService stockSearchService;
    private final MemberRepository memberRepository;
    private final NewsService newsService;
    private final TradingService tradingService;
    private final NotificationService notificationService;
    private final CommunityBoardRepository communityBoardRepository;
    private final CrewRepository crewRepository;
    private final CardNewsRepository cardNewsRepository;
    private final EntityManager em;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/prediction")
    public String predictionManagement(HttpSession session) {
        if (!isAdmin(session)) return "redirect:/";
        return "redirect:/admin/members"; // 관리 허브로 통합되었으므로 리다이렉트
    }

    // [권한 체크용 공통 메서드]
    private boolean isAdmin(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        return loginMember != null && loginMember.getRole() == Role.ADMIN;
    }

    // ==========================================
    // 1. 기존 기능: 주식 데이터 업데이트 (데이터만 반환)
    // ==========================================
    @GetMapping("/update") // /admin/update 로 매핑됨
    @ResponseBody // HTML 화면이 아니라 텍스트 데이터를 그대로 반환하라는 뜻!
    public String update(HttpSession session) {
        // (선택사항) 업데이트 버튼도 관리자만 누를 수 있게 보호하려면 아래 주석을 해제하세요.
        if (!isAdmin(session))
            return "관리자만 실행할 수 있습니다.";

        stockSearchService.updateStockDatabase();
        return "데이터 업데이트 프로세스 시작! 콘솔 로그를 확인하세요.";
    }

    // ==========================================
    // 2. 신규 기능: 회원 관리 대시보드 (HTML 화면 반환)
    // ==========================================
    @GetMapping("/members")
    public String memberList(HttpSession session, Model model) {
        if (!isAdmin(session))
            return "redirect:/"; // 관리자 아니면 메인으로

        List<Member> members = memberRepository.findAllByOrderByRoleAscIdAsc();
        List<CommunityBoard> boards = communityBoardRepository.findAll();
        List<com.pj.stock_console.entity.Crew> crews = crewRepository.findAll();
        
        // JS 인라이닝을 위한 단순화된 데이터 생성 (순환 참조 방지)
        List<Map<String, Object>> membersForJs = members.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("nickname", m.getNickname());
            map.put("loginId", m.getLoginId());
            return map;
        }).collect(Collectors.toList());

        List<Map<String, Object>> crewsForJs = crews.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("name", c.getName());
            return map;
        }).collect(Collectors.toList());

        model.addAttribute("members", members); // 테이블 렌더용 (서버 사이드)
        model.addAttribute("membersForJs", membersForJs); // JS용
        model.addAttribute("boards", boards);
        model.addAttribute("crews", crews); // 필요 시 서버 사이드용
        model.addAttribute("crewsForJs", crewsForJs); // JS용
        model.addAttribute("roles", Role.values());
        model.addAttribute("isAutoNewsEnabled", newsService.isAutoNewsEnabled());
        return "admin/members"; // templates/admin/members.html 렌더링
    }

    // ==========================================
    // 3. 신규 기능: 회원 등급 변경 로직
    // ==========================================
    @Transactional
    @PostMapping("/members/{id}/role")
    public String updateRole(@PathVariable(name = "id") Long id,
            @RequestParam(name = "role") Role newRole,
            HttpSession session) {
        if (!isAdmin(session))
            return "redirect:/";

        Member member = memberRepository.findById(id).orElseThrow();
        member.setRole(newRole);

        return "redirect:/admin/members";
    }

    // ==========================================
    // 4. 신규 기능: 회원 닉네임 변경 로직
    // ==========================================
    @Transactional
    @PostMapping("/members/{id}/nickname")
    public String updateNickname(@PathVariable(name = "id") Long id,
            @RequestParam(name = "nickname") String newNickname,
            HttpSession session) {
        // 1. 관리자 권한 체크
        if (!isAdmin(session))
            return "redirect:/";

        // 2. 회원 조회 후 닉네임 강제 업데이트
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        member.setNickname(newNickname);
        tradingService.updateNickname(id, newNickname);

        return "redirect:/admin/members";
    }

    // ==========================================
    // 5. 신규 기능: 관리자 수동 회원 등록
    // ==========================================
    @PostMapping("/members/new")
    public String registerNewMember(@RequestParam(name = "loginId") String loginId,
            @RequestParam(name = "nickname") String nickname,
            HttpSession session,
            RedirectAttributes rttr) {
        if (!isAdmin(session))
            return "redirect:/";

        // 1. 아이디 중복 체크
        if (memberRepository.existsByLoginId(loginId)) {
            // 중복일 경우 에러 메시지를 들고 다시 리다이렉트
            rttr.addFlashAttribute("errorMessage", "❌ 이미 존재하는 아이디입니다.");
            return "redirect:/admin/members";
        }

        // 2. 신규 회원 생성 및 저장
        Member newMember = new Member();
        newMember.setLoginId(loginId);
        newMember.setNickname(nickname);

        // 초기 비밀번호는 아이디와 동일하게 설정
        // (주의: Spring Security를 사용 중이라면 passwordEncoder.encode(loginId) 로 암호화해야 합니다!)
        newMember.setPassword(passwordEncoder.encode(loginId)); // 🔒 BCrypt 암호화

        newMember.setRole(Role.TIER3); // 기본 등급 부여

        memberRepository.save(newMember);

        // 성공 메시지
        rttr.addFlashAttribute("successMessage", "✅ 신규 회원이 성공적으로 등록되었습니다.");

        return "redirect:/admin/members";
    }

    // ==========================================
    // 5-5. 신규 기능: 커뮤니티 게시판(주제) 생성 및 관리
    // ==========================================
    @PostMapping("/boards/new")
    public String createBoard(@RequestParam(name = "topicName") String topicName,
                              @RequestParam(name = "description", required = false) String description,
                              HttpSession session, RedirectAttributes rttr) {
        if (!isAdmin(session)) return "redirect:/";

        try {
            CommunityBoard board = new CommunityBoard();
            board.setTopicName(topicName);
            board.setDescription(description);
            communityBoardRepository.save(board);
            rttr.addFlashAttribute("successMessage", "✅ 새로운 커뮤니티 주제[" + topicName + "]가 생성되었습니다.");
        } catch (Exception e) {
            rttr.addFlashAttribute("errorMessage", "❌ 게시판 생성 실패 (중복된 이름일 수 있습니다)");
        }
        return "redirect:/admin/members";
    }

    @Transactional
    @PostMapping("/boards/{id}/delete")
    public String deleteBoard(@PathVariable(name = "id") Long id, HttpSession session, RedirectAttributes rttr) {
        if (!isAdmin(session)) return "redirect:/";

        try {
            communityBoardRepository.deleteById(id);
            rttr.addFlashAttribute("successMessage", "✅ 게시판이 성공적으로 삭제되었습니다 (연관된 모든 게시글 및 댓글 삭제됨).");
        } catch (Exception e) {
            rttr.addFlashAttribute("errorMessage", "❌ 게시판 삭제 실패");
        }
        return "redirect:/admin/members";
    }

    // ==========================================
    // 6. 신규 기능: 뉴스 자동 수집(AI 요약) 토글 스위치
    // ==========================================
    @PostMapping("/news/toggle")
    public String toggleAutoNews(HttpSession session, RedirectAttributes rttr) {
        if (!isAdmin(session))
            return "redirect:/";

        newsService.toggleAutoNews();

        String state = newsService.isAutoNewsEnabled() ? "ON" : "OFF";
        rttr.addFlashAttribute("successMessage", "✅ 뉴스 자동 수집 기능이 " + state + " 되었습니다.");

        return "redirect:/admin/members";
    }

    // ==========================================
    // 7. 신규 기능: 모의투자 전체 초기화
    // ==========================================
    @PostMapping("/mock-invest/reset")
    public String resetMockInvestment(HttpSession session, RedirectAttributes rttr) {
        if (!isAdmin(session))
            return "redirect:/";

        try {
            tradingService.resetAllMockInvestmentData();
            rttr.addFlashAttribute("successMessage", "✅ 모든 모의투자 데이터가 성공적으로 초기화되었습니다.");
        } catch (Exception e) {
            rttr.addFlashAttribute("errorMessage", "❌ 초기화 실패: " + e.getMessage());
        }

        return "redirect:/admin/members";
    }

    // ==========================================
    // 8. 신규 기능: 사용자 비밀번호 초기화 (아이디와 동일하게)
    // ==========================================
    @Transactional
    @PostMapping("/members/{id}/password-reset")
    public String resetPassword(@PathVariable(name = "id") Long id, HttpSession session, RedirectAttributes rttr) {
        if (!isAdmin(session))
            return "redirect:/";

        Member member = memberRepository.findById(id).orElseThrow();
        member.setPassword(passwordEncoder.encode(member.getLoginId())); // 🔒 BCrypt 암호화

        rttr.addFlashAttribute("successMessage", "✅ " + member.getNickname() + " 님의 비밀번호가 초기화되었습니다.");
        return "redirect:/admin/members";
    }

    // ==========================================
    // 9. 신규 기능: 회원 삭제
    // ==========================================
    @Transactional
    @PostMapping("/members/{id}/delete")
    public String deleteMember(@PathVariable(name = "id") Long id, HttpSession session, RedirectAttributes rttr) {
        if (!isAdmin(session))
            return "redirect:/";

        if (session.getAttribute("loginMember") != null) {
            Member login = (Member) session.getAttribute("loginMember");
            if (login.getId().equals(id)) {
                rttr.addFlashAttribute("errorMessage", "❌ 자기 자신(관리자)은 삭제할 수 없습니다.");
                return "redirect:/admin/members";
            }
        }

        try {
            // JPQL 사용: Entity 이름과 필드 기반으로 더 안전하게 삭제
            em.createQuery(
                    "DELETE FROM PendingOrder po WHERE po.account.id IN (SELECT a.id FROM TradingAccount a WHERE a.memberId = :id)")
                    .setParameter("id", id).executeUpdate();
            em.createQuery(
                    "DELETE FROM TradeHistory th WHERE th.account.id IN (SELECT a.id FROM TradingAccount a WHERE a.memberId = :id)")
                    .setParameter("id", id).executeUpdate();
            em.createQuery(
                    "DELETE FROM Holding h WHERE h.account.id IN (SELECT a.id FROM TradingAccount a WHERE a.memberId = :id)")
                    .setParameter("id", id).executeUpdate();
            em.createQuery("DELETE FROM TradingAccount a WHERE a.memberId = :id").setParameter("id", id)
                    .executeUpdate();

            em.createQuery("DELETE FROM CardNewsComment c WHERE c.member.id = :id").setParameter("id", id)
                    .executeUpdate();
            // 다대다 조인 테이블은 Native 쿼리 유지
            em.createNativeQuery("DELETE FROM card_news_likes WHERE member_id = :id").setParameter("id", id)
                    .executeUpdate();
            em.createNativeQuery("DELETE FROM card_news_saves WHERE member_id = :id").setParameter("id", id)
                    .executeUpdate();

            em.createQuery("DELETE FROM CrewApplication ca WHERE ca.member.id = :id").setParameter("id", id)
                    .executeUpdate();
            em.createQuery("DELETE FROM CrewMessage cm WHERE cm.sender.id = :id").setParameter("id", id)
                    .executeUpdate();
            em.createQuery("DELETE FROM CrewMember cm WHERE cm.member.id = :id").setParameter("id", id).executeUpdate();
            em.createQuery("DELETE FROM CrewStrategyLog csl WHERE csl.author.id = :id").setParameter("id", id).executeUpdate();

            em.createQuery("DELETE FROM Discussion d WHERE d.member.id = :id").setParameter("id", id).executeUpdate();
            em.createQuery("DELETE FROM WatchlistMemo wm WHERE wm.member.id = :id").setParameter("id", id)
                    .executeUpdate();
            em.createQuery("DELETE FROM Watchlist w WHERE w.member.id = :id").setParameter("id", id).executeUpdate();

            em.createQuery("DELETE FROM Notification n WHERE n.recipient.id = :id").setParameter("id", id).executeUpdate();
            em.createQuery("DELETE FROM TpHistory th WHERE th.member.id = :id").setParameter("id", id).executeUpdate();

            // 커뮤니티 데이터 정리
            em.createNativeQuery("DELETE FROM community_post_upvotes WHERE member_id = :id").setParameter("id", id).executeUpdate();
            em.createNativeQuery("DELETE FROM community_board_subscribers WHERE member_id = :id").setParameter("id", id).executeUpdate();
            em.createQuery("DELETE FROM CommunityComment cc WHERE cc.author.id = :id").setParameter("id", id).executeUpdate();
            em.createQuery("DELETE FROM CommunityPost cp WHERE cp.author.id = :id").setParameter("id", id).executeUpdate();

            memberRepository.deleteById(id);
            rttr.addFlashAttribute("successMessage", "✅ 회원이 성공적으로 삭제되었습니다.");
        } catch (Exception e) {
            e.printStackTrace();
            rttr.addFlashAttribute("errorMessage", "❌ 회원 삭제에 실패했습니다: 연관된 데이터를 모두 지우지 못했습니다. " + e.getMessage());
        }

        return "redirect:/admin/members";
    }

    // ==========================================
    // 10. 신규 기능: 관리자 알림 발송 (개인/크루/전체)
    // ==========================================
    @PostMapping("/notifications/send")
    public String sendNotification(@RequestParam(name = "targetType") String targetType,
                                   @RequestParam(name = "targetId", required = false) Long targetId,
                                   @RequestParam(name = "title") String title,
                                   @RequestParam(name = "message") String message,
                                   @RequestParam(name = "link", required = false) String link,
                                   HttpSession session, RedirectAttributes rttr) {
        if (!isAdmin(session)) return "redirect:/";

        try {
            switch (targetType) {
                case "ALL":
                    notificationService.sendNotificationToAll(title, message, link);
                    rttr.addFlashAttribute("successMessage", "✅ 전체 사용자에게 알림이 발송되었습니다.");
                    break;
                case "CREW":
                    if (targetId == null) throw new IllegalArgumentException("대상 크루를 선택해주세요.");
                    notificationService.sendNotificationToCrew(targetId, title, message, link);
                    rttr.addFlashAttribute("successMessage", "✅ 해당 크루 멤버들에게 알림이 발송되었습니다.");
                    break;
                case "MEMBER":
                    if (targetId == null) throw new IllegalArgumentException("대상 회원을 선택해주세요.");
                    Member member = memberRepository.findById(targetId).orElseThrow();
                    notificationService.createNotification(member, title, message, link);
                    rttr.addFlashAttribute("successMessage", "✅ [" + member.getNickname() + "] 님에게 알림이 발송되었습니다.");
                    break;
                default:
                    throw new IllegalArgumentException("올바르지 않은 전송 대상입니다.");
            }
        } catch (Exception e) {
            rttr.addFlashAttribute("errorMessage", "❌ 알림 발송 실패: " + e.getMessage());
        }

        return "redirect:/admin/members";
    }

    // ==========================================
    // 11. 캡러스 기능: 카드뉴스 관리
    // ==========================================
    @GetMapping("/cardnews")
    @Transactional(readOnly = true)
    public String cardNewsList(HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/";

        // JOIN FETCH로 pages까지 한 번에 로딩 (LazyInit 완전 방지)
        List<CardNews> list = cardNewsRepository.findAllWithPagesFetched();

        // View에서 쓰기 쉽도록 DTO Map 변환
        List<java.util.Map<String, Object>> newsList = list.stream().map(cn -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", cn.getId());
            m.put("topic", cn.getTopic());
            m.put("createdDate", cn.getCreatedDate());
            m.put("instagramCaption", cn.getInstagramCaption());
            m.put("pageCount", cn.getPages().size());
            return m;
        }).collect(java.util.stream.Collectors.toList());

        model.addAttribute("newsList", newsList);
        return "admin/cardnews";
    }

    @GetMapping("/cardnews/{id}/edit")
    @Transactional(readOnly = true)
    public String cardNewsEditForm(@PathVariable("id") Long id, HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/";
        CardNews cn = cardNewsRepository.findByIdWithPages(id).orElse(null);
        if (cn == null) return "redirect:/admin/cardnews";

        // 트랜잭션 범위 안에서 데이터를 DTO로 완전히 복사 (LazyInit 방지)
        java.util.Map<String, Object> cardNewsDto = new java.util.LinkedHashMap<>();
        cardNewsDto.put("id", cn.getId());
        cardNewsDto.put("topic", cn.getTopic());
        cardNewsDto.put("createdDate", cn.getCreatedDate());
        cardNewsDto.put("instagramCaption", cn.getInstagramCaption());

        // pages도 일반 Map 리스트로 변환 (Hibernate Proxy 해제)
        java.util.List<java.util.Map<String, Object>> pagesDtoList = cn.getPages().stream()
                .sorted(java.util.Comparator.comparingInt(com.pj.stock_console.entity.CardPage::getPageNum))
                .map(p -> {
                    java.util.Map<String, Object> pm = new java.util.LinkedHashMap<>();
                    pm.put("id", p.getId());
                    pm.put("pageNum", p.getPageNum());
                    pm.put("title", p.getTitle());
                    pm.put("content", p.getContent());
                    return pm;
                })
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("cardNews", cardNewsDto);
        model.addAttribute("pages", pagesDtoList);
        return "admin/cardnews-edit";
    }

    @PostMapping("/cardnews/{id}/edit")
    @Transactional
    public String cardNewsEditSave(@PathVariable("id") Long id,
                                   @RequestParam("topic") String topic,
                                   @RequestParam(name = "instagramCaption", required = false) String instagramCaption,
                                   @RequestParam("pageCount") int pageCount,
                                   HttpSession session,
                                   RedirectAttributes rttr,
                                   jakarta.servlet.http.HttpServletRequest req) {
        if (!isAdmin(session)) return "redirect:/";

        CardNews cn = cardNewsRepository.findByIdWithPages(id).orElse(null);
        if (cn == null) return "redirect:/admin/cardnews";

        cn.setTopic(topic.trim());
        cn.setInstagramCaption(instagramCaption);

        for (int i = 0; i < pageCount; i++) {
            String pageIdStr = req.getParameter("pageId_" + i);
            String title = req.getParameter("title_" + i);
            String content = req.getParameter("content_" + i);
            if (pageIdStr != null) {
                Long pageId = Long.parseLong(pageIdStr);
                cn.getPages().stream()
                        .filter(p -> p.getId().equals(pageId))
                        .findFirst()
                        .ifPresent(p -> {
                            p.setTitle(title != null ? title.trim() : "");
                            p.setContent(content != null ? content.trim() : "");
                        });
            }
        }
        cardNewsRepository.save(cn);
        rttr.addFlashAttribute("successMessage", "'" + topic + "' 카드뉴스가 수정되었습니다.");
        return "redirect:/admin/cardnews";
    }

    @PostMapping("/cardnews/{id}/delete")
    @Transactional
    public String cardNewsDelete(@PathVariable("id") Long id, HttpSession session, RedirectAttributes rttr) {
        if (!isAdmin(session)) return "redirect:/";
        try {
            em.createQuery("DELETE FROM CardNewsComment c WHERE c.cardNews.id = :id").setParameter("id", id).executeUpdate();
            em.createNativeQuery("DELETE FROM card_news_likes WHERE card_news_id = :id").setParameter("id", id).executeUpdate();
            em.createNativeQuery("DELETE FROM card_news_saves WHERE card_news_id = :id").setParameter("id", id).executeUpdate();
            em.createQuery("DELETE FROM CardPage p WHERE p.cardNews.id = :id").setParameter("id", id).executeUpdate();
            cardNewsRepository.deleteById(id);
            rttr.addFlashAttribute("successMessage", "카드뉴스 #" + id + "가 삭제되었습니다.");
        } catch (Exception e) {
            rttr.addFlashAttribute("errorMessage", "삭제 실패: " + e.getMessage());
        }
        return "redirect:/admin/cardnews";
    }
}
package com.pj.stock_console.controller;

import com.pj.stock_console.entity.Crew;
import com.pj.stock_console.entity.CrewRole;
import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.CrewMessage;
import com.pj.stock_console.entity.CrewApplication;
import com.pj.stock_console.service.CrewService;
import com.pj.stock_console.repository.CrewMessageRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/crew")
@RequiredArgsConstructor
public class CrewController {

    private final CrewService crewService;
    private final CrewMessageRepository crewMessageRepository;
    private final com.pj.stock_console.service.TradingService tradingService;
    private static final String LOGIN_MEMBER = "loginMember";
    private static final String ERROR_MESSAGE = "errorMessage";

    /**
     * 내 크루 관리 페이지 (Dashboard)
     */
    @GetMapping
    public String crewList(HttpSession session, Model model) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) {
            return "redirect:/login?redirectURL=/crew";
        }

        List<Crew> myCrews = crewService.findMyCrews(loginMember.getId());
        List<CrewApplication> pendingApplications = crewService.getPendingApplicationsByMember(loginMember.getId());

        Map<Long, Boolean> isLeaderMap = new HashMap<>();
        Map<Long, Integer> pendingAppCounts = new HashMap<>();
        for (Crew c : myCrews) {
            boolean isLeader = c.getMembers().stream()
                    .anyMatch(cm -> cm.getMember().getId().equals(loginMember.getId())
                            && cm.getRole() == CrewRole.LEADER);
            isLeaderMap.put(c.getId(), isLeader);

            if (isLeader && c.isRequireApproval()) {
                pendingAppCounts.put(c.getId(), crewService.getPendingApplications(c.getId()).size());
            } else {
                pendingAppCounts.put(c.getId(), 0);
            }
        }

        model.addAttribute("myCrews", myCrews);
        model.addAttribute("pendingApplications", pendingApplications);
        model.addAttribute("isLeaderMap", isLeaderMap);
        model.addAttribute("pendingAppCounts", pendingAppCounts);
        
        // JS 비동기 로딩용 ID 목록
        model.addAttribute("myCrewIds", myCrews.stream().map(Crew::getId).toList());
        model.addAttribute("pendingAppCrewIds", pendingApplications.stream()
                .map(app -> app.getCrew() != null ? app.getCrew().getId() : null)
                .filter(java.util.Objects::nonNull)
                .toList());

        return "crew/list";
    }

    /**
     * 크루 구인 게시판 (탐색)
     */
    @GetMapping("/explore")
    public String exploreCrews(HttpSession session, Model model) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        List<Crew> allCrews = crewService.findAllCrews();

        model.addAttribute("allCrews", allCrews);
        model.addAttribute("member", loginMember);
        
        // JS 비동기 로딩용 ID 목록
        model.addAttribute("allCrewIds", allCrews.stream().map(Crew::getId).toList());
        
        return "crew/explore";
    }

    /**
     * 크루 상세 (Space 2 - 대시보드)
     */
    @GetMapping("/{id}")
    public String crewDetail(@PathVariable("id") Long id, HttpSession session, Model model, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);

        try {
            Crew crew = crewService.findById(id);
            boolean isMember = (loginMember != null) && crew.hasMember(loginMember.getId());
            boolean isAdmin = (loginMember != null) && loginMember.isAdmin();

            // 관리자는 크루 미가입이어도 전체 데이터에 접근 가능
            if (isAdmin) {
                isMember = true;
            }

            // 리더 여부 확인 (관리자는 리더 권한 없음)
            boolean isLeader = (loginMember != null) && crew.getMembers().stream()
                    .anyMatch(cm -> cm.getMember().getId().equals(loginMember.getId())
                            && cm.getRole() == CrewRole.LEADER);

            model.addAttribute("crew", crew);
            model.addAttribute("isMember", isMember);
            model.addAttribute("isLeader", isLeader);
            model.addAttribute("isAdmin", isAdmin);

            if (isLeader) {
                model.addAttribute("pendingApplications", crewService.getPendingApplications(id));
            }

            // 가입자(또는 관리자)일 경우 채팅 메시지 로드 및 수익금 계산
            if (isMember) {
                List<CrewMessage> chatMessages = crewMessageRepository.findAllByCrewIdOrderByCreatedAtAsc(id);
                model.addAttribute("chatMessages", chatMessages);

                List<com.pj.stock_console.entity.CrewStrategyLog> strategyLogs = crewService.getStrategyLogs(id);
                model.addAttribute("strategyLogs", strategyLogs);
            } else if (loginMember != null) {
                // 비가입자일 경우 해당 크루에 신청한 내역이 있는지 (대기/거절 등)
                CrewApplication myApplication = crewService.getLatestApplication(id, loginMember.getId());
                model.addAttribute("myApplication", myApplication);
            }

            return "crew/detail";
        } catch (Exception e) {
            rttr.addFlashAttribute(ERROR_MESSAGE, e.getMessage());
            return "redirect:/crew";
        }
    }

    /**
     * 크루 생성
     */
    @PostMapping("/create")
    public String createCrew(@RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam(value = "requireApproval", defaultValue = "false") boolean requireApproval,
            HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null)
            return "redirect:/login";

        try {
            Crew createdCrew = crewService.createCrew(name, description, loginMember, requireApproval);
            rttr.addFlashAttribute("successMessage", "새로운 트레이딩 크루가 창설되었습니다.");
            return "redirect:/crew/" + createdCrew.getId();
        } catch (Exception e) {
            rttr.addFlashAttribute(ERROR_MESSAGE, e.getMessage());
            return "redirect:/crew";
        }
    }

    /**
     * 크루 크루 가입 (역할 선택 포함)
     */
    @PostMapping("/{id}/join")
    public String joinCrew(@PathVariable("id") Long id, @RequestParam("role") CrewRole role,
            HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null)
            return "redirect:/login";

        try {
            crewService.joinCrew(id, loginMember, role);
            rttr.addFlashAttribute("successMessage", "크루에 성공적으로 합류했습니다!");
        } catch (Exception e) {
            rttr.addFlashAttribute(ERROR_MESSAGE, e.getMessage());
        }
        return "redirect:/crew/" + id;
    }

    /**
     * 크루 참가 신청 (승인제)
     */
    @PostMapping("/{id}/apply")
    public String applyCrew(@PathVariable("id") Long id, @RequestParam("role") CrewRole role,
            @RequestParam("message") String message,
            HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null)
            return "redirect:/login";

        try {
            crewService.applyCrew(id, loginMember, role, message);
            rttr.addFlashAttribute("successMessage", "크루 가입 신청이 전송되었습니다. 리더의 승인을 기다려주세요.");
        } catch (Exception e) {
            rttr.addFlashAttribute(ERROR_MESSAGE, e.getMessage());
        }
        return "redirect:/crew/" + id;
    }

    /**
     * 신청 승인/거절
     */
    @PostMapping("/application/{appId}/{action}")
    public String processApplication(@PathVariable("appId") Long appId, @PathVariable("action") String action,
            @RequestParam("crewId") Long crewId,
            HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null)
            return "redirect:/login";

        try {
            if ("approve".equals(action)) {
                crewService.approveApplication(appId, loginMember.getId());
                rttr.addFlashAttribute("successMessage", "신청을 승인했습니다.");
            } else if ("reject".equals(action)) {
                crewService.rejectApplication(appId, loginMember.getId());
                rttr.addFlashAttribute("successMessage", "신청을 거절했습니다.");
            }
        } catch (Exception e) {
            rttr.addFlashAttribute(ERROR_MESSAGE, e.getMessage());
        }
        return "redirect:/crew/" + crewId;
    }

    /**
     * 신청 일괄 승인
     */
    @PostMapping("/{id}/approve-all")
    public String approveAllApplications(@PathVariable("id") Long id, HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) return "redirect:/login";

        try {
            int count = crewService.approveAllApplications(id, loginMember.getId());
            rttr.addFlashAttribute("successMessage", count + "명의 신청을 일괄 승인했습니다.");
        } catch (Exception e) {
            rttr.addFlashAttribute(ERROR_MESSAGE, e.getMessage());
        }
        return "redirect:/crew/" + id;
    }

    /**
     * 크루 전략 일지(메모장) 업데이트
     */
    @PostMapping("/{id}/log")
    public String updateStrategyLog(@PathVariable("id") Long id, @RequestParam("strategyLog") String strategyLog,
            HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) return "redirect:/login";

        try {
            crewService.addStrategyLog(id, strategyLog, loginMember);
            rttr.addFlashAttribute("successMessage", "전략 일지가 추가되었습니다.");
        } catch (Exception e) {
            rttr.addFlashAttribute(ERROR_MESSAGE, e.getMessage());
        }
        return "redirect:/crew/" + id;
    }

    /**
     * 크루 탈퇴 및 삭제
     */
    @PostMapping("/{id}/leave")
    public String leaveCrew(@PathVariable("id") Long id, HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null)
            return "redirect:/login";

        try {
            crewService.leaveCrew(id, loginMember.getId());
            rttr.addFlashAttribute("successMessage", "크루 탈퇴/삭제가 완료되었습니다.");
        } catch (Exception e) {
            rttr.addFlashAttribute(ERROR_MESSAGE, e.getMessage());
        }
        return "redirect:/crew";
    }

    /**
     * 관리자 전용 크루 강제 삭제
     */
    @PostMapping("/{id}/admin-delete")
    public String adminDeleteCrew(@PathVariable("id") Long id, 
            @RequestParam(value = "reason", required = false) String reason,
            HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) return "redirect:/login";
        if (!loginMember.isAdmin()) {
            rttr.addFlashAttribute(ERROR_MESSAGE, "관리자만 접근할 수 있습니다.");
            return "redirect:/crew/" + id;
        }

        try {
            crewService.adminDeleteCrew(id, reason);
            rttr.addFlashAttribute("successMessage", "크루가 관리자에 의해 강제 삭제되었습니다.");
        } catch (Exception e) {
            rttr.addFlashAttribute(ERROR_MESSAGE, e.getMessage());
        }
        return "redirect:/crew";
    }
}

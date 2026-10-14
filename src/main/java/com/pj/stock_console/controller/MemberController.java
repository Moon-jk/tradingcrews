package com.pj.stock_console.controller;

import com.pj.stock_console.entity.Member;
import com.pj.stock_console.repository.MemberRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.pj.stock_console.security.InputValidator;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class MemberController {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initCreatedAtForExistingMembers() {
        List<Member> members = memberRepository.findAll();
        for (Member m : members) {
            if (m.getCreatedAt() == null) {
                m.setCreatedAt(LocalDate.now());
            }
        }
    }

    // 로그인 페이지 이동
    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "redirectURL", required = false) String redirectURL,
            org.springframework.ui.Model model, jakarta.servlet.http.HttpServletRequest request) {
        
        // 🚀 버퍼 플러시 전 CSRF 토큰/세션을 미리 생성하여 IllegalStateException 방지
        org.springframework.security.web.csrf.CsrfToken csrfToken = 
            (org.springframework.security.web.csrf.CsrfToken) request.getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken(); // 강제 할당 트리거
        }

        if (redirectURL != null && !redirectURL.isBlank()) {
            model.addAttribute("redirectURL", redirectURL);
        }
        return "login";
    }

    // 🛑 [삭제] 로그인 실행 (Spring Security에서 처리함)
    // - 기존 @PostMapping("/login") 코드는 SecurityConfig의 formLogin 설정으로 대체되었습니다.

    // 🛑 [삭제] 로그아웃 실행 (Spring Security에서 처리함)
    // - 기존 @GetMapping("/logout") 코드는 SecurityConfig의 logout 설정으로 대체되었습니다.

    // ==========================================
    // 회원가입 페이지 이동
    // ==========================================
    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }

    // ==========================================
    // 회원가입 실행
    // ==========================================
    @PostMapping("/signup")
    public String signupSubmit(@RequestParam("loginId") String loginId,
            @RequestParam("nickname") String nickname,
            RedirectAttributes rttr) {

        // 🔒 입력값 검증
        if (!InputValidator.isValidLoginId(loginId)) {
            rttr.addFlashAttribute("errorMessage", "아이디는 영문, 숫자, 특수문자(._@-)만 사용 가능합니다. (3~50자)");
            return "redirect:/signup";
        }
        if (!InputValidator.isValidNickname(nickname)) {
            rttr.addFlashAttribute("errorMessage", "닉네임은 한글, 영문, 숫자만 사용 가능합니다. (2~20자)");
            return "redirect:/signup";
        }

        // 아이디 중복 검사
        if (memberRepository.existsByLoginId(loginId)) {
            rttr.addFlashAttribute("errorMessage", "이미 존재하는 아이디입니다.");
            return "redirect:/signup";
        }

        Member newMember = new Member();
        newMember.setLoginId(loginId);
        newMember.setPassword(passwordEncoder.encode(loginId)); // 🔒 BCrypt 암호화 저장
        newMember.setNickname(nickname);
        newMember.setRole(com.pj.stock_console.entity.Role.TIER3); // 하위 등급으로 설정
        newMember.setCreatedAt(LocalDate.now()); // 가입일자 설정

        memberRepository.save(newMember);

        rttr.addFlashAttribute("successMessage", "가입이 완료되었습니다! 아이디로 로그인해주세요.");
        return "redirect:/login";
    }

    // ==========================================
    // 🌟 [새로 추가된 부분] 강제 비밀번호 변경 화면 (GET)
    // ==========================================
    @GetMapping("/member/force-change-password")
    public String forceChangePasswordForm(@RequestParam(value = "redirectURL", required = false) String redirectURL,
            HttpSession session, org.springframework.ui.Model model) {
        // 로그인이 안 된 상태면 로그인 페이지로 튕겨냄 (보안)
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login";
        }

        // 소셜 로그인 회원은 비밀번호가 없으므로 해당 페이지 진입을 차단 (메인으로 리다이렉트)
        if (loginMember.isSocialUser()) {
            return "redirect:/";
        }

        if (redirectURL != null && !redirectURL.isBlank()) {
            model.addAttribute("redirectURL", redirectURL);
        }
        // templates/member/force-change-password.html 화면을 띄워줌
        return "member/force-change-password";
    }

    // ==========================================
    // 비밀번호 변경 처리 (POST)
    // ==========================================
    @Transactional
    @PostMapping("/member/force-change-password")
    public String forceChangePasswordSubmit(@RequestParam(name = "newPassword") String newPassword,
            @RequestParam(name = "confirmPassword") String confirmPassword,
            @RequestParam(value = "redirectURL", required = false) String redirectURL,
            HttpSession session,
            RedirectAttributes rttr) {

        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null)
            return "redirect:/login";

        // 1. 새 비밀번호와 확인 비밀번호가 일치하는지 체크
        if (!newPassword.equals(confirmPassword)) {
            rttr.addFlashAttribute("errorMessage", "비밀번호가 일치하지 않습니다. 다시 입력해 주세요.");
            return "redirect:/member/force-change-password";
        }

        // 2. 기존 비밀번호(아이디)와 똑같이 입력했는지 체크
        if (newPassword.equals(loginMember.getLoginId())) {
            rttr.addFlashAttribute("errorMessage", "보안을 위해 초기 비밀번호(아이디)와 다른 비밀번호를 설정해 주세요.");
            return "redirect:/member/force-change-password";
        }

        // 3. DB 업데이트
        Member member = memberRepository.findById(loginMember.getId()).orElseThrow();
        member.setPassword(passwordEncoder.encode(newPassword)); // 🔒 BCrypt 암호화 저장

        // 4. 세션 정보 최신화
        session.setAttribute("loginMember", member);

        // 🔒 Open Redirect 방지: 내부 경로만 허용
        String safeUrl = "/";
        if (redirectURL != null && !redirectURL.isBlank() && redirectURL.startsWith("/") && !redirectURL.startsWith("//")) {
            safeUrl = redirectURL;
        }
        rttr.addFlashAttribute("successMessage", "🔒 비밀번호가 안전하게 변경되었습니다. 환영합니다!");
        return "redirect:" + safeUrl;
    }

    // ==========================================
    // 출석 체크 (TP 획득)
    // ==========================================
    @PostMapping("/attendance/check")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> checkAttendance(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        }
        
        // Re-fetch member from DB to get the latest state
        Member member = memberRepository.findById(loginMember.getId()).orElse(null);
        if (member == null) {
            return ResponseEntity.status(401).body(Map.of("error", "유저를 찾을 수 없습니다."));
        }

        LocalDate today = LocalDate.now();
        if (member.getLastAttendanceDate() != null && member.getLastAttendanceDate().isEqual(today)) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "이미 오늘 출석하셨습니다.", 
                "tp", member.getTp()
            ));
        }

        member.setLastAttendanceDate(today);
        member.setTp(member.getTp() + 50); // 50 TP 획득
        memberRepository.save(member);
        
        // Update session
        session.setAttribute("loginMember", member);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "출석체크 완료! 50 TP 획득!", 
            "tp", member.getTp()
        ));
    }

    // ==========================================
    // 카드뉴스 읽기 미션 완료 (TP 획득)
    // ==========================================
    @PostMapping("/infocenter/mission/read")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> completeReadMission(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        }
        
        Member member = memberRepository.findById(loginMember.getId()).orElse(null);
        if (member == null) {
            return ResponseEntity.status(401).body(Map.of("error", "유저를 찾을 수 없습니다."));
        }

        LocalDate today = LocalDate.now();
        if (member.getLastCardNewsReadDate() != null && member.getLastCardNewsReadDate().isEqual(today)) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "이미 오늘 카드뉴스 미션을 완료하셨습니다.", 
                "tp", member.getTp()
            ));
        }

        member.setLastCardNewsReadDate(today);
        member.setTp(member.getTp() + 50); // 50 TP 획득
        memberRepository.save(member);
        
        session.setAttribute("loginMember", member);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "카드뉴스 읽기 미션 완료! 50 TP 획득!", 
            "tp", member.getTp()
        ));
    }
}
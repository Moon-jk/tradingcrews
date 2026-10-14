package com.pj.stock_console.security;

import com.pj.stock_console.entity.Member;
import com.pj.stock_console.repository.MemberRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final MemberRepository memberRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        String loginId = "";
        Member member = null;

        if (authentication.getPrincipal() instanceof UserDetails) {
            // Form Login
            loginId = ((UserDetails) authentication.getPrincipal()).getUsername();
            member = memberRepository.findByLoginId(loginId).orElse(null);
        } else if (authentication.getPrincipal() instanceof OAuth2User) {
            // OAuth2 Login
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            String providerId = String.valueOf(oAuth2User.getAttributes().get("sub")); // Google 고유 ID
            member = memberRepository.findByProviderAndProviderId("google", providerId).orElse(null);
        }

        if (member != null) {
            // 🌟 [추가됨] 평문 비밀번호 사용자라면 첫 로그인 시 자동으로 암호화하여 DB 저장
            String rawPassword = request.getParameter("password");
            org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder bcrypt = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
            if (rawPassword != null && !member.isSocialUser() && !member.getPassword().startsWith("$2a$")) {
                // 기존 평문 비밀번호를 BCrypt로 암호화하여 DB를 업데이트합니다.
                member.setPassword(bcrypt.encode(rawPassword));
                memberRepository.save(member);
            }

            HttpSession session = request.getSession();
            session.setAttribute("loginMember", member);

            // 🔒 초기 비밀번호(=아이디) 변경 강제 — BCrypt 환경 호환
            if (!member.isSocialUser() && rawPassword != null && rawPassword.equals(member.getLoginId())) {
                setDefaultTargetUrl("/member/force-change-password");
            } else {
                setDefaultTargetUrl("/");
            }
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}

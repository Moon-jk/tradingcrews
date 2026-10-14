package com.pj.stock_console.security;

import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.Role;
import com.pj.stock_console.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId(); // "google"
        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        Map<String, Object> attributes = oAuth2User.getAttributes();

        // Google의 경우 "sub", "email", "name" 등을 가져올 수 있습니다.
        String providerId = String.valueOf(attributes.get("sub"));
        String email = String.valueOf(attributes.get("email"));
        String name = String.valueOf(attributes.get("name"));

        // 유저 정보 가져오기 혹은 가입
        Member member = memberRepository.findByProviderAndProviderId(registrationId, providerId)
                .orElseGet(() -> {
                    Member newMember = new Member();
                    newMember.setLoginId(email); // 이메일을 로그인 아이디로 사용
                    newMember.setPassword(""); // 소셜 로그인 사용자는 비밀번호가 없음
                    newMember.setNickname(name);
                    newMember.setRole(Role.TIER3);
                    newMember.setProvider(registrationId);
                    newMember.setProviderId(providerId);
                    return memberRepository.save(newMember);
                });

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())),
                attributes,
                userNameAttributeName
        );
    }
}

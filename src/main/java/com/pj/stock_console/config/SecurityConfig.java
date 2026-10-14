package com.pj.stock_console.config;

import com.pj.stock_console.security.CustomAuthenticationSuccessHandler;
import com.pj.stock_console.security.CustomOAuth2UserService;
import com.pj.stock_console.security.SecurityRequestFilter;
import com.pj.stock_console.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    private final SecurityRequestFilter securityRequestFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new PasswordEncoder() {
            private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

            @Override
            public String encode(CharSequence rawPassword) {
                return bcrypt.encode(rawPassword);
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                // 1. 이미 BCrypt로 암호화된 경우 ($2a$ 로 시작)
                if (encodedPassword != null && encodedPassword.startsWith("$2a$")) {
                    return bcrypt.matches(rawPassword, encodedPassword);
                }
                // 2. 평문인 경우 (이전 테스트용)
                return rawPassword.toString().equals(encodedPassword);
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/infocenter/api/**", "/health", "/api/v1/magical/**") // 외부 API 및 공용 API CSRF 예외
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin()) // Clickjacking 방지
                .contentTypeOptions(contentType -> {}) // MIME sniffing 방지
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000) // 1년
                )
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/signup", "/css/**", "/js/**", "/images/**", "/vendor/**", "/static/**", "/favicon.ico").permitAll()
                .requestMatchers("/infocenter/api/**", "/api/v1/magical/**").permitAll() // 외부 연동 및 공용 API
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // 🔐 보호된 페이지 (로그인 필요)
                .requestMatchers("/mydashboard/**", "/notifications/**", "/profile/**", "/todo/**", "/trading/**").authenticated()
                // 🏹 상호작용 (POST, PUT, DELETE)은 로그인이 되어 있어야 함
                .requestMatchers(HttpMethod.POST, "/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/**").authenticated()
                // 🔓 그 외 모든 요청(주로 GET)은 로그인 없이 허용
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login") // POST /login 을 시큐리티가 가로챔
                .usernameParameter("loginId")
                .passwordParameter("password")
                .successHandler(customAuthenticationSuccessHandler) // 세션 처리를 위한 핸들러
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .successHandler(customAuthenticationSuccessHandler)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            // 🛡️ 보안 필터 체인: Rate Limit → SQL/XSS 감지 → 인증
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(securityRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

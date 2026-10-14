package com.pj.stock_console.repository;

import com.pj.stock_console.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long> {
    // 로그인 ID로 회원 조회 (로그인 검증 시 사용)
    Optional<Member> findByLoginId(String loginId);
    // [추가] 로그인 아이디가 이미 존재하는지 확인하는 메서드 (회원가입 시 중복 체크용)
    boolean existsByLoginId(String loginId);

    // [추가] 소셜 로그인 시 사용자 조회를 위한 메서드
    Optional<Member> findByProviderAndProviderId(String provider, String providerId);

    // [추가] 등급(Role) 순 + ID 순으로 전체 회원 조회
    List<Member> findAllByOrderByRoleAscIdAsc();
}
package com.pj.stock_console.repository;

import com.pj.stock_console.entity.CrewMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CrewMemberRepository extends JpaRepository<CrewMember, Long> {

    // 유저가 속한 크루 개수 확인
    int countByMemberId(Long memberId);

    // 특정 크루에 유저가 속해있는지 확인
    boolean existsByCrewIdAndMemberId(Long crewId, Long memberId);

    // 특정 크루와 유저로 검색
    Optional<CrewMember> findByCrewIdAndMemberId(Long crewId, Long memberId);

    // 유저가 속한 크루 목록 조회 (Lazy 로딩 에러 방지를 위해 연관 크루 로드)
    @EntityGraph(attributePaths = { "crew", "crew.members", "crew.members.member" })
    List<CrewMember> findAllByMemberId(Long memberId);
}

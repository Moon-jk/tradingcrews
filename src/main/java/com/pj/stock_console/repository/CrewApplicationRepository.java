package com.pj.stock_console.repository;

import com.pj.stock_console.entity.ApplicationStatus;
import com.pj.stock_console.entity.CrewApplication;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CrewApplicationRepository extends JpaRepository<CrewApplication, Long> {

    // 특정 멤버가 특정 크루에 "대기중"인 신청이 있는지 확인
    boolean existsByCrewIdAndMemberIdAndStatus(Long crewId, Long memberId, ApplicationStatus status);

    // 승인 대상(리더가 볼 목록)
    @EntityGraph(attributePaths = { "member" })
    List<CrewApplication> findAllByCrewIdAndStatusOrderByCreatedAtDesc(Long crewId, ApplicationStatus status);

    @EntityGraph(attributePaths = { "crew", "member" })
    Optional<CrewApplication> findById(Long id);

    // 본인이 최근에 신청한 내역 조회 (상태값 상관없이 하나)
    Optional<CrewApplication> findFirstByCrewIdAndMemberIdOrderByCreatedAtDesc(Long crewId, Long memberId);

    // 본인이 신청한 상태별 목록 조회 (Crew 정보와 함께)
    @EntityGraph(attributePaths = { "crew", "crew.members", "crew.members.member" })
    List<CrewApplication> findAllByMemberIdAndStatusOrderByCreatedAtDesc(Long memberId, ApplicationStatus status);
}

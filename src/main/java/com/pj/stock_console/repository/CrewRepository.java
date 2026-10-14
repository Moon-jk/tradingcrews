package com.pj.stock_console.repository;

import com.pj.stock_console.entity.Crew;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CrewRepository extends JpaRepository<Crew, Long> {

    @EntityGraph(attributePaths = { "members", "members.member" })
    List<Crew> findAll();

    @EntityGraph(attributePaths = { "members", "members.member" })
    Optional<Crew> findById(Long id);

    @EntityGraph(attributePaths = { "members", "members.member" })
    @Query("select c from Crew c join c.members m where m.member.id = :memberId")
    List<Crew> findAllByMemberId(@Param("memberId") Long memberId);
}

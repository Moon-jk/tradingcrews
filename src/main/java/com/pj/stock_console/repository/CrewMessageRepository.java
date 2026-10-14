package com.pj.stock_console.repository;

import com.pj.stock_console.entity.CrewMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrewMessageRepository extends JpaRepository<CrewMessage, Long> {

    @EntityGraph(attributePaths = { "sender", "pollOptions", "votes.voter" })
    List<CrewMessage> findAllByCrewIdOrderByCreatedAtAsc(Long crewId);
}

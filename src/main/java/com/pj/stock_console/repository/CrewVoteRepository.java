package com.pj.stock_console.repository;

import com.pj.stock_console.entity.CrewMessage;
import com.pj.stock_console.entity.CrewVote;
import com.pj.stock_console.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CrewVoteRepository extends JpaRepository<CrewVote, Long> {
    List<CrewVote> findAllByPollMessageId(Long pollMessageId);
    Optional<CrewVote> findByPollMessageAndVoter(CrewMessage pollMessage, Member voter);
}

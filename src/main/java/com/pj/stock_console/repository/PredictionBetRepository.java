package com.pj.stock_console.repository;

import com.pj.stock_console.entity.PredictionBet;
import com.pj.stock_console.entity.PredictionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import java.util.List;

@Repository
public interface PredictionBetRepository extends JpaRepository<PredictionBet, Long> {
    List<PredictionBet> findAllByOption(PredictionOption option);
    boolean existsByUserAndOptionQuestion(com.pj.stock_console.entity.Member user, com.pj.stock_console.entity.PredictionQuestion question);
    Optional<PredictionBet> findByUserAndOptionQuestion(com.pj.stock_console.entity.Member user, com.pj.stock_console.entity.PredictionQuestion question);
}

package com.pj.stock_console.repository;

import com.pj.stock_console.entity.CrewStrategyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface CrewStrategyLogRepository extends JpaRepository<CrewStrategyLog, Long> {
    
    @Query("SELECT l FROM CrewStrategyLog l JOIN FETCH l.author WHERE l.crew.id = :crewId ORDER BY l.createdAt DESC")
    List<CrewStrategyLog> findAllByCrewIdOrderByCreatedAtDesc(@Param("crewId") Long crewId);
}

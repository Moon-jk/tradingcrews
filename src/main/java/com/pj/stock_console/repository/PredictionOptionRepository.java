package com.pj.stock_console.repository;

import com.pj.stock_console.entity.PredictionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PredictionOptionRepository extends JpaRepository<PredictionOption, Long> {
}

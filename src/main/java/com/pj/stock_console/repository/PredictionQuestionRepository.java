package com.pj.stock_console.repository;

import com.pj.stock_console.entity.PredictionQuestion;
import com.pj.stock_console.entity.PredictionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PredictionQuestionRepository extends JpaRepository<PredictionQuestion, Long> {

    @Query("SELECT q FROM PredictionQuestion q WHERE q.status = :status ORDER BY q.createdAt DESC")
    List<PredictionQuestion> findAllByStatusWithItems(@Param("status") PredictionStatus status);

    @Query("SELECT q FROM PredictionQuestion q WHERE q.status IN :statuses ORDER BY q.createdAt DESC")
    List<PredictionQuestion> findAllByStatusInWithItems(@Param("statuses") List<PredictionStatus> statuses);

    @Query("SELECT q FROM PredictionQuestion q")
    List<PredictionQuestion> findAllWithItems();

    @Query("SELECT q FROM PredictionQuestion q WHERE q.status = :status ORDER BY q.settledAt DESC")
    List<PredictionQuestion> findTop5RecentSettledWithItems(@Param("status") PredictionStatus status);

    @Query("SELECT q FROM PredictionQuestion q ORDER BY q.createdAt DESC LIMIT 3")
    List<PredictionQuestion> findTop3RecentWithItems();
}

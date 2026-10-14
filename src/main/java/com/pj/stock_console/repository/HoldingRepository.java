package com.pj.stock_console.repository;

import com.pj.stock_console.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    List<Holding>    findAllByAccountId(Long accountId);

    List<Holding>    findAllByAccountIdIn(java.util.Collection<Long> accountIds);

    Optional<Holding> findByAccountIdAndSymbol(Long accountId, String symbol);
}

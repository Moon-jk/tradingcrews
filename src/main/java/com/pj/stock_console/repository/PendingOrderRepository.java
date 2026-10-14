package com.pj.stock_console.repository;

import com.pj.stock_console.entity.PendingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PendingOrderRepository extends JpaRepository<PendingOrder, Long> {
    List<PendingOrder> findAllByAccountIdAndStatusOrderByCreatedAtDesc(
            Long accountId, PendingOrder.OrderStatus status);
    List<PendingOrder> findAllByStatus(PendingOrder.OrderStatus status);
}

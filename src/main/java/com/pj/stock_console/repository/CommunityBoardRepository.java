package com.pj.stock_console.repository;

import com.pj.stock_console.entity.CommunityBoard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityBoardRepository extends JpaRepository<CommunityBoard, Long> {
}

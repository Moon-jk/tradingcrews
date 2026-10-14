package com.pj.stock_console.repository;

import com.pj.stock_console.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    // 페이징 처리를 위해 Page 반환 타입 사용
    Page<Notice> findAllByOrderByPinnedDescCreatedAtDesc(Pageable pageable);
    
    // 검색 기능: 제목 또는 내용에 키워드가 포함된 공지사항 검색
    Page<Notice> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrderByPinnedDescCreatedAtDesc(String title, String content, Pageable pageable);
}
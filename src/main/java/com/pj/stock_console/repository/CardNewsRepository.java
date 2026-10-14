package com.pj.stock_console.repository;

import com.pj.stock_console.entity.CardNews;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CardNewsRepository extends JpaRepository<CardNews, Long> {

    /** 최신순 전체 목록 + pages JOIN FETCH (LazyInitializationException 방지) */
    @Query("SELECT cn FROM CardNews cn ORDER BY cn.createdDate DESC")
    List<CardNews> findAllByOrderByCreatedDateDesc();

    /** 페이징 처리된 최신순 목록 */
    Page<CardNews> findAllByOrderByCreatedDateDesc(Pageable pageable);

    /** 추천(좋아요) 수 기준 내림차순 조회 (상위 N개용) */
    @Query("SELECT cn FROM CardNews cn LEFT JOIN cn.likedMembers m GROUP BY cn ORDER BY COUNT(m) DESC, cn.createdDate DESC")
    List<CardNews> findPopularNews(Pageable pageable);

    /** ID로 단건 조회 + pages JOIN FETCH */
    @Query("SELECT cn FROM CardNews cn LEFT JOIN FETCH cn.pages WHERE cn.id = :id")
    Optional<CardNews> findByIdWithPages(@Param("id") Long id);

    /** 관리자 전체 목록 + pages JOIN FETCH (LazyInit 완전 방지) */
    @Query("SELECT DISTINCT cn FROM CardNews cn LEFT JOIN FETCH cn.pages ORDER BY cn.createdDate DESC")
    List<CardNews> findAllWithPagesFetched();

    /** 회원이 저장한 정보센터 리포트 목록 조회 */
    @Query("SELECT DISTINCT cn FROM CardNews cn JOIN cn.savedMembers m WHERE m.id = :memberId ORDER BY cn.createdDate DESC")
    List<CardNews> findSavedByMemberId(@Param("memberId") Long memberId);

    /** 폴더ID로 조회 (중복 체크용) */
    Optional<CardNews> findByFolderId(String folderId);

    /** 폴더ID 존재 여부 */
    boolean existsByFolderId(String folderId);
}

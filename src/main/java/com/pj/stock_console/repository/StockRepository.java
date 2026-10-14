package com.pj.stock_console.repository;

import com.pj.stock_console.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface StockRepository extends JpaRepository<Stock, String> {
    // 대소문자 구분 없이 한글명, 영문명, 티커에서 키워드 포함 검색
    @Query("SELECT s FROM Stock s WHERE UPPER(s.nameKo) LIKE UPPER(CONCAT('%',:kw,'%')) " +
           "OR UPPER(s.nameEn) LIKE UPPER(CONCAT('%',:kw,'%')) " +
           "OR UPPER(s.symbol) LIKE UPPER(CONCAT('%',:kw,'%'))")
    List<Stock> searchByKeyword(@Param("kw") String kw);

    @Query(value = "SELECT * FROM stocks WHERE market = 'US' ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Stock findRandomStock();
}
package com.pj.stock_console.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stocks")
@Getter @Setter
@NoArgsConstructor
public class Stock {
    @Id
    private String symbol;      // 티커 (예: AAPL)
    private String nameKo;      // 한글명 (검색용)
    private String nameEn;      // 영문명 (예: Apple Inc)
    private String market;      // 시장 (예: US)

    public Stock(String symbol, String nameKo, String nameEn, String market) {
        this.symbol = symbol;
        this.nameKo = nameKo;
        this.nameEn = nameEn;
        this.market = market;
    }
}
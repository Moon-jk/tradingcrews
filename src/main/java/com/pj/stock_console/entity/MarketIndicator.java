package com.pj.stock_console.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonGetter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "market_indicator", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "symbol", "target_date" })
})
public class MarketIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @JsonIgnore
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "close_price", nullable = false)
    private Double closePrice;

    @Column(name = "open_price")
    private Double openPrice;

    @Column(name = "high_price")
    private Double highPrice;

    @Column(name = "low_price")
    private Double lowPrice;

    @JsonGetter("targetDate")
    public String getTargetDateString() {
        return this.targetDate != null ? this.targetDate.toString() : null;
    }
}

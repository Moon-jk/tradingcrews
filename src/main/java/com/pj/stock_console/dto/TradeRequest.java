package com.pj.stock_console.dto;

import com.pj.stock_console.entity.TradingAccount.MarketType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
public class TradeRequest {
    private String     symbol;
    private String     companyName;
    private int        quantity;
    private BigDecimal price;
    private String     orderType;   // MARKET | LIMIT
    private String     tradeType;   // BUY | SELL
    private MarketType marketType;  // US | KR
}

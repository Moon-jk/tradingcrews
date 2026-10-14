package com.pj.stock_console.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // JSON에 있는 다른 필드들은 무시함
public class NewsResponse {
    private String headline;
    private String summary;
    private String url;
    private Long datetime;
    private String image;
    private String source;
    private String related; // 연관 티커 (콤마 구분 문자열)
}
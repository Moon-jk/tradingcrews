package com.pj.stock_console.controller;

import com.pj.stock_console.service.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NewsController {
    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping("/api/fetch-news")
    public String fetchNews() {
        // NewsService에서 인자 없는 fetchAndSaveNews()를 지원하므로 에러 없음!
        newsService.fetchAndSaveNews();
        return "뉴스 수집 요청을 처리했습니다. DB를 확인하세요!";
    }
}
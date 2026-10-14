package com.pj.stock_console.controller;

import com.pj.stock_console.entity.CardNews;
import com.pj.stock_console.entity.Notice;
import com.pj.stock_console.repository.CardNewsRepository;
import com.pj.stock_console.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class SitemapController {

    private final NoticeRepository noticeRepository;
    private final CardNewsRepository cardNewsRepository;
    
    private static final String DOMAIN = "https://www.tradingcrews.com";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String getSitemap() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // 1. 정적 페이지들
        addUrl(sb, "/", "1.0", "daily");
        addUrl(sb, "/notice", "0.8", "daily");
        addUrl(sb, "/infocenter", "0.8", "daily");
        addUrl(sb, "/infocenter/macro", "0.7", "weekly");
        addUrl(sb, "/prediction", "0.7", "daily");
        addUrl(sb, "/community", "0.7", "daily");

        // 2. 공지사항 상세 페이지 (Notice)
        List<Notice> notices = noticeRepository.findAll();
        for (Notice notice : notices) {
            String lastMod = notice.getUpdatedAt() != null ? notice.getUpdatedAt().format(DATE_FORMATTER) 
                           : notice.getCreatedAt().format(DATE_FORMATTER);
            addDynamicUrl(sb, "/notice/detail/" + notice.getId(), lastMod, "0.5", "monthly");
        }

        // 3. 카드뉴스 상세 페이지 (CardNews)
        List<CardNews> cardNewsList = cardNewsRepository.findAll();
        for (CardNews cardNews : cardNewsList) {
            String lastMod = cardNews.getCreatedDate().format(DATE_FORMATTER);
            addDynamicUrl(sb, "/infocenter/" + cardNews.getId(), lastMod, "0.6", "monthly");
        }

        sb.append("</urlset>");
        return sb.toString();
    }

    private void addUrl(StringBuilder sb, String path, String priority, String changefreq) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(DOMAIN).append(path).append("</loc>\n");
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }

    private void addDynamicUrl(StringBuilder sb, String path, String lastmod, String priority, String changefreq) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(DOMAIN).append(path).append("</loc>\n");
        sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }
}

package com.pj.stock_console;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@EnableScheduling
public class AppConfig implements WebMvcConfigurer {

    /**
     * 파이썬 scripts/static 폴더를 /card-assets/** URL로 노출
     * (카드뉴스 이미지 & JSON을 Spring에서 직접 서빙)
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 프로젝트 루트 기준 scripts/static 절대 경로
        String scriptsStaticPath = new File("scripts/static").getAbsolutePath();
        registry.addResourceHandler("/card-assets/**")
                .addResourceLocations("file:" + scriptsStaticPath + "/");
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        return mapper;
    }

    /**
     * Caffeine 기반 캐시 매니저
     * - ranking : 1분 TTL (모의투자 랭킹 — 외부 API 다수 호출)
     * - stockPrice : 5분 TTL (개별 주가)
     * - exchangeRate: 5분 TTL (환율)
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // 랭킹: 60초 후 자동 만료
        manager.registerCustomCache("ranking",
                Caffeine.newBuilder()
                        .expireAfterWrite(60, TimeUnit.SECONDS)
                        .maximumSize(10)
                        .build());

        // 주가 / 환율: 5분 후 자동 만료
        Caffeine<Object, Object> fiveMin = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(200);
        manager.registerCustomCache("stockPrice", fiveMin.build());
        manager.registerCustomCache("exchangeRate", fiveMin.build());

        return manager;
    }
}

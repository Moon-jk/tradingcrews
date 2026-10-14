package com.pj.stock_console;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@EnableAsync // 비동기 처리 활성화
@EnableScheduling // 스케줄러 활성화 필수!
@SpringBootApplication
public class StockConsoleApplication {

    static {
        // 🌟 [추가됨] 앱 시작 전 secu.env 파일을 읽어 시스템 환경 변수로 등록합니다.
        loadDotEnv();
    }

    private static void loadDotEnv() {
        java.io.File envFile = new java.io.File("secu.env");
        if (envFile.exists()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(envFile), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int sep = line.indexOf('=');
                    if (sep > 0) {
                        String key = line.substring(0, sep).trim();
                        String value = line.substring(sep + 1).trim();
                        System.setProperty(key, value);
                    }
                }
                System.out.println("✅ Environment variables loaded from secu.env");
            } catch (Exception e) {
                System.err.println("❌ Failed to load secu.env: " + e.getMessage());
            }
        }
    }

    @PostConstruct
    public void started() {
        // 서버의 기본 타임존을 한국 시간(KST)으로 고정 설정
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    public static void main(String[] args) {
        SpringApplication.run(StockConsoleApplication.class, args);
    }
}
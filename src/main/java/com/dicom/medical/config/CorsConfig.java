package com.dicom.medical.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "https://www.hjp7208.site",   // 실제 프론트 (www, 슬래시 X)
                        "http://localhost:3000",
                        "http://192.168.7.120:3000/auth/google/login",
                        "https://master.d2ahqjf7y3gh6b.amplifyapp.com"  // ← 추가

// 1. AWS Elastic Beanstalk
// 2. 가비아 연동 주소 추가 예정
//                        "https://main.d1t17ie3uipfsv.amplifyapp.com"
//                        "https://www.ksw1360.asia",    // ← 추가!! (이게 진짜 핵심)
//                        "https://ksw1360.asia"          // ← www 없는 버전도 추가
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}

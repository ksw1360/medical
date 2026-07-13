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
                        "https://hjp7208.site",       // 실제 프론트 (루트)
                        "http://localhost:3000",
                        "https://master.d2ahqjf7y3gh6b.amplifyapp.com",
                        "https://www.ksw1360.asia",   // 가비아 도메인 (www)
                        "https://ksw1360.asia"        // 가비아 도메인 (루트)
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}

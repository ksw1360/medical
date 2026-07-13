package com.dicom.medical.config;

import com.dicom.medical.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // JWT는 세션을 안 쓰므로 CSRF 비활성화 + 무상태(STATELESS)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Swagger / API 문서 (개발용 공개)
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()

                        // 항상 공개
                        .requestMatchers("/auth/**").permitAll()                 // 로그인/회원가입
                        .requestMatchers("/actuator/health").permitAll()          // ELB 헬스체크
                        .requestMatchers("/favicon.ico", "/error").permitAll()

                        // TODO: 개발용 임시 개방 — 운영 전환 시 authenticated()로 전환
                        .requestMatchers(
                                "/image/**",
                                "/dicomweb/**",
                                "/api/ai/**",
                                "/api/studies/**",
                                "/api/dicom/**",
                                "/api/reports/**",
                                "/api/admin/**",
                                "/api/patients/**"
                        ).permitAll()

                        // 그 외 전부 JWT 인증 필요
                        .anyRequest().authenticated()
                )

                // 폼 로그인 / Basic 인증 끔 (JWT만 사용)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // JWT 필터를 표준 인증 필터 앞에 삽입
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 비밀번호 해싱 (회원가입/로그인)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // CORS: 프론트(다른 도메인/포트) 호출 허용
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "https://ksw1360.asia",
                "https://www.ksw1360.asia",
                "http://localhost:3000",
                "https://master.d2ahqjf7y3gh6b.amplifyapp.com"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
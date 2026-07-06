package com.dicom.medical.config;

import com.dicom.medical.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
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

    // swagger 접속용 계정 (application.yml 또는 EB 환경변수로 관리)
    @Value("${swagger.auth.username:dev}")
    private String swaggerUsername;

    @Value("${swagger.auth.password}")
    private String swaggerPassword;

    /**
     * [체인 1] swagger 전용 — Basic Auth
     * 프론트 개발자는 브라우저에서 dev/비밀번호 한 번 입력하면 계속 사용 가능.
     * 봇은 401에 막힘.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain swaggerChain(HttpSecurity http, PasswordEncoder encoder) throws Exception {
        // 이 체인 안에서만 쓰는 계정 (JWT 로그인용 UserDetailsService와 충돌 없음)
        InMemoryUserDetailsManager swaggerUser = new InMemoryUserDetailsManager(
                User.withUsername(swaggerUsername)
                        .password(encoder.encode(swaggerPassword))
                        .roles("SWAGGER")
                        .build()
        );

        http
                .securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                .userDetailsService(swaggerUser)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * [체인 2] 나머지 전부 — JWT
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
                // JWT는 세션을 안 쓰므로 CSRF 비활성화 + 무상태(STATELESS)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
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
                                "/api/patients/**"      // ← 추가
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

    // 로그인 로직에서 사용할 AuthenticationManager
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // CORS: 프론트(다른 도메인/포트) 호출 허용
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "https://ksw1360.asia",
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
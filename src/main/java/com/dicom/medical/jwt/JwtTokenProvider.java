package com.dicom.medical.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import com.dicom.medical.entity.UserRole;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {
    private final String secret;
    private final long accessExpiration;
    private final long refreshExpiration;
    private SecretKey key;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration}") long accessExpiration,
            @Value("${jwt.refresh-expiration}") long refreshExpiration) {
        this.secret = secret;
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    @PostConstruct
    protected void init() {
        // 문자열 시크릿을 HMAC-SHA 키로 변환 (32바이트 이상이어야 함)
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ── Access Token 생성: userId(sub) + email + role ──
    public String createAccessToken(Long userId, String email, UserRole role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpiration))
                .signWith(key)
                .compact();
    }

    // ── Refresh Token 생성: userId(sub)만 (재발급 전용, 최소 정보) ──
    public String createRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpiration))
                .signWith(key)
                .compact();
    }

    // ── 토큰에서 userId 추출 ──
    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    // ── 토큰에서 role 추출 (access 토큰에만 들어있음) ──
    public UserRole getRole(String token) {
        String role = parseClaims(token).get("role", String.class);
        return UserRole.valueOf(role);
    }

    // ── 토큰에서 email 추출 ──
    public String getEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    // ── 토큰 유효성 검증 (서명·만료 등) ──
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 위조, 만료, 형식 오류 등 모두 여기서 false
            return false;
        }
    }

    // ── 내부 공통: 토큰 파싱 + 서명 검증 ──
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 토큰의 만료 시각 추출 (DB 저장용)
    public java.time.LocalDateTime getExpiration(String token) {
        java.util.Date exp = parseClaims(token).getExpiration();
        return exp.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
    }

    // refresh 만료 시간 외부에서 알 수 있게 노출
    public long getRefreshExpirationMillis() {
        return refreshExpiration;
    }
}

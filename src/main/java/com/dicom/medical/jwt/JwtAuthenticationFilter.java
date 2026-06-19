package com.dicom.medical.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import com.dicom.medical.entity.UserRole;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        // 토큰이 있고 유효하면 → SecurityContext 에 인증 정보 등록
        if (token != null && jwtTokenProvider.validateToken(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            UserRole role = jwtTokenProvider.getRole(token);

            // 권한은 "ROLE_" 접두사 + 역할명 (Spring Security 규칙)
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));

            // principal 에 userId 를 담아둠 → 컨트롤러에서 꺼내 쓸 수 있음
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, authorities);
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        // 토큰이 없거나 무효여도 여기서 막지 않음 → 통과시키고,
        // 최종 접근 허용/거부는 SecurityConfig 가 판단 (인증 안 된 채로 보호 경로 가면 거기서 401)

        filterChain.doFilter(request, response);
    }

    // Authorization 헤더에서 "Bearer {토큰}" 형태로 토큰만 추출
    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7); // "Bearer " 7글자 잘라냄
        }
        return null;
    }
}
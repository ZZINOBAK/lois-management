package com.lois.management.auth;

import com.lois.management.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    public String createToken(String username, String role) {
        log.info("크리에이트토큰");

        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpiration());
        log.info("이스파이어리");

        return Jwts.builder()
                .subject(username)
                .claim("role", role) // 🔥 커스텀 클레임 추가
                .issuedAt(now)
                .expiration(expiry)
                .signWith(Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes()))
                .compact();
    }

    public String getRole(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            System.out.println("✅ [validate] 들어온 token = " + token);

            Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes()))
                    .build()
                    .parseSignedClaims(token); // 파싱 성공 = 검증 성공
            System.out.println("✅ [validate] 토큰 파싱/서명 검증 성공");

            return true;
        } catch (Exception e) {
            System.out.println("❌ [validate] 토큰 검증 실패: " + e.getClass().getSimpleName()
                    + " - " + e.getMessage());
            return false;
        }
    }

    public String getUsername(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public Authentication getAuthentication(String token) {
        String username = getUsername(token);
        String role = getRole(token);

        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + role));

        UserDetails principal = new User(username, "", authorities);

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    public boolean isValid(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));

            Jwts.parser()
                    .verifyWith(key)      // ✅ 0.12.x 방식
                    .build()
                    .parseSignedClaims(token); // ✅ 서명 + exp 검증까지 수행

            return true;
        } catch (ExpiredJwtException e) {
            return false; // 만료
        } catch (JwtException | IllegalArgumentException e) {
            return false; // 위조/형식 오류 등
        }
    }
}

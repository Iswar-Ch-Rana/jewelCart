package com.jewelcart.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;
    // secretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"

    @Value("${application.security.jwt.expiration}")
    private long expiration;
    // expiration = 86400000 (24 hours in milliseconds)

    // ─── GENERATE TOKEN ───────────────────────────────────────────────────────

    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("role", userDetails.getAuthorities()
                        .stream()
                        .findFirst()
                        .map(GrantedAuthority::getAuthority)
                        .orElse("ROLE_CUSTOMER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    // ─── EXTRACT EMAIL ────────────────────────────────────────────────────────

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    // ─── VALIDATE TOKEN ───────────────────────────────────────────────────────

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String emailFromToken = extractEmail(token);
        return emailFromToken.equals(userDetails.getUsername())
                && !isTokenExpired(token);
    }

    // ─── PRIVATE HELPERS ──────────────────────────────────────────────────────

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token)
                .getExpiration()
                .before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

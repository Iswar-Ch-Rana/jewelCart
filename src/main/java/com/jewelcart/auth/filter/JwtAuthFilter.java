package com.jewelcart.auth.filter;

import com.jewelcart.auth.repository.UserRepository;
import com.jewelcart.auth.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Step 1 — read Authorization header
        String header = request.getHeader("Authorization");

        // Step 2 — check Bearer prefix
        // no token = public endpoint or missing auth → continue without setting user
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;  // stop here — don't process further
        }

        // Step 3 — extract token (remove "Bearer " prefix — 7 characters)
        String token = header.substring(7);

        // Step 4 — extract email from token
        // In JwtAuthFilter — wrap token parsing in try-catch
        // replace the email extraction with try-catch
        String email;
        try {
            email = jwtService.extractEmail(token);
        } catch (ExpiredJwtException e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Token expired\"}");
            return;
        } catch (JwtException e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid token\"}");
            return;
        }

        if (email == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 5 — check if user already authenticated in this request
        // avoid re-authenticating if already done
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 6 — load user from database
        UserDetails user = userRepository.findByEmail(email)
                .orElse(null);

        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 7 — validate token (signature + expiry + email match)
        if (jwtService.isTokenValid(token, user)) {

            // Step 8 — tell Spring Security this user is authenticated
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            user,
                            null,                   // no credentials needed after token validation
                            user.getAuthorities()   // ROLE_ADMIN, ROLE_VENDOR etc.
                    );

            // attach request details (IP address, session) to the auth token
            authToken.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            // set in SecurityContext — Spring Security now knows who this user is
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        // Step 9 — always continue the filter chain
        filterChain.doFilter(request, response);
    }

}

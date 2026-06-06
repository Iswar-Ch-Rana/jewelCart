package com.jewelcart.auth.config;

import com.jewelcart.auth.filter.JwtAuthFilter;
import com.jewelcart.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // enables @PreAuthorize("hasRole('ADMIN')") on methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserRepository userRepository;

    // ─── 1. SECURITY FILTER CHAIN ────────────────────────────────────────────
    // defines which endpoints are public, which need JWT
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // disable CSRF — not needed for REST APIs using JWT
                // CSRF protects browser-based sessions — JWT is stateless
                .csrf(AbstractHttpConfigurer::disable)

                // define access rules per endpoint
                .authorizeHttpRequests(auth -> auth

                        // public endpoints — no token needed
                        .requestMatchers("/v1/auth/**").permitAll()         // register, login
                        .requestMatchers(HttpMethod.GET, "/v1/products/**").permitAll() // browse products
                        .requestMatchers(HttpMethod.GET, "/v1/categories/**").permitAll() // browse categories
                        .requestMatchers("/actuator/health").permitAll()    // health check

                        // swagger — allow in dev
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // everything else needs authentication
                        .anyRequest().authenticated()
                )

                // handle unauthorized access — return 401 with JSON error message
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"message\":\"Authentication required\"}");
                        })
                )

                // use stateless session — no server-side sessions
                // JWT carries all state — server doesn't remember anything
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // use our custom authentication provider
                .authenticationProvider(authenticationProvider())

                // add JWT filter BEFORE Spring's default username/password filter
                // our filter runs first, validates JWT, sets SecurityContext
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ─── 3. AUTHENTICATION PROVIDER ──────────────────────────────────────────
    // combines UserDetailsService + PasswordEncoder
    // Spring uses this to verify username + password during login
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(
                username -> userRepository.findByEmail(username)
                        .orElseThrow(() -> new UsernameNotFoundException(
                                "User not found: " + username))
        );
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // ─── 4. PASSWORD ENCODER ─────────────────────────────────────────────────
    // BCrypt — industry standard password hashing
    // never store plain text passwords
    // BCrypt automatically salts — same password = different hash every time
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ─── 5. AUTHENTICATION MANAGER ───────────────────────────────────────────
    // used by AuthService to authenticate login requests
    // Spring manages this — we just expose it as a bean
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

}

package com.jewelcart.auth.service;

import com.jewelcart.auth.dto.AuthResponse;
import com.jewelcart.auth.dto.LoginRequest;
import com.jewelcart.auth.dto.RegisterRequest;
import com.jewelcart.auth.entity.User;
import com.jewelcart.auth.repository.UserRepository;
import com.jewelcart.common.exception.DuplicateResourceException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // check duplicate email
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateResourceException(
                    "Email already registered: " + request.email());
        }

        // encode password — never store plain text
        User user = User.builder()
                .email(request.email())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .passwordHash(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .role(request.role())
                .build();

        user = userRepository.save(user);

        // generate JWT immediately — user logged in after registration
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, "Bearer", user.getEmail(), user.getRole());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // AuthenticationManager handles:
        // 1. load user by email
        // 2. verify password with BCrypt
        // 3. check an account is active
        // throws BadCredentialsException if wrong credentials
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        // authentication passed — load user and generate token
        User user = userRepository.findByEmail(request.email())
                .orElseThrow();

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, "Bearer", user.getEmail(), user.getRole());
    }
}

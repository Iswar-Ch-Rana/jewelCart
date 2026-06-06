package com.jewelcart.auth.controller;

import com.jewelcart.auth.dto.AuthResponse;
import com.jewelcart.auth.dto.LoginRequest;
import com.jewelcart.auth.dto.RegisterRequest;
import com.jewelcart.auth.service.AuthService;
import com.jewelcart.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.jewelcart.common.dto.ApiResponse.success;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/v1/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @RequestBody @Valid RegisterRequest request
    ) {
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity.ok(success("User registered successfully", authResponse));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody @Valid LoginRequest request
    ) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(success("User logged in successfully", authResponse));
    }

}

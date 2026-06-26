package com.jewelcart.auth.controller;

import com.jewelcart.auth.dto.AuthResponse;
import com.jewelcart.auth.dto.LoginRequest;
import com.jewelcart.auth.dto.RegisterRequest;
import com.jewelcart.auth.service.AuthService;
import com.jewelcart.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@Tag(name = "Auth", description = "User registration, login, and vendor account creation")
public class AuthController {
    private final AuthService authService;

    @Operation(summary = "Register a new customer", description = "Public endpoint. Creates a user with CUSTOMER role and returns a JWT token.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User registered successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already registered")
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @RequestBody @Valid RegisterRequest request
    ) {
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(success("User registered successfully", authResponse));
    }

    @Operation(summary = "Login", description = "Public endpoint. Authenticates user and returns a JWT token. Token must be sent as Bearer in Authorization header for protected endpoints.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful — JWT token returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid email or password")
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody @Valid LoginRequest request
    ) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(success("User logged in successfully", authResponse));
    }

    @Operation(summary = "Create a vendor account", description = "Admin-only. Creates a user with VENDOR role. Vendor can then log in using the same /login endpoint.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Vendor account created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already registered")
    })
    @PostMapping("/admin/create-vendor")
    @PreAuthorize("hasRole('ADMIN')")   // only ADMIN can create vendors
    public ResponseEntity<ApiResponse<AuthResponse>> createVendor(
            @RequestBody @Valid RegisterRequest request
    ) {
        AuthResponse response = authService.registerVendor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(success("Vendor created successfully", response));
    }

}

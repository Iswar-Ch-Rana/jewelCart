package com.jewelcart.auth.dto;

import com.jewelcart.common.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        String password,

        String firstName,   // optional — can fill in profile later
        String lastName,    // optional

        String phone,       // optional

        @NotNull(message = "Role is required")
        UserRole role
) {
}

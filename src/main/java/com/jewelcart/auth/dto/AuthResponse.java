package com.jewelcart.auth.dto;

import com.jewelcart.common.enums.UserRole;

public record AuthResponse(
        String token,
        String tokenType,    // always "Bearer"
        String email,
        UserRole role        // frontend needs this for routing
) {
}

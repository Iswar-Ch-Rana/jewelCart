package com.jewelcart.common.util;

import com.jewelcart.auth.entity.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    // get currently logged-in user — set by JwtAuthFilter
    public static User getCurrentUser() {
        return (User) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    // get current user's ID — useful for audit fields
    public static Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    // get current user's email
    public static String getCurrentUserEmail() {
        return getCurrentUser().getEmail();
    }
}
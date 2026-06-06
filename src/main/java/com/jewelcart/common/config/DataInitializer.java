package com.jewelcart.common.config;

import com.jewelcart.auth.entity.User;
import com.jewelcart.auth.repository.UserRepository;
import com.jewelcart.common.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        // create admin only if no admin exists
        if (userRepository.findByEmail("admin@jewelcart.com").isEmpty()) {
            User admin = User.builder()
                    .email("admin@jewelcart.com")
                    .passwordHash(passwordEncoder.encode("admin123456"))
                    .firstName("Admin")
                    .lastName("JewelCart")
                    .role(UserRole.ADMIN)
                    .isActive(true)
                    .build();
            userRepository.save(admin);
            System.out.println("Admin user created");
        }
    }
}

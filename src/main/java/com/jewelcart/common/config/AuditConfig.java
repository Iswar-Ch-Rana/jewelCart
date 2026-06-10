package com.jewelcart.common.config;

import com.jewelcart.common.util.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

@Configuration
public class AuditConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        // return logged-in username from SecurityContext
        return () -> {
            try {
                return Optional.of(SecurityUtils.getCurrentUserEmail());
            } catch (Exception e) {
                return Optional.empty();
            }
        };
    }

}

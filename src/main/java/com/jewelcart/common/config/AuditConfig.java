package com.jewelcart.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

@Configuration
public class AuditConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        // Phase 1: return system user (no auth yet)
        // Phase 2: return logged-in username from SecurityContext
        return () -> Optional
                .of("system");
    }
}

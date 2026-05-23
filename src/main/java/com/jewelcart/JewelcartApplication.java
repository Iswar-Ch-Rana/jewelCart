package com.jewelcart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorProvider") // JPA Auditing Enabled with AuditorAware Bean Reference
public class JewelcartApplication {

	public static void main(String[] args) {
		SpringApplication.run(JewelcartApplication.class, args);
	}

}

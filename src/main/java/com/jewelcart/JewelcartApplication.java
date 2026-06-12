package com.jewelcart;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorProvider") // JPA Auditing Enabled with AuditorAware Bean Reference
public class JewelcartApplication {

	public static void main(String[] args) {
		// load .env before Spring context starts
		Dotenv dotenv = Dotenv.configure()
				.ignoreIfMissing()  // don't crash if .env missing (prod uses real env vars)
				.load();
		dotenv.entries().forEach(e ->
				System.setProperty(e.getKey(), e.getValue())
		);

		SpringApplication.run(JewelcartApplication.class, args);
	}

}

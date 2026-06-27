package com.jewelcart;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class JewelcartApplicationTests {

	@Test
	void contextLoads() {
		// verifies entire Spring context starts without errors
		// catches: missing beans, wrong configs, DB connection issues
		// if this passes → your app is correctly configured
	}
}
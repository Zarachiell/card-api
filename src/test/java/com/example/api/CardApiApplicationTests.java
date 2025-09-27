package com.example.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = CardApiApplication.class)
@ActiveProfiles("test")
class CardApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
